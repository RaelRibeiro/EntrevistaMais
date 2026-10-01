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

/**
 * Guarda o currículo anexado e o envia para o Firestore.
 *
 * O arquivo é copiado para o armazenamento interno do app (para renderizar
 * imediatamente) e é enviado também para `usuarios/{uid}/curriculos/`: é isso
 * que faz o currículo viajar com a entrevista para outro aparelho ou para o
 * site. Nas Entrevistas, o campo de caminho guarda esse endereço; na hora de
 * abrir, a [CurriculoPaginasRepository] traz o arquivo de volta para o cache
 * daquele aparelho se ele não estiver local.
 *
 * ## Por que o arquivo vai para o Firestore e não para o Storage
 *
 * O Storage do Firebase só é liberado com faturamento ativo no projeto, o que
 * exige cartão de crédito. Como este aplicativo é de uso pessoal e o volume é
 * de poucos currículos, o arquivo é guardado no próprio Firestore, que já está
 * no nível gratuito. O Firestore aceita no máximo 1 MiB por documento, então o
 * arquivo é convertido para base64 (que infla o tamanho em 33%) e dividido em
 * pedaços gravados como documentos irmãos em `partes/`. Na leitura os pedaços
 * voltam, são concatenados e decodificados de volta para o arquivo original.
 *
 * O custo disso: base64 ocupa um terço a mais de espaço, cada abertura do
 * currículo lê mais de um documento e a cota do Firestore se esgota mais rápido
 * do que se usasse um storage de verdade. Em troca, nenhuma dependência nova,
 * nenhuma configuração de servidor e as regras de segurança continuam isolando
 * cada usuário na própria pasta.
 */
class AnexoRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
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
     * Envia a cópia local para o Firestore e devolve o endereço gravado. O
     * caminho devolvido é o que deve ser gravado na entrevista.
     */
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

            // Um lote só: ou o currículo inteiro vai para o Firestore, ou nada
            // dele fica pela metade (um currículo pela metade não serve para nada).
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

    /** Busca um currículo no Firestore, monta o arquivo no cache e devolve o caminho local. */
    suspend fun baixarDaNuvem(caminhoNuvem: String): String = withContext(Dispatchers.IO) {
        val partes = caminhoNuvem.split('/')
        require(partes.size >= 4 && partes[0] == "usuarios") { "Endereço de currículo inválido." }
        val usuario = partes[1]
        val docId = partes[3].substringBeforeLast('.')

        val referencia = colecaoDeCurriculos(usuario).document(docId)
        val documento = referencia.get().await()
        require(documento.exists()) { "Currículo não encontrado." }

        val documentos = referencia.collection("partes").get().await().documents
        // Os pedaços são numerados com zeros à esquerda, o que faz a ordem
        // alfabética do Firestore coincidir com a ordem do arquivo.
        val texto = documentos.joinToString("") { it.getString("dados").orEmpty() }
        val bytes = Base64.decode(texto, Base64.NO_WRAP)

        val nome = documento.getString("nome")?.let { sanear(it) } ?: "$docId.bin"
        val destino = File(pastaCacheNuvem, nome)
        // Cópia pela metade deixaria um PDF truncado, que o renderizador não
        // conseguiria abrir; descartar antes de gravar é mais seguro.
        runCatching { if (destino.exists()) destino.delete() }
        destino.writeBytes(bytes)
        destino.absolutePath
    }

    /**
     * Apaga o currículo: os pedaços e o documento no Firestore (se o caminho for
     * deles) e a cópia local quando ela existir neste aparelho.
     */
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
        // Só arquivos de texto podem ser lidos; PDF é binário e fica no visualizador.
        val arquivo = File(caminho)
        if (!arquivo.exists() || arquivo.length() > TAMANHO_MAXIMO_LEITURA) return@withContext null
        runCatching { arquivo.readText() }.getOrNull()
    }

    /** Um currículo que vive no Firestore (endereço `usuarios/{uid}/curriculos/...`). */
    fun ehCaminhoNuvem(caminho: String): Boolean = caminho.startsWith("usuarios/")

    /** Resolve para um arquivo local: devolve o caminho como está ou busca no Firestore. */
    suspend fun resolverLocal(caminho: String): String {
        if (!ehCaminhoNuvem(caminho)) return caminho
        if (File(caminho).exists()) return caminho
        val local = File(pastaCacheNuvem, caminho.substringAfterLast('/'))
        // Arquivo de tamanho zero é sobra de um download que falhou no meio.
        if (local.exists() && local.length() > 0L) return local.absolutePath
        return baixarDaNuvem(caminho)
    }

    private fun colecaoDeCurriculos(usuario: String) =
        firestore.collection("usuarios").document(usuario).collection("curriculos")

    /**
     * Os pedaços são numerados com zeros à esquerda para que a ordem do
     * Firestore (que é alfabética) coincida com a ordem do arquivo. O site usa
     * a mesma regra, então um currículo gravado por um abre no outro.
     */
    private fun nomeDaParte(indice: Int): String = indice.toString().padStart(6, '0')

    private fun lerNome(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indice >= 0 && cursor.moveToFirst()) cursor.getString(indice) else null
            }
        }.getOrNull()
    }

    /** Só caracteres seguros em nomes de documento do Firestore. */
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

        /**
         * Firestore aceita 1 MiB por documento. 500 mil caracteres de base64
         * deixam folga larga para os outros campos, o que dá cerca de 4 MB de
         * currículo (5 MB de arquivo) sem estourar o limite.
         */
        private const val PARTE = 500_000
        private const val TAMANHO_MAXIMO = 5L * 1024 * 1024

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
