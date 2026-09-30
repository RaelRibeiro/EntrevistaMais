package com.example.entrevistador.data.repository

import com.example.entrevistador.data.local.dao.ContagemPorVaga
import com.example.entrevistador.data.local.dao.EntrevistaDao
import com.example.entrevistador.data.local.dao.ExperienciaDao
import com.example.entrevistador.data.local.dao.RespostaDao
import com.example.entrevistador.data.local.entity.EntrevistaEntity
import com.example.entrevistador.data.local.entity.ExperienciaEntity
import com.example.entrevistador.data.local.entity.RespostaEntity
import com.example.entrevistador.data.local.entity.paraDominio
import com.example.entrevistador.domain.model.DefinicoesPadrao
import com.example.entrevistador.domain.model.Experiencia
import com.example.entrevistador.domain.model.StatusEntrevista
import com.example.entrevistador.domain.model.TipoCurriculo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Regressão do crash "o app fecha ao adicionar candidato".
 *
 * O bug: ao recalcular os horários, o mapa de ids era montado com a lista
 * lida *antes* do insert, mas a iteração usava a lista *depois*. O candidato
 * novo não existia no mapa e `getValue` estourava NoSuchElementException.
 */
class EntrevistaRepositoryTest {

    private val hoje = LocalDate.of(2026, 3, 10)

    private class DaoFalso : EntrevistaDao {
        private val linhas = MutableStateFlow<List<EntrevistaEntity>>(emptyList())
        private var proximoId = 1L

        val todas: List<EntrevistaEntity> get() = linhas.value

        override fun observarPorData(data: LocalDate): Flow<List<EntrevistaEntity>> =
            linhas.map { lista -> lista.filter { it.data == data }.sortedBy { it.ordem } }

        override suspend fun listarPorData(data: LocalDate): List<EntrevistaEntity> =
            linhas.value.filter { it.data == data }.sortedBy { it.ordem }

        override suspend fun buscarPorId(id: Long): EntrevistaEntity? =
            linhas.value.firstOrNull { it.id == id }

        override suspend fun buscarEmAndamento(data: LocalDate): EntrevistaEntity? =
            linhas.value.firstOrNull { it.data == data && it.status == StatusEntrevista.EM_ANDAMENTO }

        override suspend fun contarPorData(data: LocalDate): Int =
            linhas.value.count { it.data == data }

        override suspend fun contarPorVaga(vagaId: Long): Int =
            linhas.value.count { it.vagaId == vagaId }

        override fun observarContagemPorVaga(): Flow<List<ContagemPorVaga>> =
            linhas.map { lista ->
                lista.filter { it.vagaId != null }
                    .groupingBy { it.vagaId!! }
                    .eachCount()
                    .map { (vagaId, quantidade) -> ContagemPorVaga(vagaId, quantidade) }
            }

        override fun observarEmAndamento(data: LocalDate): Flow<EntrevistaEntity?> =
            linhas.map { lista ->
                lista.firstOrNull { it.data == data && it.status == StatusEntrevista.EM_ANDAMENTO }
            }

        override fun observarPorId(id: Long): Flow<EntrevistaEntity?> =
            linhas.map { lista -> lista.firstOrNull { it.id == id } }

        override suspend fun inserir(entrevista: EntrevistaEntity): Long {
            val id = proximoId++
            linhas.value = linhas.value + entrevista.copy(id = id)
            return id
        }

        override suspend fun atualizar(entrevista: EntrevistaEntity) {
            linhas.value = linhas.value.map { if (it.id == entrevista.id) entrevista else it }
        }

        override suspend fun atualizarTodas(entrevistas: List<EntrevistaEntity>) {
            val porId = entrevistas.associateBy { it.id }
            linhas.value = linhas.value.map { porId[it.id] ?: it }
        }

        override suspend fun excluirPorId(id: Long) {
            linhas.value = linhas.value.filterNot { it.id == id }
        }
    }

    private class DefinicoesFixas(private val valor: DefinicoesPadrao) : ProvedorDefinicoes {
        override suspend fun obter(): DefinicoesPadrao = valor
    }

    /** Guarda o que as tabelas filhas recebem, para o teste conferir a limpeza. */
    private class DadosFilhos {
        val respostas = mutableMapOf<Long, MutableMap<Long, String>>()
        val experiencias = mutableMapOf<Long, MutableList<ExperienciaEntity>>()
    }

