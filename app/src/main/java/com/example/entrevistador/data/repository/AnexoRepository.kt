package com.example.entrevistador.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.entrevistador.domain.model.TipoCurriculo
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Guarda o currículo anexado e o envia para o Firebase Storage.
 *
 * O arquivo é copiado para o armazenamento interno do app (para renderizar
 * imediatamente) e é enviado também para `usuarios/{uid}/curriculos/`: é isso
 * que faz o currículo viajar com a entrevista para outro aparelho ou para o
 * site. Nas Entrevistas, o campo de caminho guarda o endereço no Storage; na
 * hora de abrir, a [CurriculoPaginasRepository] baixa para o cache daquele
 * aparelho se o arquivo não estiver local.
 */
class AnexoRepository(
    private val context: Context,
    private val storage: FirebaseStorage,
    private val uid: () -> String,
) {

    private val pasta: File
        get() = File(context.filesDir, PASTA).apply { if (!exists()) mkdirs() }

    private val pastaCacheNuvem: File
        get() = File(context.cacheDir, PASTA_NUVEM).apply { if (!exists()) mkdirs() }

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

    /**
     * Envia a cópia local para o Storage e devolve o endereço (caminho no
     * bucket). O caminho devolvido é o que deve ser gravado na entrevista.
     */
    suspend fun enviarParaNuvem(caminhoLocal: String, nomeArquivo: String): String = withContext(Dispatchers.IO) {
        val destino = caminhoNoStorage(nomeArquivo)
        storage.reference.child(destino)
            .putFile(Uri.fromFile(File(caminhoLocal)))
            .await()
        destino
    }

    /** Baixa um currículo do Storage para o cache e devolve o caminho local. */
    suspend fun baixarDaNuvem(caminhoNuvem: String): String = withContext(Dispatchers.IO) {
        val destino = File(pastaCacheNuvem, sanear(caminhoNuvem.substringAfterLast('/')))
        storage.reference.child(caminhoNuvem).getFile(destino).await()
        destino.absolutePath
    }

    /**
     * Apaga o currículo: arquivo no Storage (se o caminho for do Storage) e a
     * cópia local quando ela existir neste aparelho.
     */
    suspend fun apagar(caminho: String) {
        withContext(Dispatchers.IO) {
            if (ehCaminhoNuvem(caminho)) {
                runCatching { storage.reference.child(caminho).delete().await() }
                val nome = sanear(caminho.substringAfterLast('/'))
                File(pasta, nome).takeIf { it.exists() }?.delete()
                File(pastaCacheNuvem, nome).takeIf { it.exists() }?.delete()
            } else {
                runCatching { File(caminho).takeIf { it.exists() }?.delete() }
            }
        }
    }

    suspend fun lerComoTexto(caminho: String): String? = withContext(Dispatchers.IO) {
        // Só arquivos de texto podem ser lidos; PDF é binário e fica no visualizador.
        val arquivo = File(caminho)
        if (!arquivo.exists() || arquivo.length() > TAMANHO_MAXIMO_LEITURA) return@withContext null
        runCatching { arquivo.readText() }.getOrNull()
    }

    /** Um currículo que vive no Storage (endereço `usuarios/{uid}/curriculos/...`). */
    fun ehCaminhoNuvem(caminho: String): Boolean = caminho.startsWith("usuarios/")

    /** Resolve para um arquivo local: devolve o caminho como está ou baixa do Storage. */
    suspend fun resolverLocal(caminho: String): String {
        if (!ehCaminhoNuvem(caminho) || File(caminho).exists()) return caminho
        val local = File(pastaCacheNuvem, sanear(caminho.substringAfterLast('/')))
        if (local.exists()) return local.absolutePath
        return baixarDaNuvem(caminho)
    }

    private fun caminhoNoStorage(nomeArquivo: String): String {
        val extensao = nomeArquivo.substringAfterLast('.', "pdf").take(5)
        val base = nomeArquivo.substringBeforeLast('.', "curriculo")
        return "usuarios/${uid()}/curriculos/${System.currentTimeMillis()}_${sanear(base)}.$extensao"
    }

    private fun lerNome(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indice >= 0 && cursor.moveToFirst()) cursor.getString(indice) else null
            }
        }.getOrNull()
    }

    /** Só caracteres seguros em caminhos de Storage. */
    private fun sanear(nome: String): String =
        nome.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)

    sealed interface Resultado {
        data class Sucesso(val caminho: String, val nomeOriginal: String) : Resultado
        data class Erro(val mensagem: String) : Resultado
    }

    companion object {
        private const val PASTA = "curriculos"
        private const val PASTA_NUVEM = "curriculos_nuvem"
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