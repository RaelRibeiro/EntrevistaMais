package com.example.entrevistador.domain.model

import java.time.LocalTime

/**
 * Valores padrão definidos pelo recrutador. Toda vez que um candidato é
 * adicionado, removido ou reordenado, a agenda do dia é recalculada a partir
 * daqui — por isso os horários ficam sempre organizados sozinhos.
 *
 * Os horários são guardados em minutos desde a meia-noite para facilitar a
 * persistência e os cálculos.
 */
data class DefinicoesPadrao(
    val horarioInicioMinutos: Int = 9 * 60,
    val almocoInicioMinutos: Int = 12 * 60,
    val almocoFimMinutos: Int = 13 * 60,
    val quantidadePorDia: Int = 6,
    val duracaoMinutos: Int = 45,
    val intervaloMinutos: Int = 0,
) {
    val horarioInicio: LocalTime get() = LocalTime.ofSecondOfDay(horarioInicioMinutos * 60L)

    val almocoInicio: LocalTime get() = LocalTime.ofSecondOfDay(almocoInicioMinutos * 60L)

    val almocoFim: LocalTime get() = LocalTime.ofSecondOfDay(almocoFimMinutos * 60L)

    companion object {
        val PADRAO = DefinicoesPadrao()

        const val DURACAO_MIN = 5
        const val DURACAO_MAX = 240
        const val QUANTIDADE_MIN = 1
        const val QUANTIDADE_MAX = 50
        const val INTERVALO_MAX = 120
    }
}

/** Resultado da validação de um formulário de definições, campo a campo. */
data class ErrosDefinicoes(
    val horarioInicio: String? = null,
    val almocoInicio: String? = null,
    val almocoFim: String? = null,
    val quantidade: String? = null,
    val duracao: String? = null,
    val intervalo: String? = null,
) {
    val possuiErros: Boolean
        get() = listOf(horarioInicio, almocoInicio, almocoFim, quantidade, duracao, intervalo)
            .any { it != null }
}
