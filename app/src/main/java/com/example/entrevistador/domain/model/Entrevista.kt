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

    /**
     * Aprovado para a próxima fase. Sai de [CONCLUIDA] porque a decisão do
     * recrutador só existe depois da entrevista concluída.
     */
    APROVADO("Aprovado"),
    REPROVADO("Reprovado"),

    /**
     * Encerrado: o recrutador já validou e entrevistou este candidato e não
     * quer mais ele ocupando a agenda do dia. O registro é preservado para
     * consulta, só sai da lista do dia.
     */
    ENCERRADA("Encerrada"),
    ;

    /** Verdadeiro quando a entrevista já passou por decisão do recrutador. */
    val decidido: Boolean
        get() = this == APROVADO || this == REPROVADO

    /** Verdadeiro quando o candidato já foi avaliado e pode ser encerrado. */
    val encerravel: Boolean
        get() = this == CONCLUIDA || decidido

    companion object {
        fun from(valor: String): StatusEntrevista =
            entries.firstOrNull { it.name == valor } ?: AGENDADA
    }
}

/**
 * Uma entrevista agendada.
 *
 * O horário é guardado como minutos desde a meia-noite ([inicioMinutos]) em vez
 * de um instante absoluto: assim a agenda continua válida se o fuso do aparelho
 * mudar e é trivialmente ordenável.
 */
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
    /**
     * O horário foi escolhido a mão pelo recrutador, então o recálculo
     * automático não pode sobrescrever [inicioMinutos].
     */
    val inicioManual: Boolean = false,
) {
    val fimMinutos: Int get() = inicioMinutos + duracaoMinutos

    val temArquivoCurriculo: Boolean get() = caminhoCurriculo.isNotBlank()

    val horaInicio: LocalTime get() = LocalTime.ofSecondOfDay(inicioMinutos * 60L)

    val horaFim: LocalTime get() = LocalTime.ofSecondOfDay(fimMinutos * 60L)

    val duracaoRealMs: Long?
        get() = if (inicioReal != null && fimReal != null) fimReal.toEpochMilli() - inicioReal.toEpochMilli() else null
}
