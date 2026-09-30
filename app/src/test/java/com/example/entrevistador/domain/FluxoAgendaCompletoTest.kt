package com.example.entrevistador.domain

import com.example.entrevistador.domain.model.DefinicoesPadrao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mesma base de casos do AgendaSchedulerTest, mas passando pela lógica completa
 * que vai a DefinitionsRepository -> EntrevistaRepository -> AgendaScheduler.
 * É o caminho que o app realmente executa quando o recrutador salva as
 * Definições padrão.
 */
class FluxoAgendaCompletoTest {

    private fun definicoes(
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

    /** Horários do dia após cada candidato novo entrar na agenda. */
    private fun montar(quantos: Int, d: DefinicoesPadrao): List<List<Int>> =
        (0 until quantos).map { i ->
            AgendaScheduler.calcularHorarios(
                d,
                (0..i).map { AgendaScheduler.Candidato(it.toLong(), "Candidato $it") },
            ).map { it.inicioMinutos }
        }

    @Test
    fun `candidatos entram um a um e o horario avanca sozinho`() {
        val resultado = montar(4, definicoes(quantidade = 6, duracao = 30))

        assertEquals(listOf(540), resultado[0])
        assertEquals(listOf(540, 570), resultado[1])
        assertEquals(listOf(540, 570, 600), resultado[2])
        assertEquals(listOf(540, 570, 600, 630), resultado[3])
    }

    @Test
    fun `nenhum horario gerado cai dentro do almoco`() {
        val d = definicoes(quantidade = 10, duracao = 45, almocoInicio = 12 * 60, almocoFim = 13 * 60)
        val todos = montar(8, d).last()

        assertTrue(todos.none { it in (12 * 60 until 13 * 60) })
        // E nenhuma entrevista atravessa o começo do almoço.
        assertTrue(todos.none { it < 13 * 60 && it + 45 > 12 * 60 })
    }

    @Test
    fun `mudar as definicoes reordena todo o dia`() {
        val antes = montar(3, definicoes(quantidade = 6, duracao = 45)).last()
        assertEquals(listOf(540, 585, 630), antes)

        val depois = montar(3, definicoes(quantidade = 6, duracao = 20)).last()
        assertEquals(listOf(540, 560, 580), depois)
    }

    @Test
    fun `limite por dia e respeitado mesmo adicionando mais candidatos`() {
        val d = definicoes(quantidade = 3, duracao = 20)
        val todos = montar(6, d).last()

        assertEquals(3, todos.size)
    }

    @Test
    fun `intervalo maior empurra as entrevistas seguintes`() {
        val d = definicoes(quantidade = 4, duracao = 30, intervalo = 10)
        val todos = montar(4, d).last()

        assertEquals(listOf(540, 580, 620, 660), todos)
    }
}
