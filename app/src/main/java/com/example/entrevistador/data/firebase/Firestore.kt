package com.example.entrevistador.data.firebase

import com.example.entrevistador.data.local.entity.EntrevistaEntity
import com.example.entrevistador.data.local.entity.ExperienciaEntity
import com.example.entrevistador.data.local.entity.PerguntaEntity
import com.example.entrevistador.data.local.entity.RespostaEntity
import com.example.entrevistador.data.local.entity.RoteiroEntity
import com.example.entrevistador.data.local.entity.VagaEntity
import com.example.entrevistador.domain.model.RespostaAutomatica
import com.example.entrevistador.domain.model.StatusEntrevista
import com.example.entrevistador.domain.model.TipoCurriculo
import com.example.entrevistador.domain.model.TipoResposta
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * Raiz dos dados do usuário no Firestore.
 *
 * O app escreve na mesma árvore do site: tudo vive dentro de
 * `usuarios/{uid}/...`. O uid é resolvido na hora da chamada (nunca é
 * guardado), então cada método, Fluxo ou DAO sempre aponta para o perfil logado.
 */
internal class FirestoreFontes(
    private val firestore: FirebaseFirestore,
    private val uidProvider: ProvedorUid,
) {
    private fun usuarioId(): String = uidProvider.uid()
    private fun raiz() = firestore.collection("usuarios").document(usuarioId())

    fun entrevistas() = raiz().collection("entrevistas")
    fun vagas() = raiz().collection("vagas")
    fun roteiros() = raiz().collection("roteiros")
    fun perguntas() = raiz().collection("perguntas")
    fun respostas() = raiz().collection("respostas")
    fun experiencias() = raiz().collection("experiencias")
    fun definicoes() = raiz().collection("definicoes").document("padrao")
    fun contador() = raiz().collection("_meta").document("contadores")
}

/** Observa uma query e emite o resultado a cada mudança. */
internal fun <T> Query.fluxo(mapear: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
    val ouvinte = addSnapshotListener { resultado, erro ->
        if (erro != null) {
            close(erro)
            return@addSnapshotListener
        }
        resultado?.let { trySend(mapear(it)) }
    }
    awaitClose { ouvinte.remove() }
}

/** Observa um documento e emite o resultado a cada mudança. */
internal fun <T> DocumentReference.fluxo(mapear: (DocumentSnapshot?) -> T): Flow<T> = callbackFlow {
    val ouvinte = addSnapshotListener { documento, erro ->
        if (erro != null) {
            close(erro)
            return@addSnapshotListener
        }
        trySend(mapear(documento))
    }
    awaitClose { ouvinte.remove() }
}

/**
 * Gera ids numéricos únicos por usuário.
 *
 * O Room usava autoincremento; aqui o id é derivado do relógio + um acaso
 * local, então dois aparelhos nunca geram o mesmo documento (e a escrita
 * offline não sobrescreve um documento já sincronizado). Vira o mesmo Long que
 * os repositórios e a interface já esperam.
 *
 * O valor fica SEMPRE abaixo de 2^53 (o site lê como número do JavaScript e
 * precisaria de exatidão): `(millis - época) * 4096 + acaso` em vez de
 * `millis shl 20`, que passa muito do limite seguro.
 */
internal fun novoId(): Long {
    val desdeEpoca = System.currentTimeMillis() - 1704067200000L
    val acaso = Random.nextInt(1 shl 12)
    return (desdeEpoca * 4096) + acaso.toLong()
}

// ---------------------------------------------------------------------------
// Conversão entre as entidades do Room e os documentos do Firestore.
// ---------------------------------------------------------------------------

/** Constrói um mapa pronto para set() descartando pares nulos (ausência). */
private fun campos(vararg pares: Pair<String, Any?>): Map<String, Any> =
    pares.mapNotNull { (chave, valor) -> valor?.let { chave to it } }.toMap()

internal fun DocumentSnapshot.paraEntrevista(): EntrevistaEntity? {
    val id = getLong("id") ?: return null
    val data = getString("data")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return EntrevistaEntity(
        id = id,
        data = data,
        inicioMinutos = (getLong("inicioMinutos") ?: 0L).toInt(),
        duracaoMinutos = (getLong("duracaoMinutos") ?: 45L).toInt(),
        nome = getString("nome").orEmpty(),
        telefone = getString("telefone").orEmpty(),
        curriculo = getString("curriculo").orEmpty(),
        status = StatusEntrevista.from(getString("status").orEmpty()),
        ordem = (getLong("ordem") ?: 0L).toInt(),
        inicioReal = getLong("inicioReal")?.let(Instant::ofEpochMilli),
        fimReal = getLong("fimReal")?.let(Instant::ofEpochMilli),
        respostasRoteiro = getString("respostasRoteiro").orEmpty(),
        notas = getString("notas").orEmpty(),
        tipoCurriculo = TipoCurriculo.from(getString("tipoCurriculo")),
        caminhoCurriculo = getString("caminhoCurriculo").orEmpty(),
        nomeArquivoCurriculo = getString("nomeArquivoCurriculo").orEmpty(),
        vagaId = getLong("vagaId"),
        inicioManual = getBoolean("inicioManual") ?: false,
    )
}

internal fun EntrevistaEntity.camposParaFirestore(): Map<String, Any> = campos(
    "id" to id,
    "data" to data.toString(),
    "inicioMinutos" to inicioMinutos,
    "duracaoMinutos" to duracaoMinutos,
    "nome" to nome,
    "telefone" to telefone,
    "curriculo" to curriculo,
    "status" to status.name,
    "ordem" to ordem,
    "inicioReal" to inicioReal?.toEpochMilli(),
    "fimReal" to fimReal?.toEpochMilli(),
    "respostasRoteiro" to respostasRoteiro,
    "notas" to notas,
    "tipoCurriculo" to tipoCurriculo.name,
    "caminhoCurriculo" to caminhoCurriculo,
    "nomeArquivoCurriculo" to nomeArquivoCurriculo,
    "vagaId" to vagaId,
    "inicioManual" to inicioManual,
)

