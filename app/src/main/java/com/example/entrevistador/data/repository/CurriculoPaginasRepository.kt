package com.example.entrevistador.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Converte o currículo em PDF em imagens, uma por página.
 *
 * O Android não tem visualizador de PDF pronto, e embutir uma biblioteca de
 * PDF só para ler o currículo do candidato pesa o app à toa. O caminho mais
 * simples e confiável é usar o [PdfRenderer] do próprio sistema para desenhar
 * cada página num bitmap e mostrar isso como imagem.
 *
 * As páginas ficam em `cacheDir`: são feitas de novo quando o candidato é
 * aberto, e o sistema limpa sozinho quando falta espaço. Currículos que vêm de
 * outro aparelho chegam como endereço do Firestore; neste caso primeiro
 * buscamos o arquivo para o cache e então renderizamos (o caminho local já fica
 * pronto aqui).
 */
class CurriculoPaginasRepository(
    private val context: Context,
    private val anexoRepository: AnexoRepository,
) {

    private val pasta: File
        get() = File(context.cacheDir, PASTA).apply { if (!exists()) mkdirs() }

    /**
     * Gera (ou reaproveita) as páginas do currículo e devolve a lista de
     * caminhos das imagens.
     *
     * PDF é convertido página a página. Imagem não precisa de conversão: ela
     * entra na lista como está, e a tela trata igual.
     *
     * O nome da pasta no cache inclui o caminho de origem, então o mesmo
     * currículo reaproveita as imagens enquanto ele não mudar.
     */
    suspend fun paginas(caminho: String): Resultado = withContext(Dispatchers.IO) {
        val arquivo = arquivoPronto(caminho)
            ?: return@withContext Resultado.Erro("Currículo não encontrado neste aparelho.")

        if (!arquivo.name.endsWith(PDF, ignoreCase = true)) {
            return@withContext Resultado.Paginas(listOf(arquivo.absolutePath))
        }

        val pastaDoArquivo = File(pasta, pastaDe(caminho))
        val existentes = paginasExistentes(pastaDoArquivo)
        if (existentes.isNotEmpty()) {
            return@withContext Resultado.Paginas(existentes)
        }

        renderizar(arquivo, pastaDoArquivo, caminho)
    }

    /**
     * Arquivo local legível, buscando no Firestore quando ainda não está em
     * cache. Arquivo de tamanho zero é sobra de um download interrompido e é
     * descartado antes de tentar de novo.
     */
    private suspend fun arquivoPronto(caminho: String): File? {
        val local = runCatching { anexoRepository.resolverLocal(caminho) }.getOrNull() ?: return null
        val arquivo = File(local)
        return arquivo.takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * Desenha as páginas do PDF em JPEGs dentro de [pastaDoArquivo].
     *
     * Se o [ParcelFileDescriptor] não conseguir abrir o arquivo, a cópia local
     * provavelmente está incompleta: apagamos e baixamos de novo uma única vez.
     */
    private suspend fun renderizar(arquivo: File, pastaDoArquivo: File, caminho: String): Resultado {
        return try {
            renderizarPaginas(arquivo, pastaDoArquivo)
        } catch (e: SecurityException) {
            // O arquivo está protegido pelo sistema (ex.: "Nenhum app pode acessar").
            Resultado.Erro("O Android bloqueou a abertura deste currículo.")
        } catch (e: FileNotFoundException) {
            if (!anexoRepository.ehCaminhoNuvem(caminho)) {
                return Resultado.Erro("O arquivo deste currículo não está mais no aparelho.")
            }
            val novo = runCatching { anexoRepository.baixarDaNuvem(caminho) }.getOrNull()
                ?.let(::File)
                ?.takeIf { it.isFile && it.length() > 0L }
                ?: return Resultado.Erro("Não consegui baixar este currículo do banco de dados.")
            runCatching { pastaDoArquivo.deleteRecursively() }
            try {
                renderizarPaginas(novo, pastaDoArquivo)
            } catch (e2: Exception) {
                Resultado.Erro("Não consegui abrir o currículo: ${e2.message ?: "PDF inválido"}")
            }
        } catch (e: Exception) {
            Resultado.Erro("Não consegui abrir o currículo: ${e.message ?: "PDF inválido"}")
        }
    }

    private fun renderizarPaginas(arquivo: File, pastaDoArquivo: File): Resultado {
        if (!pastaDoArquivo.exists()) pastaDoArquivo.mkdirs()
        val descritor = ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(descritor).use { renderizador ->
            val quantidade = renderizador.pageCount
            if (quantidade <= 0) {
                return Resultado.Erro("Não consegui ler as páginas deste PDF.")
            }

            val caminhos = mutableListOf<String>()
            for (indice in 0 until quantidade) {
                // Uma página por vez: manter todas abertas de uma vez estoura a
                // memória em currículo com muitas páginas.
                renderizador.openPage(indice).use { pagina ->
                    val largura = pagina.width.coerceAtLeast(1)
                    val altura = pagina.height.coerceAtLeast(1)
                    val escala = escalaDe(largura, altura)

                    val bitmap = Bitmap.createBitmap(
                        (largura * escala).toInt().coerceAtLeast(1),
                        (altura * escala).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    // Fundo branco: o PDF é transparente por padrão e apareceria
                    // com o fundo escuro do app por trás do texto.
                    bitmap.eraseColor(Color.WHITE)
                    pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val destino = File(pastaDoArquivo, "$indice.$EXTENSAO")
                    destino.outputStream().use { saida ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, saida)
                    }
                    bitmap.recycle()
                    caminhos += destino.absolutePath
                }
            }
            return Resultado.Paginas(caminhos)
        }
    }

    /**
     * Quanto ampliar a página ao desenhar.
     *
     * O [PdfRenderer] entrega a página no tamanho em que o PDF foi salvo, que
     * costuma ser 72 dpi — muito menor que a tela do celular. Desenhar nesse
     * tamanho e depois esticar para a largura da tela deixa o texto borrado,
     * então a página é desenhada já no tamanho em que vai ser vista.
     */
    private fun escalaDe(largura: Int, altura: Int): Float {
        val larguraDaTela = context.resources.displayMetrics.widthPixels.toFloat()
        val escala = larguraDaTela / largura

        // Acima disso a memória vai embora antes do ganho aparecer: uma página
        // A4 ampliada 4x já passa de 30 MB em bitmap.
        val teto = (PIXELS_MAXIMOS / (largura.toLong() * altura)).toFloat()
        return escala.coerceIn(1f, minOf(ESCALA_MAXIMA, teto))
    }

    private fun paginasExistentes(pastaDoArquivo: File): List<String> {
        if (!pastaDoArquivo.exists()) return emptyList()
        val arquivos = pastaDoArquivo.listFiles { arquivo ->
            arquivo.extension == EXTENSAO
        } ?: return emptyList()

        val paginas = arquivos
            .map { arquivo -> arquivo.name.substringBeforeLast('.') }
            .filter { nome ->
                // Página sem número indica renderização pela metade: descarta tudo.
                nome.toIntOrNull() != null
            }
            .sortedBy { it.toInt() }

        return paginas.map { File(pastaDoArquivo, "$it.$EXTENSAO").absolutePath }
    }

    /** Pasta do cache própria de um currículo, derivada do caminho dele. */
    private fun pastaDe(caminhoPdf: String): String {
        val chave = caminhoPdf.hashCode().toUInt().toString(16)
        val nome = File(caminhoPdf).nameWithoutExtension.replace(INVALIDOS, "_")
        return "${nome}_$chave"
    }

    sealed interface Resultado {
        data class Paginas(val caminhos: List<String>) : Resultado
        data class Erro(val mensagem: String) : Resultado
    }

    companion object {
        /**
         * O sufixo entra no nome da pasta porque a forma de renderizar mudou:
         * as imagens antigas foram desenhadas no tamanho do PDF e ficavam
         * borradas. Trocar o nome do cache faz o app refazer as páginas na
         * qualidade certa em vez de reaproveitar as antigas já baixadas.
         */
        private const val PASTA = "paginas_curriculo_v2"
        private const val EXTENSAO = "jpg"
        private const val QUALIDADE = 92
        private const val PDF = ".pdf"
        private val INVALIDOS = Regex("[^A-Za-z0-9._-]")

        /** Ampliação máxima ao desenhar a página (2,5x o tamanho do PDF). */
        private const val ESCALA_MAXIMA = 2.5f

        /** Teto de pixels por página, para não estourar a memória do aparelho. */
        private const val PIXELS_MAXIMOS = 8_000_000L
    }
}
