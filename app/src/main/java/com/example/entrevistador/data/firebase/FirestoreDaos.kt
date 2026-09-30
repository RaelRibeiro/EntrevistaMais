package com.example.entrevistador.data.firebase

import com.example.entrevistador.data.firebase.paraEntrevista as paraEntrevistaDoc
import com.example.entrevistador.data.firebase.paraExperiencia as paraExperienciaDoc
import com.example.entrevistador.data.firebase.paraPergunta as paraPerguntaDoc
import com.example.entrevistador.data.firebase.paraResposta as paraRespostaDoc
import com.example.entrevistador.data.firebase.paraRoteiro as paraRoteiroDoc
import com.example.entrevistador.data.firebase.paraVaga as paraVagaDoc
import com.example.entrevistador.data.local.dao.ContagemPorVaga
import com.example.entrevistador.data.local.dao.EntrevistaDao
import com.example.entrevistador.data.local.dao.ExperienciaDao
import com.example.entrevistador.data.local.dao.PerguntaDao
import com.example.entrevistador.data.local.dao.RespostaDao
import com.example.entrevistador.data.local.dao.RoteiroDao
import com.example.entrevistador.data.local.dao.VagaDao
import com.example.entrevistador.data.local.entity.EntrevistaEntity
import com.example.entrevistador.data.local.entity.ExperienciaEntity
import com.example.entrevistador.data.local.entity.PerguntaEntity
import com.example.entrevistador.data.local.entity.RespostaEntity
import com.example.entrevistador.data.local.entity.RoteiroEntity
import com.example.entrevistador.data.local.entity.VagaEntity
import com.example.entrevistador.domain.model.StatusEntrevista
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Converte a escrita do Room (substituição do documento) em escrita Firestore:
 * valores nulos viram `FieldValue.delete()` para que o documento volte ao estado
 * limpo, em vez de manter um valor antigo indevidamente.
 */
private fun Map<String, Any>.comNulosComoDelete(): Map<String, Any> =
    mapValues { (_, valor) -> valor ?: com.google.firebase.firestore.FieldValue.delete() }

internal class FirestoreEntrevistaDao(private val fontes: FirestoreFontes) : EntrevistaDao {

    private val colecao get() = fontes.entrevistas()

    override fun observarPorData(data: LocalDate): Flow<List<EntrevistaEntity>> =
        colecao.whereEqualTo("data", data.toString()).fluxo { resultado ->
            resultado.documents.mapNotNull { it.paraEntrevistaDoc() }.sortedBy { it.ordem }
        }

    override suspend fun listarPorData(data: LocalDate): List<EntrevistaEntity> =
        colecao.whereEqualTo("data", data.toString()).get().await()
            .documents.mapNotNull { it.paraEntrevistaDoc() }.sortedBy { it.ordem }

    override suspend fun buscarPorId(id: Long): EntrevistaEntity? =
        colecao.document("$id").get().await()?.let { it.paraEntrevistaDoc() }

    override suspend fun buscarEmAndamento(data: LocalDate): EntrevistaEntity? =
        colecao.whereEqualTo("data", data.toString())
            .whereEqualTo("status", StatusEntrevista.EM_ANDAMENTO.name)
            .get().await()
            .documents.firstNotNullOfOrNull { it.paraEntrevistaDoc() }

    override suspend fun contarPorData(data: LocalDate): Int =
        colecao.whereEqualTo("data", data.toString()).get().await().size()

    override suspend fun contarPorVaga(vagaId: Long): Int =
        colecao.whereEqualTo("vagaId", vagaId).get().await().size()

    override fun observarContagemPorVaga(): Flow<List<ContagemPorVaga>> =
        colecao.fluxo { resultado ->
            resultado.documents
                .mapNotNull { it.paraEntrevistaDoc() }
                .filter { it.vagaId != null }
                .groupBy { it.vagaId }
                .map { (vagaId, lista) -> ContagemPorVaga(vagaId = vagaId!!, quantidade = lista.size) }
        }

    override fun observarEmAndamento(data: LocalDate): Flow<EntrevistaEntity?> =
        colecao.whereEqualTo("data", data.toString())
            .whereEqualTo("status", StatusEntrevista.EM_ANDAMENTO.name)
            .fluxo { resultado -> resultado.documents.firstNotNullOfOrNull { it.paraEntrevistaDoc() } }

    override fun observarPorId(id: Long): Flow<EntrevistaEntity?> =
        colecao.document("$id").fluxo { documento -> documento?.paraEntrevistaDoc() }

    override suspend fun inserir(entrevista: EntrevistaEntity): Long {
        val id = novoId()
        colecao.document("$id").set(entrevista.copy(id = id).camposParaFirestore())
        return id
    }

    override suspend fun atualizar(entrevista: EntrevistaEntity) {
        colecao.document("${entrevista.id}")
            .set(entrevista.camposParaFirestore().comNulosComoDelete(), SetOptions.merge())
    }