    private class RespostaDaoFalso(private val dados: DadosFilhos) : RespostaDao {
        private fun daEntrevista(entrevistaId: Long) =
            dados.respostas[entrevistaId]?.map { (perguntaId, texto) ->
                RespostaEntity(entrevistaId, perguntaId, texto)
            }.orEmpty()

        override fun observarDaEntrevista(entrevistaId: Long): Flow<List<RespostaEntity>> =
            MutableStateFlow(daEntrevista(entrevistaId))

        override suspend fun listarDaEntrevista(entrevistaId: Long) = daEntrevista(entrevistaId)

        override suspend fun salvar(resposta: RespostaEntity) {
            dados.respostas.getOrPut(resposta.entrevistaId) { mutableMapOf() }[resposta.perguntaId] =
                resposta.texto
        }

        override suspend fun salvarTodas(respostas: List<RespostaEntity>) {
            respostas.forEach { salvar(it) }
        }

        override suspend fun excluirDaEntrevista(entrevistaId: Long) {
            dados.respostas.remove(entrevistaId)
        }
    }

    private class ExperienciaDaoFalso(private val dados: DadosFilhos) : ExperienciaDao {
        private var proximoId = 1L

        private fun daEntrevista(entrevistaId: Long) =
            dados.experiencias[entrevistaId].orEmpty().sortedBy { it.ordem }

        override fun observarDaEntrevista(entrevistaId: Long): Flow<List<ExperienciaEntity>> =
            MutableStateFlow(daEntrevista(entrevistaId))

        override suspend fun listarDaEntrevista(entrevistaId: Long) = daEntrevista(entrevistaId)

        override suspend fun inserir(experiencia: ExperienciaEntity): Long {
            val id = proximoId++
            dados.experiencias.getOrPut(experiencia.entrevistaId) { mutableListOf() }
                .add(experiencia.copy(id = id))
            return id
        }

        override suspend fun atualizar(experiencia: ExperienciaEntity) {
            val lista = dados.experiencias[experiencia.entrevistaId] ?: return
            val indice = lista.indexOfFirst { it.id == experiencia.id }
            if (indice >= 0) lista[indice] = experiencia
        }

        override suspend fun excluir(experiencia: ExperienciaEntity) {
            dados.experiencias[experiencia.entrevistaId]?.removeAll { it.id == experiencia.id }
        }

        override suspend fun excluirDaEntrevista(entrevistaId: Long) {
            dados.experiencias.remove(entrevistaId)
        }
    }

    private fun formulario(dados: DadosFilhos = DadosFilhos()): EntrevistaFormularioRepository =
        EntrevistaFormularioRepository(RespostaDaoFalso(dados), ExperienciaDaoFalso(dados))

    private fun definicoes(quantidade: Int = 6, duracao: Int = 30) = DefinicoesPadrao(
        horarioInicioMinutos = 9 * 60,
        almocoInicioMinutos = 12 * 60,
        almocoFimMinutos = 13 * 60,
        quantidadePorDia = quantidade,
        duracaoMinutos = duracao,
        intervaloMinutos = 0,
    )

    private fun repositorio(
        dao: DaoFalso = DaoFalso(),
        definicoes: DefinicoesPadrao = definicoes(),
    ) = EntrevistaRepository(dao, DefinicoesFixas(definicoes), formulario()) to dao

    private suspend fun adicionar(
        repository: EntrevistaRepository,
        nome: String,
        resumo: String = "Auxiliar com experiência",
    ) = repository.adicionarCandidato(
        data = hoje,
        nome = nome,
        telefone = "(35) 99999-0000",
        resumoCurriculo = resumo,
    )

    @Test
    fun `adicionar o primeiro candidato nao estoura excecao`() = runBlocking {
        val (repository, _) = repositorio()

        val resultado = adicionar(repository, "Ana")

        assertTrue(resultado is EntrevistaRepository.Resultado.Sucesso)
    }

    @Test
    fun `adicionar varios seguidos mantem todos na agenda`() = runBlocking {
        val (repository, dao) = repositorio()

        (1..4).forEach { adicionar(repository, "Candidato $it") }

        assertEquals(4, dao.todas.size)
        assertEquals(listOf(9, 9, 10, 10), dao.todas.sortedBy { it.ordem }.map { it.inicioMinutos / 60 })
    }

