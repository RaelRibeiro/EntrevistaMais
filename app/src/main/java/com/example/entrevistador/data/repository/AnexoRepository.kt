package com.example.entrevistador.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.entrevistador.domain.model.TipoCurriculo
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class AnexoRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val uid: () -> String,
) {

    private val pasta: File
        get() = File(context.filesDir, PASTA).apply { if (!exists()) mkdirs() }

    private val pastaCacheNuvem: File
        get() = File(context.cacheDir, PASTA_NUVEM).apply { if (!exists()) mkdirs() }

    fun ehAceito(uri: Uri): Boolean = tipoDeArquivo(uri) != null

    fun tipoDeArquivo(uri: Uri): TipoCurriculo? {
        val tipo = context.contentResolver.getType(uri)?.lowercase() ?: return null
        return when {
            tipo == "application/pdf" -> TipoCurriculo.PDF
            tipo in TIPOS_IMAGEM -> TipoCurriculo.IMAGEM
            else -> null
        }
    }

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

    suspend fun enviarParaNuvem(caminhoLocal: String, nomeArquivo: String): String =
        withContext(Dispatchers.IO) {
            val usuario = uid()
            require(usuario.isNotEmpty()) { "Sem usuário conectado." }

            val arquivo = File(caminhoLocal)
            val bytes = arquivo.readBytes()
            if (bytes.size > TAMANHO_MAXIMO) {
                throw IllegalStateException(
                    "Currículo maior que ${TAMANHO_MAXIMO / (1024 * 1024)} MB."
                )
            }

            val extensao = nomeArquivo.substringAfterLast('.', "pdf").lowercase().take(5)
            val base = nomeArquivo.substringBeforeLast('.', "curriculo")
            val docId = "${System.currentTimeMillis()}_${sanear(base)}"

            val texto = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val total = texto.length
            val quantidade = if (total == 0) 1 else (total + PARTE - 1) / PARTE

            val lote = firestore.batch()
            val documento = colecaoDeCurriculos(usuario).document(docId)
            lote.set(
                documento,
                mapOf(
                    "id" to System.currentTimeMillis(),
                    "nome" to nomeArquivo,
                    "ext" to extensao,
                    "partes" to quantidade,
                    "bytes" to bytes.size,
                ),
            )
            for (indice in 0 until quantidade) {
                val pedaco = texto.substring(
                    indice * PARTE,
                    minOf((indice + 1) * PARTE, total),
                )
                lote.set(
                    documento.collection("partes").document(nomeDaParte(indice)),
                    mapOf("dados" to pedaco),
                )
            }
            lote.commit().await()

            "usuarios/$usuario/curriculos/$docId.$extensao"
        }

    suspend fun baixarDaNuvem(caminhoNuvem: String): String = withContext(Dispatchers.IO) {
        val partes = caminhoNuvem.split('/')
        require(partes.size >= 4 && partes[0] == "usuarios") { "Endereço de currículo inválido." }
        val usuario = partes[1]
        val docId = partes[3].substringBeforeLast('.')

        val referencia = colecaoDeCurriculos(usuario).document(docId)
        val documento = referencia.get().await()
        require(documento.exists()) { "Currículo não encontrado." }

        val documentos = referencia.collection("partes").get().await().documents

        val texto = documentos.joinToString("") { it.getString("dados").orEmpty() }
        val bytes = Base64.decode(texto, Base64.NO_WRAP)

        val nome = documento.getString("nome")?.let { sanear(it) } ?: "$docId.bin"
        val destino = File(pastaCacheNuvem, nome)

        runCatching { if (destino.exists()) destino.delete() }
        destino.writeBytes(bytes)
        destino.absolutePath
    }

    suspend fun apagar(caminho: String) {
        withContext(Dispatchers.IO) {
            if (ehCaminhoNuvem(caminho)) {
                val partes = caminho.split('/')
                if (partes.size >= 4) {
                    runCatching {
                        val referencia = colecaoDeCurriculos(partes[1])
                            .document(partes[3].substringBeforeLast('.'))
                        val documentos = referencia.collection("partes").get().await().documents
                        val lote = firestore.batch()
                        for (item in documentos) {
                            lote.delete(item.reference)
                        }
                        lote.delete(referencia)
                        lote.commit().await()
                    }
                }
                val nome = caminho.substringAfterLast('/')
                File(pasta, nome).takeIf { it.exists() }?.delete()
                File(pastaCacheNuvem, nome).takeIf { it.exists() }?.delete()
            } else {
                runCatching { File(caminho).takeIf { it.exists() }?.delete() }
            }
        }
    }

    suspend fun lerComoTexto(caminho: String): String? = withContext(Dispatchers.IO) {

        val arquivo = File(caminho)
        if (!arquivo.exists() || arquivo.length() > TAMANHO_MAXIMO_LEITURA) return@withContext null
        runCatching { arquivo.readText() }.getOrNull()
    }

    fun ehCaminhoNuvem(caminho: String): Boolean = caminho.startsWith("usuarios/")

    suspend fun resolverLocal(caminho: String): String {
        if (!ehCaminhoNuvem(caminho)) return caminho
        if (File(caminho).exists()) return caminho
        val local = File(pastaCacheNuvem, caminho.substringAfterLast('/'))

        if (local.exists() && local.length() > 0L) return local.absolutePath
        return baixarDaNuvem(caminho)
    }

    private fun colecaoDeCurriculos(usuario: String) =
        firestore.collection("usuarios").document(usuario).collection("curriculos")

    private fun nomeDaParte(indice: Int): String = indice.toString().padStart(6, '0')

    private fun lerNome(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indice >= 0 && cursor.moveToFirst()) cursor.getString(indice) else null
            }
        }.getOrNull()
    }

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

        private const val PARTE = 500_000
        private const val TAMANHO_MAXIMO = 5L * 1024 * 1024

        val TIPOS_IMAGEM = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/heic",
        )

        val TIPOS_ACEITOS = TIPOS_IMAGEM + "application/pdf"
    }
}
