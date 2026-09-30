package com.example.entrevistador.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR: Locale = Locale("pt", "BR")

private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", PT_BR)
private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)
private val FORMATO_DIA_SEMANA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", PT_BR)
private val FORMATO_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", PT_BR)
private val FORMATO_CRONOMETRO: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", PT_BR)

/** "540" -> "09:00". */
fun Int.minutosParaHora(): String =
    FORMATO_HORA.format(LocalTime.ofSecondOfDay(coerceIn(0, 24 * 60) * 60L))

fun Int.minutosParaHoraComSufixo(): String {
    val horas = this / 60
    val minutos = this % 60
    return when {
        horas > 0 && minutos > 0 -> "${horas}h${minutos}min"
        horas > 0 -> "${horas}h"
        else -> "${minutos}min"
    }
}

fun Int.minutosParaCronometro(): String {
    val seguro = coerceAtLeast(0)
    return FORMATO_CRONOMETRO.format(LocalTime.ofSecondOfDay((seguro * 60L).coerceAtMost(24 * 60 * 60L - 1)))
}

fun LocalDate.paraTexto(): String = FORMATO_DATA.format(this)

fun LocalDate.paraDiaDaSemana(): String =
    FORMATO_DIA_SEMANA.format(this).replaceFirstChar { it.uppercase() }

fun LocalDate.paraMes(): String =
    FORMATO_MES.format(this).replaceFirstChar { it.uppercase() }

/** "Hoje", "Amanhã" ou "12/03/2026 - quinta-feira". */
fun LocalDate.paraTituloAgenda(): String = when (this) {
    LocalDate.now() -> "Hoje - ${paraDiaDaSemana()}"
    LocalDate.now().plusDays(1) -> "Amanhã - ${paraDiaDaSemana()}"
    else -> "${paraTexto()} - ${paraDiaDaSemana()}"
}

fun Long.paraDataLocal(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun Long.paraCronometro(): String =
    Duration.ofMillis(coerceAtLeast(0)).toCronometro()

private fun Duration.toCronometro(): String {
    val totalSegundos = seconds
    return String.format(
        PT_BR,
        "%02d:%02d:%02d",
        totalSegundos / 3600,
        (totalSegundos % 3600) / 60,
        totalSegundos % 60,
    )
}