    @Test
    fun `o candidato novo recebe o ultimo horario livre`() = runBlocking {
        val (repository, dao) = repositorio(definicoes = definicoes(quantidade = 3, duracao = 30))

        adicionar(repository, "Ana")
        adicionar(repository, "Bia")

        val horarios = dao.todas.sortedBy { it.ordem }.map { it.inicioMinutos }
        assertEquals(9 * 60, horarios[0])
        assertEquals(9 * 60 + 30, horarios[1])
    }

    @Test
    fun `todos os candidatos tem id diferente e ordem sequencial`() = runBlocking {
        val (repository, dao) = repositorio()

        (1..3).forEach { adicionar(repository, "Candidato $it") }

        assertEquals(3, dao.todas.map { it.id }.distinct().size)
        assertEquals(listOf(0, 1, 2), dao.todas.sortedBy { it.ordem }.map { it.ordem })
    }

    @Test
    fun `bloqueia quando o dia atinge o limite`() = runBlocking {
        val (repository, _) = repositorio(definicoes = definicoes(quantidade = 1))

        adicionar(repository, "Ana")
        val segundo = adicionar(repository, "Bia")

        assertTrue(segundo is EntrevistaRepository.Resultado.Erro)
    }

    @Test
    fun `exige nome do candidato`() = runBlocking {
        val (repository, _) = repositorio()

        val resultado = repository.adicionarCandidato(hoje, "  ", "(35) 99999-0000", "resumo")

        assertTrue(resultado is EntrevistaRepository.Resultado.Erro)
    }

    @Test
    fun `exige resumo quando nao ha arquivo anexado`() = runBlocking {
        val (repository, _) = repositorio()

        val resultado = repository.adicionarCandidato(hoje, "Ana", "(35) 99999-0000", "")

        assertTrue(resultado is EntrevistaRepository.Resultado.Erro)
    }

    @Test
    fun `aceita anexo sem resumo`() = runBlocking {
        val (repository, _) = repositorio()

        val resultado = repository.adicionarCandidato(
            data = hoje,
            nome = "Ana",
            telefone = "(35) 99999-0000",
            tipoCurriculo = TipoCurriculo.PDF,
            caminhoCurriculo = "/data/curriculo.pdf",
            nomeArquivoCurriculo = "curriculo.pdf",
        )

        assertTrue(resultado is EntrevistaRepository.Resultado.Sucesso)
    }

    @Test
    fun `telefone incompleto e recusado`() = runBlocking {
        val (repository, _) = repositorio()

        val resultado = repository.adicionarCandidato(hoje, "Ana", "3599", "resumo")

        assertTrue(resultado is EntrevistaRepository.Resultado.Erro)
    }

    @Test
    fun `remover candidato reacomoda os horarios`() = runBlocking {
        val (repository, dao) = repositorio(definicoes = definicoes(quantidade = 3, duracao = 30))
        adicionar(repository, "Ana")
        adicionar(repository, "Bia")
        adicionar(repository, "Cida")

        val idDoMeio = dao.todas.sortedBy { it.ordem }[1].id
        repository.remover(idDoMeio)

        val restantes = dao.todas.sortedBy { it.ordem }
        assertEquals(listOf("Ana", "Cida"), restantes.map { it.nome })
        assertEquals(listOf(0, 1), restantes.map { it.ordem })
    }

    @Test
    fun `iniciar e finalizar registram os horarios reais`() = runBlocking {
        val (repository, dao) = repositorio()
        adicionar(repository, "Ana")
        val id = dao.todas.single().id
        val agora = Instant.parse("2026-03-10T13:00:00Z")

        repository.iniciar(id, agora)
        assertEquals(StatusEntrevista.EM_ANDAMENTO, dao.todas.single().status)

        repository.finalizar(id, agora.plusSeconds(1800))
        val finalizada = dao.todas.single()
        assertEquals(StatusEntrevista.CONCLUIDA, finalizada.status)
        assertEquals(1_800_000L, finalizada.paraDominio().duracaoRealMs)
    }

    @Test
    fun `nao permite iniciar entrevista ja concluida`() = runBlocking {
        val (repository, dao) = repositorio()
        adicionar(repository, "Ana")
        val id = dao.todas.single().id
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        repository.iniciar(id, agora)
        repository.finalizar(id, agora)

        val resultado = repository.iniciar(id, agora)

        assertTrue(resultado is EntrevistaRepository.Resultado.Erro)
    }

