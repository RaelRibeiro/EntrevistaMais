package com.example.entrevistador.domain

import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.StatusEntrevista
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ordem em que as entrevistas aparecem na agenda.
 *
 * O recrutador precisa ver primeiro o que está acontecendo agora, depois o que
 * vem a seguir (mais próximo para o mais longe), e só no fim o histórico. Por
 * isso as entrevistas já avaliadas e as encerradas vão para o fim, em vez de
 * ficarem misturadas com as próximas.
 */
object OrdenacaoAgenda {

    /**
     * Faixa de prioridade: quanto menor, mais perto do topo.
     */
    fun faixa(status: StatusEntrevista): Int = when (status) {
        StatusEntrevista.EM_ANDAMENTO -> 0
        StatusEntrevista.AGENDADA -> 1
        StatusEntrevista.CONCLUIDA -> 2
        StatusEntrevista.APROVADO -> 3
        StatusEntrevista.REPROVADO -> 4
        StatusEntrevista.NAO_COMPARECEU -> 5
        StatusEntrevista.CANCELADA -> 6
        StatusEntrevista.ENCERRADA -> 7
    }

    fun ordenarParaExibicao(
        entrevistas: List<Entrevista>,
        agora: Instant = Instant.now(),
        zona: ZoneId = ZoneId.systemDefault(),
    ): List<Entrevista> {
        val agoraLocal = LocalDateTime.ofInstant(agora, zona)
        return entrevistas.sortedWith(
            compareBy<Entrevista> { faixa(it.status) }
                .thenBy { entrevista ->
                    // Interview happening right now comes first inside its faixa.
                    if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) 0 else 1
                }
                .thenBy { entrevista ->
                    // "Mais próximo" = menor distância até agora. O que já passou
                    // fica logo atrás do que está por vir.
                    distanciaMinutos(entrevista, agoraLocal)
                }
                .thenBy { it.ordem }
                .thenBy { it.nome.lowercase() }
        )
    }

    /**
     * Distância usada só para ordenar: quanto menor, mais perto do topo.
     *
     * Faixas reservadas para não misturar os grupos (valores pequenos, sem risco
     * de overflow): hoje em [0, 500_000), dias futuros a partir de 1_000_000 e
     * dias passados a partir de 2_000_000.
     */
    private fun distanciaMinutos(entrevista: Entrevista, agora: LocalDateTime): Long {
        val dias = agora.toLocalDate().toEpochDay() - entrevista.data.toEpochDay()
        if (dias < 0) return 1_000_000L - dias // dia futuro
        if (dias > 0) return 2_000_000L - dias // dia passado

        val inicio = LocalDateTime.of(entrevista.data, entrevista.horaInicio)
        val minutos = Duration.between(agora, inicio).toMinutes()
        // O que já começou fica logo atrás do que está por vir, sem nunca
        // ultrapassar o bloco de "hoje".
        return if (minutos < 0) 500_000L + minutos else minutos
    }
}
