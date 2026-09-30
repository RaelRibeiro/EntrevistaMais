package com.example.entrevistador.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.entrevistador.domain.model.TipoCurriculo
import java.io.File

/**
 * Guarda o currículo anexado dentro do armazenamento interno do app.
 *
 * Copiar é melhor do que guardar só a URI do seletor: permissão de URI
 * temporária pode ser revogada, e o arquivo sumiria da lista depois. Com uma
 * cópia em `filesDir/curriculos/` o PDF do candidato fica disponível sempre.
 */
class AnexoRepository(private val context: Context) {

    private val pasta: File
        get() = File(context.filesDir, PASTA).apply { if (!exists()) mkdirs() }

    /** Extensões que o app aceita como currículo. */
    fun ehAceito(uri: Uri): Boolean = tipoDeArquivo(uri) != null

    /** PDF ou imagem, conforme o MIME informado pelo seletor do sistema. */
    fun tipoDeArquivo(uri: Uri): TipoCurriculo? {
        val tipo = context.contentResolver.getType(uri)?.lowercase() ?: return null
        return when {
            tipo == "application/pdf" -> TipoCurriculo.PDF
            tipo in TIPOS_IMAGEM -> TipoCurriculo.IMAGEM
            else -> null
        }
    }

    /**
     * Copia o arquivo escolhido para dentro do app e devolve o caminho.
     * Retorna null se o arquivo não existir ou não puder ser lido.
     */
    suspend fun copiar(uri: Uri): Resultado = withContext(Dispatchers.IO) {
        try {
            val nomeOriginal = lerNome(uri) ?: "curriculo"
            val extensao = when {
                nomeOriginal.contains('.') -> nomeOriginal.substringAfterLast('.').take(5)
                else -> "pdf"
            }
            val destino = File(pasta, "cv_${System.currentTimeMillis()}.$extensao")

            context.contentResolver.openInputStream(uri)?.use { entrada ->
                destino.outputStream().use { saida -> entrada.copyTo(saida) }
            } ?: return@withContext Resultado.Erro("Não consegui abrir o arquivo.")

            Resultado.Sucesso(caminho = destino.absolutePath, nomeOriginal = nomeOriginal)
        } catch (e: Exception) {
            Resultado.Erro("Falha ao salvar o arquivo: ${e.message ?: "erro desconhecido"}")
        }
    }

    suspend fun apagar(caminho: String) = withContext(Dispatchers.IO) {
        runCatching { File(caminho).takeIf { it.exists() }?.delete() }
        Unit
    }

    suspend fun lerComoTexto(caminho: String): String? = withContext(Dispatchers.IO) {
        // Só arquivos de texto podem ser lidos; PDF é binário e fica no visualizador.
        val arquivo = File(caminho)
        if (!arquivo.exists() || arquivo.length() > TAMANHO_MAXIMO_LEITURA) return@withContext null
        runCatching { arquivo.readText() }.getOrNull()
    }

    private fun lerNome(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indice >= 0 && cursor.moveToFirst()) cursor.getString(indice) else null
            }
        }.getOrNull()
    }

    sealed interface Resultado {
        data class Sucesso(val caminho: String, val nomeOriginal: String) : Resultado
        data class Erro(val mensagem: String) : Resultado
    }

    companion object {
        private const val PASTA = "curriculos"
        private const val TAMANHO_MAXIMO_LEITURA = 512L * 1024

        val TIPOS_IMAGEM = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/heic",
        )

        /** Filtro enviado ao seletor de arquivos do Android. */
        val TIPOS_ACEITOS = TIPOS_IMAGEM + "application/pdf"
    }
}