    @Test
    fun `nao permite iniciar entrevista cancelada`() = runBlocking {
        val (repository, dao) = repositorio()
        adicionar(repository, "Ana")
        val id = dao.todas.single().id
        repository.cancelar(id)

        val resultado = repository.iniciar(id, Instant.parse("2026-03-10T13:00:00Z"))

        assertTrue(resultado is EntrevistaRepository.Resultado.Erro)
        assertEquals(StatusEntrevista.CANCELADA, dao.todas.single().status)
    }

    @Test
    fun `nao permite iniciar entrevista de quem nao compareceu`() = runBlocking {
        val (repository, dao) = repositorio()
        adicionar(repository, "Ana")
        val id = dao.todas.single().id
        repository.marcarNaoCompareceu(id)

        val resultado = repository.iniciar(id, Instant.parse("2026-03-10T13:00:00Z"))

        assertTrue(resultado is EntrevistaRepository.Resultado.Erro)
        assertEquals(StatusEntrevista.NAO_COMPARECEU, dao.todas.single().status)
    }

    @Test
    fun `reabrir devolve a entrevista cancelada para agendada e permite iniciar`() = runBlocking {
        val (repository, dao) = repositorio()
        adicionar(repository, "Ana")
        val id = dao.todas.single().id
        repository.cancelar(id)

        repository.reabrir(id)
        assertEquals(StatusEntrevista.AGENDADA, dao.todas.single().status)

        val agora = Instant.parse("2026-03-10T13:00:00Z")
        assertTrue(repository.iniciar(id, agora) is EntrevistaRepository.Resultado.Ok)
        assertEquals(StatusEntrevista.EM_ANDAMENTO, dao.todas.single().status)
    }

    /**
     * Regressão: as respostas e a tabela de locais ficavam órfãs no banco depois
     * da remoção, apontando para um candidato que já não existe.
     */
    @Test
    fun `remover candidato apaga tambem as respostas e a tabela de locais`() = runBlocking {
        val dao = DaoFalso()
        val dados = DadosFilhos()
        val formulario = formulario(dados)
        val repository = EntrevistaRepository(dao, DefinicoesFixas(definicoes()), formulario)
        adicionar(repository, "Ana")
        val id = dao.todas.single().id

        formulario.salvarResposta(id, perguntaId = 7L, texto = "Resposta da Ana")
        formulario.salvarExperiencia(
            Experiencia(entrevistaId = id, ordem = 0, local = "Empresa Antiga", cargo = "Vendedor"),
        )
        assertEquals(1, dados.respostas.size)

        val resultado = repository.remover(id)

        assertEquals(EntrevistaRepository.Resultado.Removido(""), resultado)
        assertTrue(dados.respostas.isEmpty())
        assertTrue(dados.experiencias.isEmpty())
        assertTrue(dao.todas.isEmpty())
    }

    @Test
    fun `remover candidato devolve o caminho do curriculo para apagar o arquivo`() = runBlocking {
        val dao = DaoFalso()
        val repository = EntrevistaRepository(dao, DefinicoesFixas(definicoes()), formulario())
        repository.adicionarCandidato(
            data = hoje,
            nome = "Ana",
            telefone = "35999999999",
            resumoCurriculo = "",
            tipoCurriculo = TipoCurriculo.PDF,
            caminhoCurriculo = "/data/curriculos/cv_1.pdf",
            nomeArquivoCurriculo = "ana.pdf",
        )
        val id = dao.todas.single().id

        val resultado = repository.remover(id)

        assertEquals(
            EntrevistaRepository.Resultado.Removido("/data/curriculos/cv_1.pdf"),
            resultado,
        )
    }

    // --- aprovar / reprovar / encerrar ---

    @Test
    fun `aprovar move a entrevista concluida para a proxima fase`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        concluir(repository, dao, agora)