    override suspend fun atualizarTodas(entrevistas: List<EntrevistaEntity>) {
        if (entrevistas.isEmpty()) return
        val batch = colecao.firestore.batch()
        entrevistas.forEach { entrevista ->
            batch.set(
                colecao.document("${entrevista.id}"),
                entrevista.camposParaFirestore().comNulosComoDelete(),
                SetOptions.merge(),
            )
        }
        batch.commit().await()
    }

    override suspend fun excluirPorId(id: Long) {
        colecao.document("$id").delete().await()
    }
}

internal class FirestoreVagaDao(private val fontes: FirestoreFontes) : VagaDao {

    private val colecao get() = fontes.vagas()

    override fun observarTodas(): Flow<List<VagaEntity>> =
        colecao.fluxo { resultado ->
            resultado.documents.mapNotNull { it.paraVagaDoc() }
                .sortedWith(compareByDescending<VagaEntity> { it.ativa }.thenBy { it.titulo })
        }

    override suspend fun buscarPorId(id: Long): VagaEntity? =
        colecao.document("$id").get().await()?.let { it.paraVagaDoc() }

    override suspend fun contar(): Int = colecao.get().await().size()

    override suspend fun inserir(vaga: VagaEntity): Long {
        val id = novoId()
        colecao.document("$id").set(vaga.copy(id = id).camposParaFirestore())
        return id
    }

    override suspend fun atualizar(vaga: VagaEntity) {
        colecao.document("${vaga.id}")
            .set(vaga.camposParaFirestore().comNulosComoDelete(), SetOptions.merge())
    }

    override suspend fun excluirPorId(id: Long) {
        colecao.document("$id").delete().await()
    }
}

internal class FirestoreRoteiroDao(private val fontes: FirestoreFontes) : RoteiroDao {

    private val colecao get() = fontes.roteiros()

    private fun ordenar(lista: List<RoteiroEntity>): List<RoteiroEntity> =
        lista.sortedWith(compareBy({ it.ordem }, { it.id }))

    /** Roteiros do processo (sem vaga). */
    private suspend fun somenteDoProcesso() = colecao.get().await()
        .documents.mapNotNull { it.paraRoteiroDoc() }
        .filter { it.vagaId == null }
        .let { ordenar(it) }

    override fun observarTodos(): Flow<List<RoteiroEntity>> =
        colecao.fluxo { resultado ->
            resultado.documents.mapNotNull { it.paraRoteiroDoc() }
                .filter { it.vagaId == null }
                .let { ordenar(it) }
        }

    override fun observarPadrao(): Flow<RoteiroEntity?> =
        colecao.fluxo { resultado ->
            resultado.documents.mapNotNull { it.paraRoteiroDoc() }
                .filter { it.vagaId == null }
                .let { ordenar(it) }
                .firstOrNull { it.padrao }
        }

    override suspend fun buscarPadrao(): RoteiroEntity? =
        somenteDoProcesso().firstOrNull { it.padrao }

    override fun observarDaVaga(vagaId: Long): Flow<RoteiroEntity?> =
        colecao.whereEqualTo("vagaId", vagaId).fluxo { resultado ->
            resultado.documents.firstNotNullOfOrNull { it.paraRoteiroDoc() }
        }

    override suspend fun buscarDaVaga(vagaId: Long): RoteiroEntity? =
        colecao.whereEqualTo("vagaId", vagaId).get().await()
            .documents.firstNotNullOfOrNull { it.paraRoteiroDoc() }

    override suspend fun contar(): Int = colecao.get().await().size()

    override suspend fun inserir(roteiro: RoteiroEntity): Long {
        val id = novoId()
        colecao.document("$id").set(roteiro.copy(id = id).camposParaFirestore())
        return id
    }

    override suspend fun atualizar(roteiro: RoteiroEntity) {
        colecao.document("${roteiro.id}")
            .set(roteiro.camposParaFirestore().comNulosComoDelete(), SetOptions.merge())
    }

    override suspend fun excluirPorId(id: Long) {
        colecao.document("$id").delete().await()
    }
}

internal class FirestorePerguntaDao(private val fontes: FirestoreFontes) : PerguntaDao {

    private val colecao get() = fontes.perguntas()

    override fun observarTodos(): Flow<List<PerguntaEntity>> =
        colecao.fluxo { resultado ->
            resultado.documents.mapNotNull { it.paraPerguntaDoc() }
                .sortedWith(compareBy({ it.roteiroId }, { it.ordem }))
        }

    override fun observarDoRoteiro(roteiroId: Long): Flow<List<PerguntaEntity>> =
        colecao.whereEqualTo("roteiroId", roteiroId).fluxo { resultado ->
            resultado.documents.mapNotNull { it.paraPerguntaDoc() }.sortedBy { it.ordem }
        }

    override suspend fun listarDoRoteiro(roteiroId: Long): List<PerguntaEntity> =
        colecao.whereEqualTo("roteiroId", roteiroId).get().await()
            .documents.mapNotNull { it.paraPerguntaDoc() }.sortedBy { it.ordem }

