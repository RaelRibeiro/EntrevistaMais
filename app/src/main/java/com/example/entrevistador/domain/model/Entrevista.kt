package com.example.entrevistador.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class StatusEntrevista(val rotulo: String) {
    AGENDADA("Agendada"),
    EM_ANDAMENTO("Em andamento"),
    CONCLUIDA("Concluída"),
    CANCELADA("Cancelada"),
    NAO_COMPARECEU("Não compareceu"),

    APROVADO("Aprovado"),
    REPROVADO("Reprovado"),

    ENCERRADA("Encerrada"),
    ;

    val decidido: Boolean
        get() = this == APROVADO || this == REPROVADO

    val encerravel: Boolean
        get() = this == CONCLUIDA || decidido

    val finalizada: Boolean
        get() = this != AGENDADA && this != EM_ANDAMENTO

    companion object {
        fun from(valor: String): StatusEntrevista =
            entries.firstOrNull { it.name == valor } ?: AGENDADA
    }
}

data class Entrevista(
    val id: Long = 0L,
    val data: LocalDate,
    val inicioMinutos: Int,
    val duracaoMinutos: Int,
    val nome: String,
    val telefone: String,
    val curriculo: String = "",
    val status: StatusEntrevista = StatusEntrevista.AGENDADA,
    val ordem: Int = 0,
    val inicioReal: Instant? = null,
    val fimReal: Instant? = null,
    val respostasRoteiro: String = "",
    val notas: String = "",
    val tipoCurriculo: TipoCurriculo = TipoCurriculo.RESUMO,
    val caminhoCurriculo: String = "",
    val nomeArquivoCurriculo: String = "",
    val vagaId: Long? = null,

    val inicioManual: Boolean = false,
) {
    val fimMinutos: Int get() = inicioMinutos + duracaoMinutos

    val temArquivoCurriculo: Boolean get() = caminhoCurriculo.isNotBlank()

    val horaInicio: LocalTime get() = LocalTime.ofSecondOfDay(inicioMinutos * 60L)

    val horaFim: LocalTime get() = LocalTime.ofSecondOfDay(fimMinutos * 60L)

    val duracaoRealMs: Long?
        get() = if (inicioReal != null && fimReal != null) fimReal.toEpochMilli() - inicioReal.toEpochMilli() else null
}
