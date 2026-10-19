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

class CurriculoPaginasRepository(
    private val context: Context,
    private val anexoRepository: AnexoRepository,
) {

    private val pasta: File
        get() = File(context.cacheDir, PASTA).apply { if (!exists()) mkdirs() }

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

    private suspend fun arquivoPronto(caminho: String): File? {
        val local = runCatching { anexoRepository.resolverLocal(caminho) }.getOrNull() ?: return null
        val arquivo = File(local)
        return arquivo.takeIf { it.isFile && it.length() > 0L }
    }

    private suspend fun renderizar(arquivo: File, pastaDoArquivo: File, caminho: String): Resultado {
        return try {
            renderizarPaginas(arquivo, pastaDoArquivo)
        } catch (e: SecurityException) {

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

                renderizador.openPage(indice).use { pagina ->
                    val largura = pagina.width.coerceAtLeast(1)
                    val altura = pagina.height.coerceAtLeast(1)
                    val escala = escalaDe(largura, altura)

                    val bitmap = Bitmap.createBitmap(
                        (largura * escala).toInt().coerceAtLeast(1),
                        (altura * escala).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )

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

    private fun escalaDe(largura: Int, altura: Int): Float {
        val larguraDaTela = context.resources.displayMetrics.widthPixels.toFloat()
        val escala = larguraDaTela / largura

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

                nome.toIntOrNull() != null
            }
            .sortedBy { it.toInt() }

        return paginas.map { File(pastaDoArquivo, "$it.$EXTENSAO").absolutePath }
    }

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

        private const val PASTA = "paginas_curriculo_v2"
        private const val EXTENSAO = "jpg"
        private const val QUALIDADE = 92
        private const val PDF = ".pdf"
        private val INVALIDOS = Regex("[^A-Za-z0-9._-]")

        private const val ESCALA_MAXIMA = 2.5f

        private const val PIXELS_MAXIMOS = 8_000_000L
    }
}