    override suspend fun buscarPorId(id: Long): PerguntaEntity? =
        colecao.document("$id").get().await()?.let { it.paraPerguntaDoc() }

    override suspend fun inserir(pergunta: PerguntaEntity): Long {
        val id = novoId()
        colecao.document("$id").set(pergunta.copy(id = id).camposParaFirestore())
        return id
    }

    override suspend fun inserirTodas(perguntas: List<PerguntaEntity>) {
        if (perguntas.isEmpty()) return
        val batch = colecao.firestore.batch()
        perguntas.forEach { pergunta ->
            val id = novoId()
            batch.set(colecao.document("$id"), pergunta.copy(id = id).camposParaFirestore())
        }
        batch.commit().await()
    }

    override suspend fun atualizar(pergunta: PerguntaEntity) {
        colecao.document("${pergunta.id}")
            .set(pergunta.camposParaFirestore().comNulosComoDelete(), SetOptions.merge())
    }

    override suspend fun excluir(pergunta: PerguntaEntity) {
        colecao.document("${pergunta.id}").delete().await()
    }

    override suspend fun excluirDoRoteiro(roteiroId: Long) {
        val ids = colecao.whereEqualTo("roteiroId", roteiroId).get().await().documents.map { it.id }
        if (ids.isEmpty()) return
        val batch = colecao.firestore.batch()
        ids.forEach { id -> batch.delete(colecao.document(id)) }
        batch.commit().await()
    }
}

internal class FirestoreRespostaDao(private val fontes: FirestoreFontes) : RespostaDao {

    private val colecao get() = fontes.respostas()

    override fun observarDaEntrevista(entrevistaId: Long): Flow<List<RespostaEntity>> =
        colecao.whereEqualTo("entrevistaId", entrevistaId).fluxo { resultado ->
            resultado.documents.mapNotNull { it.paraRespostaDoc() }
        }

    override suspend fun listarDaEntrevista(entrevistaId: Long): List<RespostaEntity> =
        colecao.whereEqualTo("entrevistaId", entrevistaId).get().await()
            .documents.mapNotNull { it.paraRespostaDoc() }

    override suspend fun salvar(resposta: RespostaEntity) {
        colecao.document(idDeResposta(resposta.entrevistaId, resposta.perguntaId))
            .set(resposta.camposParaFirestore(), SetOptions.merge())
    }

    override suspend fun salvarTodas(respostas: List<RespostaEntity>) {
        if (respostas.isEmpty()) return
        val batch = colecao.firestore.batch()
        respostas.forEach { resposta ->
            batch.set(
                colecao.document(idDeResposta(resposta.entrevistaId, resposta.perguntaId)),
                resposta.camposParaFirestore(),
                SetOptions.merge(),
            )
        }
        batch.commit().await()
    }

    override suspend fun excluirDaEntrevista(entrevistaId: Long) {
        val ids = colecao.whereEqualTo("entrevistaId", entrevistaId).get().await().documents.map { it.id }
        if (ids.isEmpty()) return
        val batch = colecao.firestore.batch()
        ids.forEach { id -> batch.delete(colecao.document(id)) }
        batch.commit().await()
    }
}

internal class FirestoreExperienciaDao(private val fontes: FirestoreFontes) : ExperienciaDao {

    private val colecao get() = fontes.experiencias()

    override fun observarDaEntrevista(entrevistaId: Long): Flow<List<ExperienciaEntity>> =
        colecao.whereEqualTo("entrevistaId", entrevistaId).fluxo { resultado ->
            resultado.documents.mapNotNull { it.paraExperienciaDoc() }.sortedBy { it.ordem }
        }

    override suspend fun listarDaEntrevista(entrevistaId: Long): List<ExperienciaEntity> =
        colecao.whereEqualTo("entrevistaId", entrevistaId).get().await()
            .documents.mapNotNull { it.paraExperienciaDoc() }.sortedBy { it.ordem }

    override suspend fun inserir(experiencia: ExperienciaEntity): Long {
        val id = novoId()
        colecao.document("$id").set(experiencia.copy(id = id).camposParaFirestore())
        return id
    }

    override suspend fun atualizar(experiencia: ExperienciaEntity) {
        colecao.document("${experiencia.id}")
            .set(experiencia.camposParaFirestore().comNulosComoDelete(), SetOptions.merge())
    }

    override suspend fun excluir(experiencia: ExperienciaEntity) {
        colecao.document("${experiencia.id}").delete().await()
    }

    override suspend fun excluirDaEntrevista(entrevistaId: Long) {
        val ids = colecao.whereEqualTo("entrevistaId", entrevistaId).get().await().documents.map { it.id }
        if (ids.isEmpty()) return
        val batch = colecao.firestore.batch()
        ids.forEach { id -> batch.delete(colecao.document(id)) }
        batch.commit().await()
    }
}