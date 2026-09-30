package com.example.entrevistador.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
 * outro aparelho chegam como endereço do Storage; neste caso primeiro baixamos
 * para o cache e então renderizamos (o caminho local já fica pronto aqui).
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
     * O nome do arquivo no cache inclui o caminho de origem, então o mesmo
     * currículo reaproveita as imagens enquanto ele não mudar.
     */
    suspend fun paginas(caminho: String): Resultado = withContext(Dispatchers.IO) {
        val arquivo = File(anexoRepository.resolverLocal(caminho))
        if (!arquivo.exists()) {
            return@withContext Resultado.Erro("Currículo não encontrado.")
        }

        if (!arquivo.name.endsWith(PDF, ignoreCase = true)) {
            return@withContext Resultado.Paginas(listOf(arquivo.absolutePath))
        }

        try {
            val pastaDoArquivo = File(pasta, pastaDe(caminho))
            val existentes = paginasExistentes(pastaDoArquivo)
            if (existentes.isNotEmpty()) {
                return@withContext Resultado.Paginas(existentes)
            }

            val novas = renderizar(arquivo, pastaDoArquivo)
            if (novas.isEmpty()) {
                Resultado.Erro("Não consegui ler as páginas deste PDF.")
            } else {
                Resultado.Paginas(novas)
            }
        } catch (e: SecurityException) {
            // O arquivo está protegido pelo sistema (ex.: "Nenhum app pode acessar").
            Resultado.Erro("O Android bloqueou a abertura deste currículo.")
        } catch (e: Exception) {
            Resultado.Erro("Não consegui abrir o currículo: ${e.message ?: "PDF inválido"}")
        }
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

    private fun renderizar(arquivo: File, pastaDoArquivo: File): List<String> {
        val descritor = ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(descritor).use { renderizador ->
            val quantidade = renderizador.pageCount
            if (quantidade <= 0) return emptyList()

            val caminhos = mutableListOf<String>()
            for (indice in 0 until quantidade) {
                // Uma página por vez: manter todas abertas de uma vez estoura a
                // memória em currículo com muitas páginas.
                renderizador.openPage(indice).use { pagina ->
                    val bitmap = Bitmap.createBitmap(
                        pagina.width.coerceAtLeast(1),
                        pagina.height.coerceAtLeast(1),
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
            return caminhos
        }
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
        private const val PASTA = "paginas_curriculo"
        private const val EXTENSAO = "jpg"
        private const val QUALIDADE = 90
        private const val PDF = ".pdf"
        private val INVALIDOS = Regex("[^A-Za-z0-9._-]")
    }
}