internal fun DocumentSnapshot.paraVaga(): VagaEntity? {
    val id = getLong("id") ?: return null
    return VagaEntity(
        id = id,
        titulo = getString("titulo").orEmpty(),
        empresa = getString("empresa").orEmpty(),
        entradaMinutos = (getLong("entradaMinutos") ?: -1L).toInt(),
        saidaMinutos = (getLong("saidaMinutos") ?: -1L).toInt(),
        tipoContrato = getString("tipoContrato").orEmpty(),
        salarioBeneficios = getString("salarioBeneficios").orEmpty(),
        tempoExperiencia = getString("tempoExperiencia").orEmpty(),
        escolaridade = getString("escolaridade").orEmpty(),
        exigeHabilitacao = getString("exigeHabilitacao").orEmpty(),
        resumoAtividades = getString("resumoAtividades").orEmpty(),
        limiteCandidatos = (getLong("limiteCandidatos") ?: 0L).toInt(),
        ativa = getBoolean("ativa") ?: true,
    )
}

internal fun VagaEntity.camposParaFirestore(): Map<String, Any> = campos(
    "id" to id,
    "titulo" to titulo,
    "empresa" to empresa,
    "entradaMinutos" to entradaMinutos,
    "saidaMinutos" to saidaMinutos,
    "tipoContrato" to tipoContrato,
    "salarioBeneficios" to salarioBeneficios,
    "tempoExperiencia" to tempoExperiencia,
    "escolaridade" to escolaridade,
    "exigeHabilitacao" to exigeHabilitacao,
    "resumoAtividades" to resumoAtividades,
    "limiteCandidatos" to limiteCandidatos,
    "ativa" to ativa,
)

internal fun DocumentSnapshot.paraRoteiro(): RoteiroEntity? {
    val id = getLong("id") ?: return null
    return RoteiroEntity(
        id = id,
        titulo = getString("titulo").orEmpty(),
        conteudo = getString("conteudo").orEmpty(),
        ordem = (getLong("ordem") ?: 0L).toInt(),
        padrao = getBoolean("padrao") ?: false,
        vagaId = getLong("vagaId"),
    )
}

internal fun RoteiroEntity.camposParaFirestore(): Map<String, Any> = campos(
    "id" to id,
    "titulo" to titulo,
    "conteudo" to conteudo,
    "ordem" to ordem,
    "padrao" to padrao,
    "vagaId" to vagaId,
)

internal fun DocumentSnapshot.paraPergunta(): PerguntaEntity? {
    val id = getLong("id") ?: return null
    val roteiroId = getLong("roteiroId") ?: return null
    return PerguntaEntity(
        id = id,
        roteiroId = roteiroId,
        ordem = (getLong("ordem") ?: 0L).toInt(),
        titulo = getString("titulo").orEmpty(),
        tipo = TipoResposta.entries.firstOrNull { it.name == getString("tipo") } ?: TipoResposta.TEXTO,
        obrigatoria = getBoolean("obrigatoria") ?: false,
        dica = getString("dica").orEmpty(),
        respostaAutomatica = getString("respostaAutomatica")
            ?.let { nome -> RespostaAutomatica.entries.firstOrNull { it.name == nome } },
    )
}

internal fun PerguntaEntity.camposParaFirestore(): Map<String, Any> = campos(
    "id" to id,
    "roteiroId" to roteiroId,
    "ordem" to ordem,
    "titulo" to titulo,
    "tipo" to tipo.name,
    "obrigatoria" to obrigatoria,
    "dica" to dica,
    "respostaAutomatica" to respostaAutomatica?.name,
)

internal fun DocumentSnapshot.paraResposta(): RespostaEntity? {
    val entrevistaId = getLong("entrevistaId") ?: return null
    val perguntaId = getLong("perguntaId") ?: return null
    return RespostaEntity(
        entrevistaId = entrevistaId,
        perguntaId = perguntaId,
        texto = getString("texto").orEmpty(),
    )
}

/** Id estável de um documento de resposta: `entrevistaId-perguntaId`. */
internal fun idDeResposta(entrevistaId: Long, perguntaId: Long): String = "$entrevistaId-$perguntaId"

internal fun RespostaEntity.camposParaFirestore(): Map<String, Any> = campos(
    "entrevistaId" to entrevistaId,
    "perguntaId" to perguntaId,
    "texto" to texto,
)

internal fun DocumentSnapshot.paraExperiencia(): ExperienciaEntity? {
    val id = getLong("id") ?: return null
    return ExperienciaEntity(
        id = id,
        entrevistaId = getLong("entrevistaId") ?: 0L,
        ordem = (getLong("ordem") ?: 0L).toInt(),
        local = getString("local").orEmpty(),
        ano = getString("ano").orEmpty(),
        duracao = getString("duracao").orEmpty(),
        cargo = getString("cargo").orEmpty(),
        motivoSaida = getString("motivoSaida").orEmpty(),
    )
}

internal fun ExperienciaEntity.camposParaFirestore(): Map<String, Any> = campos(
    "id" to id,
    "entrevistaId" to entrevistaId,
    "ordem" to ordem,
    "local" to local,
    "ano" to ano,
    "duracao" to duracao,
    "cargo" to cargo,
    "motivoSaida" to motivoSaida,
)