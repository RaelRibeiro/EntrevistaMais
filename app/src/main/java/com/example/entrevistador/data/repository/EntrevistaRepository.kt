package com.example.entrevistador.data.repository

import com.example.entrevistador.data.local.dao.EntrevistaDao
import com.example.entrevistador.data.local.entity.EntrevistaEntity
import com.example.entrevistador.data.local.entity.paraDominio
import com.example.entrevistador.domain.AgendaScheduler
import com.example.entrevistador.domain.model.DefinicoesPadrao
import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.StatusEntrevista
import com.example.entrevistador.domain.model.TipoCurriculo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class EntrevistaRepository(
    private val dao: EntrevistaDao,
    private val definicoesRepository: ProvedorDefinicoes,
    private val formularioRepository: EntrevistaFormularioRepository,
) {

    fun observarPorData(data: LocalDate): Flow<List<Entrevista>> =
        dao.observarPorData(data).map { lista -> lista.map { it.paraDominio() } }

    fun observarPorId(id: Long): Flow<Entrevista?> =
        dao.observarPorId(id).map { it?.paraDominio() }

    suspend fun buscarPorId(id: Long): Entrevista? = dao.buscarPorId(id)?.paraDominio()

    suspend fun contarCandidatosDaVaga(vagaId: Long): Int = dao.contarPorVaga(vagaId)

    fun observarContagemPorVaga(): Flow<Map<Long, Int>> =
        dao.observarContagemPorVaga().map { lista -> lista.associate { it.vagaId to it.quantidade } }

    suspend fun adicionarCandidato(
        data: LocalDate,
        nome: String,
        telefone: String,
        categoria: String = "",
        resumoCurriculo: String = "",
        tipoCurriculo: TipoCurriculo = TipoCurriculo.RESUMO,
        caminhoCurriculo: String = "",
        nomeArquivoCurriculo: String = "",
        vagaId: Long? = null,

        inicioMinutos: Int? = null,
    ): Resultado {
        if (nome.isBlank()) return Resultado.Erro("Informe o nome do candidato.")
        if (telefone.isNotBlank() && telefone.filter(Char::isDigit).length < 10) {
            return Resultado.Erro("Telefone incompleto — inclua o DDD.")
        }
        if (tipoCurriculo == TipoCurriculo.RESUMO && resumoCurriculo.isBlank()) {
            return Resultado.Erro("Escreva um resumo do currículo ou anexe um PDF/imagem.")
        }
        if (tipoCurriculo != TipoCurriculo.RESUMO && caminhoCurriculo.isBlank()) {
            return Resultado.Erro("Anexe o arquivo do currículo.")
        }

        val definicoes = definicoesRepository.obter()
        val existentes = dao.listarPorData(data)
        if (existentes.size >= definicoes.quantidadePorDia) {
            return Resultado.Erro(
                "O dia já tem ${definicoes.quantidadePorDia} entrevistas, que é o limite definido."
            )
        }

        if (inicioMinutos != null) {
            if (inicioMinutos !in 0 until AgendaScheduler.MINUTOS_NO_DIA) {
                return Resultado.Erro("Horário inválido.")
            }
            if (inicioMinutos + definicoes.duracaoMinutos >= AgendaScheduler.MINUTOS_NO_DIA) {
                return Resultado.Erro("A entrevista não cabe antes da meia-noite com esse horário.")
            }
        }

        val id = dao.inserir(
            EntrevistaEntity(
                data = data,
                inicioMinutos = inicioMinutos ?: definicoes.horarioInicioMinutos,
                inicioManual = inicioMinutos != null,
                duracaoMinutos = definicoes.duracaoMinutos,
                nome = nome.trim(),
                telefone = telefone.trim(),
                categoria = categoria.trim(),
                curriculo = resumoCurriculo.trim(),
                status = StatusEntrevista.AGENDADA,
                ordem = existentes.size,
                tipoCurriculo = tipoCurriculo,
                caminhoCurriculo = caminhoCurriculo,
                nomeArquivoCurriculo = nomeArquivoCurriculo,
                vagaId = vagaId,
            )
        )

        persistirOrdem(dao.listarPorData(data), definicoes)
        return Resultado.Sucesso(id)
    }

    suspend fun proximoHorarioMinutos(data: LocalDate): Int {
        val definicoes = definicoesRepository.obter()
        val existentes = dao.listarPorData(data)

        val proximo = AgendaScheduler.Candidato(id = -1L, nome = "")
        val calculados = AgendaScheduler.calcularHorarios(
            definicoes,
            existentes.map { AgendaScheduler.Candidato(it.id, it.nome) } + proximo,
        )
        return calculados.firstOrNull { it.candidato.id == -1L }?.inicioMinutos
            ?: definicoes.horarioInicioMinutos
    }

    suspend fun remover(id: Long): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Finalize a entrevista em andamento antes de removê-la.")
        }
        formularioRepository.apagarDadosDaEntrevista(id)
        dao.excluirPorId(id)
        recalcular(entrevista.data)
        return Resultado.Removido(entrevista.caminhoCurriculo)
    }

    suspend fun moverParaCima(id: Long): Resultado = mover(id, delta = -1)

    suspend fun moverParaBaixo(id: Long): Resultado = mover(id, delta = 1)

    suspend fun alterarHorario(id: Long, novoInicioMinutos: Int): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Não é possível mudar o horário de uma entrevista em andamento.")
        }
        if (novoInicioMinutos !in 0 until AgendaScheduler.MINUTOS_NO_DIA) {
            return Resultado.Erro("Horário inválido.")
        }
        if (novoInicioMinutos + entrevista.duracaoMinutos >= AgendaScheduler.MINUTOS_NO_DIA) {
            return Resultado.Erro("A entrevista não cabe antes da meia-noite com esse horário.")
        }

        val dia = entrevista.data
        val conflito = dao.listarPorData(dia).firstOrNull { outra ->
            val fimOutro = outra.inicioMinutos + outra.duracaoMinutos
            val fimNovo = novoInicioMinutos + entrevista.duracaoMinutos
            outra.id != id && novoInicioMinutos < fimOutro && outra.inicioMinutos < fimNovo
        }
        val choca = conflito != null

        dao.atualizar(entrevista.copy(inicioMinutos = novoInicioMinutos, inicioManual = true))

        return if (choca) {
            Resultado.Aviso("Horário alterado, mas choca com ${conflito!!.nome}.")
        } else {
            Resultado.Ok
        }
    }

    suspend fun voltarAoHorarioPadrao(id: Long): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (!entrevista.inicioManual) return Resultado.Ok
        dao.atualizar(entrevista.copy(inicioManual = false))
        recalcular(entrevista.data)
        return Resultado.Ok
    }

    suspend fun iniciar(id: Long, agora: Instant = Instant.now()): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.CONCLUIDA) {
            return Resultado.Erro("Esta entrevista já foi concluída.")
        }

        if (entrevista.status == StatusEntrevista.CANCELADA ||
            entrevista.status == StatusEntrevista.NAO_COMPARECEU ||
            entrevista.status.decidido ||
            entrevista.status == StatusEntrevista.ENCERRADA
        ) {
            return Resultado.Erro("Reabra a entrevista antes de iniciá-la.")
        }
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) return Resultado.Ok

        dao.atualizar(entrevista.copy(status = StatusEntrevista.EM_ANDAMENTO, inicioReal = agora))
        return Resultado.Ok
    }

    suspend fun finalizar(id: Long, agora: Instant = Instant.now()): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.ENCERRADA) {
            return Resultado.Erro("Reabra a entrevista encerrada para finalizá-la de novo.")
        }

        dao.atualizar(
            entrevista.copy(
                status = StatusEntrevista.CONCLUIDA,
                inicioReal = entrevista.inicioReal ?: agora,
                fimReal = agora,
            )
        )
        return Resultado.Ok
    }

    suspend fun cancelar(id: Long): Resultado = alterarStatusSimples(id, StatusEntrevista.CANCELADA)

    suspend fun marcarNaoCompareceu(id: Long): Resultado =
        alterarStatusSimples(id, StatusEntrevista.NAO_COMPARECEU)

    suspend fun aprovar(id: Long): Resultado = decidir(id, StatusEntrevista.APROVADO)

    suspend fun reprovar(id: Long): Resultado = decidir(id, StatusEntrevista.REPROVADO)

    private suspend fun decidir(id: Long, status: StatusEntrevista): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Finalize a entrevista antes de decidir.")
        }
        if (entrevista.status != StatusEntrevista.CONCLUIDA &&
            entrevista.status != StatusEntrevista.APROVADO &&
            entrevista.status != StatusEntrevista.REPROVADO
        ) {
            return Resultado.Erro("Só é possível decidir depois de concluir a entrevista.")
        }
        dao.atualizar(entrevista.copy(status = status))
        return Resultado.Ok
    }

    suspend fun encerrar(id: Long): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Finalize a entrevista antes de encerrá-la.")
        }
        if (!entrevista.status.encerravel) {
            return Resultado.Erro("Só é possível encerrar candidatos já avaliados.")
        }
        dao.atualizar(entrevista.copy(status = StatusEntrevista.ENCERRADA))
        return Resultado.Ok
    }

    suspend fun encerrarAvaliadosDoDia(data: LocalDate): Int {
        val candidatos = dao.listarPorData(data).filter { it.status.encerravel }
        if (candidatos.isEmpty()) return 0
        dao.atualizarTodas(
            candidatos.map { it.copy(status = StatusEntrevista.ENCERRADA) }
        )
        return candidatos.size
    }

    suspend fun reabrir(id: Long): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Finalize a entrevista antes de reabrí-la.")
        }

        dao.atualizar(
            entrevista.copy(
                status = StatusEntrevista.AGENDADA,
                inicioReal = null,
                fimReal = null,
            )
        )
        return Resultado.Ok
    }

    suspend fun voltarParaConcluida(id: Long): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Finalize a entrevista antes de alterar a decisão.")
        }
        if (entrevista.status != StatusEntrevista.APROVADO &&
            entrevista.status != StatusEntrevista.REPROVADO &&
            entrevista.status != StatusEntrevista.ENCERRADA
        ) {
            return Resultado.Erro("Esta entrevista ainda não tem decisão.")
        }
        dao.atualizar(entrevista.copy(status = StatusEntrevista.CONCLUIDA))
        return Resultado.Ok
    }

    private suspend fun alterarStatusSimples(id: Long, status: StatusEntrevista): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Finalize a entrevista antes de alterar o status.")
        }

        if (entrevista.status.encerravel && status != StatusEntrevista.ENCERRADA) {
            return Resultado.Erro("Reabra a entrevista antes de alterar o status.")
        }
        dao.atualizar(entrevista.copy(status = status))
        return Resultado.Ok
    }

    suspend fun salvarRespostas(id: Long, respostas: String): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        dao.atualizar(entrevista.copy(respostasRoteiro = respostas))
        return Resultado.Ok
    }

    suspend fun salvarCurriculo(id: Long, curriculo: String): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        dao.atualizar(entrevista.copy(curriculo = curriculo, tipoCurriculo = TipoCurriculo.RESUMO))
        return Resultado.Ok
    }

    suspend fun salvarCurriculoAnexado(
        id: Long,
        tipoCurriculo: TipoCurriculo,
        caminho: String,
        nomeArquivo: String,
    ): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        dao.atualizar(
            entrevista.copy(
                tipoCurriculo = tipoCurriculo,
                caminhoCurriculo = caminho,
                nomeArquivoCurriculo = nomeArquivo,
            )
        )
        return Resultado.Ok
    }

    suspend fun definirVaga(id: Long, vagaId: Long?): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        dao.atualizar(entrevista.copy(vagaId = vagaId))
        return Resultado.Ok
    }

    suspend fun reaplicarDefinicoes(data: LocalDate) {
        recalcular(data)
    }

    private suspend fun mover(id: Long, delta: Int): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Não é possível reordenar uma entrevista em andamento.")
        }

        val doDia = dao.listarPorData(entrevista.data).sortedBy { it.ordem }
        val indiceAtual = doDia.indexOfFirst { it.id == id }
        val indiceDestino = indiceAtual + delta
        if (indiceAtual < 0 || indiceDestino !in doDia.indices) return Resultado.Ok

        val reordenada = doDia.toMutableList().apply { add(indiceDestino, removeAt(indiceAtual)) }
        persistirOrdem(reordenada, definicoesRepository.obter())
        return Resultado.Ok
    }

    private suspend fun recalcular(data: LocalDate) {
        val doDia = dao.listarPorData(data).sortedBy { it.ordem }
        if (doDia.isEmpty()) return
        persistirOrdem(doDia, definicoesRepository.obter())
    }

    private suspend fun persistirOrdem(
        naNovaOrdem: List<EntrevistaEntity>,
        definicoes: DefinicoesPadrao,
    ) {
        if (naNovaOrdem.isEmpty()) return

        val horarios = AgendaScheduler.calcularHorarios(
            definicoes,
            naNovaOrdem.map { AgendaScheduler.Candidato(it.id, it.nome) },
        )
        val porId = naNovaOrdem.associateBy { it.id }
        dao.atualizarTodas(
            horarios.mapIndexed { indice, item ->
                val original = porId[item.candidato.id] ?: return@mapIndexed null
                original.copy(
                    ordem = indice,
                    inicioMinutos = if (original.inicioManual) {
                        original.inicioMinutos
                    } else {
                        item.inicioMinutos
                    },
                    duracaoMinutos = item.duracaoMinutos,
                )
            }.filterNotNull()
        )
    }

    sealed interface Resultado {
        data class Sucesso(val id: Long = 0L) : Resultado

        data object Ok : Resultado

        data class Aviso(val mensagem: String) : Resultado

        data class Removido(val caminhoCurriculo: String = "") : Resultado

        data class Erro(val mensagem: String) : Resultado
    }
}