        assertTrue(repository.aprovar(dao.todas.single().id) is EntrevistaRepository.Resultado.Ok)
        assertEquals(StatusEntrevista.APROVADO, dao.todas.single().status)
    }

    @Test
    fun `reprovar move a entrevista concluida para reprovada`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        concluir(repository, dao, agora)

        assertTrue(repository.reprovar(dao.todas.single().id) is EntrevistaRepository.Resultado.Ok)
        assertEquals(StatusEntrevista.REPROVADO, dao.todas.single().status)
    }

    @Test
    fun `decisao pode ser trocada sem refazer a entrevista`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        concluir(repository, dao, agora)
        val id = dao.todas.single().id

        repository.aprovar(id)
        assertTrue(repository.reprovar(id) is EntrevistaRepository.Resultado.Ok)
        assertEquals(StatusEntrevista.REPROVADO, dao.todas.single().status)
    }

    @Test
    fun `nao permite decidir antes de concluir a entrevista`() = runBlocking {
        val (repository, dao) = repositorio()
        adicionar(repository, "Ana")
        val id = dao.todas.single().id

        val resultado = repository.aprovar(id)

        assertTrue(resultado is EntrevistaRepository.Resultado.Erro)
        assertEquals(StatusEntrevista.AGENDADA, dao.todas.single().status)
    }

    @Test
    fun `encerrar some com o candidato mas preserva o registro`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        concluir(repository, dao, agora)
        val id = dao.todas.single().id

        assertTrue(repository.encerrar(id) is EntrevistaRepository.Resultado.Ok)

        val encerrada = dao.todas.single()
        assertEquals(StatusEntrevista.ENCERRADA, encerrada.status)
        assertEquals("Ana", encerrada.nome)
    }

    @Test
    fun `encerrar avaliados do dia afeta so quem ja passou por entrevista`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        adicionar(repository, "Ana")
        adicionar(repository, "Bia")
        adicionar(repository, "Cida")
        val ids = dao.todas.sortedBy { it.ordem }.map { it.id }

        repository.finalizar(ids[0], agora)
        repository.cancelar(ids[1])
        // ids[2] continua agendada, sem entrevista.

        assertEquals(1, repository.encerrarAvaliadosDoDia(hoje))

        val porNome = dao.todas.associateBy { it.nome }
        assertEquals(StatusEntrevista.ENCERRADA, porNome.getValue("Ana").status)
        assertEquals(StatusEntrevista.CANCELADA, porNome.getValue("Bia").status)
        assertEquals(StatusEntrevista.AGENDADA, porNome.getValue("Cida").status)
    }

    @Test
    fun `encerrar avaliados do dia nao mexe em outro dia`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        adicionar(repository, "Ana")
        repository.finalizar(dao.todas.single().id, agora)

        assertEquals(0, repository.encerrarAvaliadosDoDia(hoje.plusDays(1)))
        assertEquals(StatusEntrevista.CONCLUIDA, dao.todas.single().status)
    }

    @Test
    fun `cancelar depois de decidir e bloqueado`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        concluir(repository, dao, agora)
        val id = dao.todas.single().id
        repository.aprovar(id)

        assertTrue(repository.cancelar(id) is EntrevistaRepository.Resultado.Erro)
        assertEquals(StatusEntrevista.APROVADO, dao.todas.single().status)
    }

    @Test
    fun `iniciar depois de decidir e bloqueado`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        concluir(repository, dao, agora)
        val id = dao.todas.single().id
        repository.aprovar(id)

        assertTrue(repository.iniciar(id, agora) is EntrevistaRepository.Resultado.Erro)
        assertEquals(StatusEntrevista.APROVADO, dao.todas.single().status)
    }

    @Test
    fun `voltar para concluida mantem a entrevista e limpa a decisao`() = runBlocking {
        val (repository, dao) = repositorio()
        val agora = Instant.parse("2026-03-10T13:00:00Z")
        concluir(repository, dao, agora)
        val id = dao.todas.single().id
        repository.encerrar(id)

        assertTrue(repository.voltarParaConcluida(id) is EntrevistaRepository.Resultado.Ok)

        val restaurada = dao.todas.single()
        assertEquals(StatusEntrevista.CONCLUIDA, restaurada.status)
        // O tempo real continua salvo: encerrar não desfaz a entrevista.
        assertEquals(1_800_000L, restaurada.paraDominio().duracaoRealMs)
    }

    private suspend fun concluir(
        repository: EntrevistaRepository,
        dao: DaoFalso,
        agora: Instant,
    ): Long {
        adicionar(repository, "Ana")
        val id = dao.todas.single().id
        repository.iniciar(id, agora)
        repository.finalizar(id, agora.plusSeconds(1800))
        return id
    }
}
