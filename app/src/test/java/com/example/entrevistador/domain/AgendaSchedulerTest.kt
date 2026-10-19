package com.example.entrevistador.domain

import com.example.entrevistador.domain.model.DefinicoesPadrao
import com.example.entrevistador.domain.model.Entrevista
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgendaSchedulerTest {

    private fun padrao(
        inicio: Int = 9 * 60,
        almocoInicio: Int = 12 * 60,
        almocoFim: Int = 13 * 60,
        quantidade: Int = 6,
        duracao: Int = 45,
        intervalo: Int = 0,
    ) = DefinicoesPadrao(
        horarioInicioMinutos = inicio,
        almocoInicioMinutos = almocoInicio,
        almocoFimMinutos = almocoFim,
        quantidadePorDia = quantidade,
        duracaoMinutos = duracao,
        intervaloMinutos = intervalo,
    )

    private fun candidatos(n: Int) = (0 until n).map {
        AgendaScheduler.Candidato(id = it.toLong(), nome = "Candidato $it")
    }

    @Test
    fun `comeca no horario definido e avanca pela duracao`() {
        val horarios = AgendaScheduler.calcularHorarios(padrao(quantidade = 3), candidatos(3))

        assertEquals(listOf(9 * 60, 9 * 60 + 45, 9 * 60 + 90), horarios.map { it.inicioMinutos })
    }

    @Test
    fun `respeita o intervalo entre entrevistas`() {
        val horarios = AgendaScheduler.calcularHorarios(
            padrao(quantidade = 3, duracao = 30, intervalo = 15),
            candidatos(3),
        )

        assertEquals(listOf(9 * 60, 9 * 60 + 45, 9 * 60 + 90), horarios.map { it.inicioMinutos })
    }

    @Test
    fun `pula o almoco quando a entrevista cairia dentro dele`() {

        val horarios = AgendaScheduler.calcularHorarios(
            padrao(quantidade = 6, duracao = 45, almocoInicio = 12 * 60, almocoFim = 13 * 60),
            candidatos(6),
        )

        assertEquals(9 * 60, horarios[0].inicioMinutos)
        assertEquals(11 * 60 + 15, horarios[3].inicioMinutos)

        assertTrue(horarios.none { it.inicioMinutos in (12 * 60 until 13 * 60) })

        assertEquals(13 * 60, horarios[4].inicioMinutos)
    }

    @Test
    fun `nao cria entrevista que atravesse o inicio do almoco`() {
        val horarios = AgendaScheduler.calcularHorarios(
            padrao(quantidade = 4, duracao = 60, almocoInicio = 12 * 60, almocoFim = 13 * 60),
            candidatos(4),
        )

        assertEquals(listOf(9 * 60, 10 * 60, 11 * 60, 13 * 60), horarios.map { it.inicioMinutos })
    }

    @Test
    fun `limita pela quantidade por dia`() {
        val horarios = AgendaScheduler.calcularHorarios(padrao(quantidade = 2), candidatos(5))

        assertEquals(2, horarios.size)
    }

    @Test
    fun `nao gera horario quando o dia ja acabou`() {
        val horarios = AgendaScheduler.calcularHorarios(
            padrao(inicio = 23 * 60, duracao = 60, almocoInicio = 0, almocoFim = 0),
            candidatos(3),
        )

        assertTrue(horarios.isEmpty())
    }

    @Test
    fun `recalcular preserva ordem, duracao e dados ja preenchidos`() {
        val definicoes = padrao(quantidade = 5, duracao = 30)
        val existentes = candidatos(3).map { candidato ->
            Entrevista(
                id = candidato.id,
                data = LocalDate.of(2026, 3, 12),
                inicioMinutos = 999,
                duracaoMinutos = 999,
                nome = candidato.nome,
                telefone = "(11) 90000-0000",
                ordem = candidatos(3).indexOf(candidato),
            )
        }

        val recalculadas = AgendaScheduler.recalcular(definicoes, existentes)

        assertEquals(listOf(9 * 60, 9 * 60 + 30, 9 * 60 + 60), recalculadas.map { it.inicioMinutos })
        assertEquals(listOf(30, 30, 30), recalculadas.map { it.duracaoMinutos })
        assertTrue(recalculadas.all { it.telefone == "(11) 90000-0000" })
    }

    @Test
    fun `capacidade do dia considera o almoco`() {

        assertEquals(6, AgendaScheduler.capacidadeDoDia(padrao(quantidade = 6, duracao = 45)))
    }
}
