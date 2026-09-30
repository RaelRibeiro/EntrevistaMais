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

/**
 * Guarda as entrevistas e mantém os horários sempre organizados.
 *
 * Regra central: qualquer mudança na lista de candidatos de um dia dispara um
 * recálculo usando as [DefinicoesPadrao] vigentes. É isso que faz o recrutador
 * "jogar" candidatos na agenda e os horários se ajeitarem sozinhos.
 */
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

    /** Quantos candidatos já estão na vaga, para avisar quando o limite passar. */
    suspend fun contarCandidatosDaVaga(vagaId: Long): Int = dao.contarPorVaga(vagaId)

    /**
     * Contagem por vaga que se atualiza sozinha quando um candidato entra ou sai.
     * Vagas sem nenhum candidato não aparecem no mapa, e a interface trata a
     * ausência como zero.
     */
    fun observarContagemPorVaga(): Flow<Map<Long, Int>> =
        dao.observarContagemPorVaga().map { lista -> lista.associate { it.vagaId to it.quantidade } }

    /**
     * Currículo do candidato: um resumo escrito, um PDF ou uma imagem.
     * Links não são aceitos de propósito — link de currículo expira e some,
     * e o recrutador precisa do documento na hora da entrevista.
     */
    suspend fun adicionarCandidato(
        data: LocalDate,
        nome: String,
        telefone: String,
        resumoCurriculo: String = "",
        tipoCurriculo: TipoCurriculo = TipoCurriculo.RESUMO,
        caminhoCurriculo: String = "",
        nomeArquivoCurriculo: String = "",
        vagaId: Long? = null,
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

        val id = dao.inserir(
            EntrevistaEntity(
                data = data,
                inicioMinutos = definicoes.horarioInicioMinutos,
                duracaoMinutos = definicoes.duracaoMinutos,
                nome = nome.trim(),
                telefone = telefone.trim(),
                curriculo = resumoCurriculo.trim(),
                status = StatusEntrevista.AGENDADA,
                ordem = existentes.size,
                tipoCurriculo = tipoCurriculo,
                caminhoCurriculo = caminhoCurriculo,
                nomeArquivoCurriculo = nomeArquivoCurriculo,
                vagaId = vagaId,
            )
        )
        // A lista precisa ser relida depois do insert: o candidato novo só existe
        // no banco a partir de agora.
        persistirOrdem(dao.listarPorData(data), definicoes)
        return Resultado.Sucesso(id)
    }

    /**
     * Remove o candidato e tudo que pertencia a ele.
     *
     * As respostas do roteiro e a tabela de locais são apagadas antes da
     * entrevista: elas não têm cascade no banco, e ficavam órfãs apontando
     * para um candidato que não existe mais. Devolve o caminho do currículo
     * anexado para o app apagar também o arquivo.
     */
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

    /**
     * Muda só o horário de início deste candidato.
     *
     * As Definições dão o padrão da agenda, mas o dia real não segue o papel:
     * o candidato chega mais cedo, o anterior atrasou, e aí é preciso encurtar
     * ou adiar a chamada. O horário passa a ser manual ([inicioManual]) e passa
     * a resistir ao recálculo automático.
     *
     * A duração não muda: quem define o tempo é a definição, não este ajuste.
     * Se o novo horário chocar com outro candidato, a alteração é feita mesmo
     * assim e o app avisa — quem decide de encurtar a conversa é o recrutador.
     */
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

    /** Devolve o candidato para o horário calculado pelas Definições. */
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
        // Cancelada, "não compareceu", decidida e encerrada não começam a contar
        // tempo: se a pessoa voltou, o recrutador reabre a entrevista e só então
        // inicia.
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
        // Reaprovar ou reprovar significa voltar à conclusão: a entrevista já
        // aconteceu, só a decisão é que mudou.
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

    /**
     * Aprova o candidato para a próxima fase.
     *
     * Só faz sentido depois da entrevista concluída: decidir antes de entrevistar
     * não tem o que avaliar, então devolvemos um erro em vez de gravar.
     */
    suspend fun aprovar(id: Long): Resultado = decidir(id, StatusEntrevista.APROVADO)

    /** Reprova o candidato na fase atual. Mesmas regras de [aprovar]. */
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

    /**
     * Encerra um candidato: ele some da lista do dia, mas o registro, as
     * respostas e o currículo continuam guardados para consulta.
     */
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

    /**
     * Encerra de uma vez todos os candidatos do dia que já foram avaliados.
     *
     * É o atalho para o caso comum: o recrutador selecionou e entrevistou o
     * dia inteiro e quer só limpar a lista. Devolve quantos foram encerrados
     * para a interface avisar.
     */
    suspend fun encerrarAvaliadosDoDia(data: LocalDate): Int {
        val candidatos = dao.listarPorData(data).filter { it.status.encerravel }
        if (candidatos.isEmpty()) return 0
        dao.atualizarTodas(
            candidatos.map { it.copy(status = StatusEntrevista.ENCERRADA) }
        )
        return candidatos.size
    }

    /** Devolve a entrevista para AGENDADA, para o recrutador corrigir algo errado. */
    suspend fun reabrir(id: Long): Resultado {
        val entrevista = dao.buscarPorId(id) ?: return Resultado.Erro("Entrevista não encontrada.")
        if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
            return Resultado.Erro("Finalize a entrevista antes de reabrí-la.")
        }
        // Reabrir é sempre um recuo: volta para AGENDADA e zera a decisão e o
        // encerramento, senão o candidato reapareceria com um status que não
        // corresponde a nada.
        dao.atualizar(
            entrevista.copy(
                status = StatusEntrevista.AGENDADA,
                inicioReal = null,
                fimReal = null,
            )
        )
        return Resultado.Ok
    }

    /** Traz de volta só a decisão, mantendo a entrevista concluída. */
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
        // Cancelar ou marcar falta depois de decidir inverteria o sentido do
        // que o recrutador já avaliou, então exigimos voltar antes.
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

    /** Troca o currículo do candidato por um arquivo anexado. */
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

    /**
     * Reaplica as definições a um dia inteiro. Usado quando o recrutador muda
     * as configurações padrão: os horários do dia são rearranjados.
     */
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

    /** Recalcula e persiste os horários de [data] com as definições vigentes. */
    private suspend fun recalcular(data: LocalDate) {
        val doDia = dao.listarPorData(data).sortedBy { it.ordem }
        if (doDia.isEmpty()) return
        persistirOrdem(doDia, definicoesRepository.obter())
    }

    /**
     * Calcula os horários de [naNovaOrdem] e grava de volta.
     * [naNovaOrdem] é a lista completa do dia, já na ordem desejada.
     *
     * Quem tem [EntrevistaEntity.inicioManual] guarda o horário escolhido pelo
     * recrutador: as Definições continuam valendo para a duração e para todo o
     * resto, mas não sobrescrevem uma decisão manual.
     */
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

        /** Atalho para quando a ação não devolve nada (ex.: mover). */
        data object Ok : Resultado

        /**
         * A ação foi feita, mas o recrutador precisa ficar sabendo de um
         * detalhe — por exemplo, que o novo horário choca com outra entrevista.
         */
        data class Aviso(val mensagem: String) : Resultado

        /** Candidato removido; [caminhoCurriculo] é o arquivo a apagar, se houver. */
        data class Removido(val caminhoCurriculo: String = "") : Resultado

        data class Erro(val mensagem: String) : Resultado
    }
}
