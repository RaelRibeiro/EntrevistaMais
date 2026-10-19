package com.example.entrevistador.domain

import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.StatusEntrevista
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object OrdenacaoAgenda {

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

                    if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) 0 else 1
                }
                .thenBy { entrevista ->

                    distanciaMinutos(entrevista, agoraLocal)
                }
                .thenBy { it.ordem }
                .thenBy { it.nome.lowercase() }
        )
    }

    private fun distanciaMinutos(entrevista: Entrevista, agora: LocalDateTime): Long {
        val dias = agora.toLocalDate().toEpochDay() - entrevista.data.toEpochDay()
        if (dias < 0) return 1_000_000L - dias
        if (dias > 0) return 2_000_000L - dias

        val inicio = LocalDateTime.of(entrevista.data, entrevista.horaInicio)
        val minutos = Duration.between(agora, inicio).toMinutes()

        return if (minutos < 0) 500_000L + minutos else minutos
    }
}
