package com.example.entrevistador.domain

import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.StatusEntrevista
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A ordem da agenda é o que o recrutador enxerga primeiro: o que está rolando
 * agora, depois o mais próximo, e o histórico só no fim.
 */
class OrdenacaoAgendaTest {

    private val hoje = LocalDate.of(2026, 3, 10)
    private val zona = ZoneId.of("America/Sao_Paulo")

    private fun agora(hora: Int, minuto: Int = 0): Instant =
        ZonedDateTime.of(hoje, java.time.LocalTime.of(hora, minuto), zona).toInstant()

    private fun entrevista(
        nome: String,
        hora: Int,
        minuto: Int = 0,
        status: StatusEntrevista = StatusEntrevista.AGENDADA,
        data: LocalDate = hoje,
    ) = Entrevista(
        id = nome.hashCode().toLong(),
        data = data,
        inicioMinutos = hora * 60 + minuto,
        duracaoMinutos = 30,
        nome = nome,
        telefone = "35999999999",
        status = status,
    )

    private fun nomes(lista: List<Entrevista>) = lista.map { it.nome }

    @Test
    fun `em andamento fica sempre no topo`() {
        val lista = listOf(
            entrevista("Cedulas", 9),
            entrevista("Ana", 10),
            entrevista("Bruno", 11),
            entrevista("Diana", 14, status = StatusEntrevista.EM_ANDAMENTO),
        )

        assertEquals("Diana", ordenar(lista, agora(11, 20)).first().nome)
    }

    @Test
    fun `agendadas vao do horario mais proximo para o mais longe`() {
        val lista = listOf(
            entrevista("Tarde", 16),
            entrevista("Manha", 9),
            entrevista("MeioDia", 13),
        )

        assertEquals(listOf("Manha", "MeioDia", "Tarde"), nomes(ordenar(lista, agora(8))))
    }

    @Test
    fun `ja adentro do horario passa para tras das futuras`() {
        val lista = listOf(
            entrevista("Passou", 9),
            entrevista("VemAi", 11),
            entrevista("Depois", 15),
        )

        assertEquals(listOf("VemAi", "Depois", "Passou"), nomes(ordenar(lista, agora(10))))
    }

    @Test
    fun `concluidas vem depois das agendadas`() {
        val lista = listOf(
            entrevista("Feita", 9, status = StatusEntrevista.CONCLUIDA),
            entrevista("Pendente", 16),
        )

        assertEquals(listOf("Pendente", "Feita"), nomes(ordenar(lista, agora(8))))
    }

    @Test
    fun `nao compareceu vem depois de concluida e antes de cancelada`() {
        val lista = listOf(
            entrevista("Cancelada", 9, status = StatusEntrevista.CANCELADA),
            entrevista("Faltou", 10, status = StatusEntrevista.NAO_COMPARECEU),
            entrevista("Feita", 11, status = StatusEntrevista.CONCLUIDA),
            entrevista("Agendada", 16),
        )

        assertEquals(
            listOf("Agendada", "Feita", "Faltou", "Cancelada"),
            nomes(ordenar(lista, agora(8))),
        )
    }    @Test
    fun `dia diferente do de hoje vai para o fim`() {
        val lista = listOf(
            entrevista("Amanha", 9, data = hoje.plusDays(1)),
            entrevista("Hoje", 17),
        )

        assertEquals(listOf("Hoje", "Amanha"), nomes(ordenar(lista, agora(8))))
    }

    @Test
    fun `ordem de insercao desempata horarios iguais`() {
        val primeira = entrevista("Ana", 9).copy(ordem = 0)
        val segunda = entrevista("Bia", 9).copy(ordem = 1)

        assertEquals(listOf("Ana", "Bia"), nomes(ordenar(listOf(segunda, primeira), agora(8))))
    }

    @Test
    fun `faixa de prioridade segue a ordem esperada`() {
        assertEquals(0, OrdenacaoAgenda.faixa(StatusEntrevista.EM_ANDAMENTO))
        assertEquals(1, OrdenacaoAgenda.faixa(StatusEntrevista.AGENDADA))
        assertEquals(2, OrdenacaoAgenda.faixa(StatusEntrevista.CONCLUIDA))
        assertEquals(3, OrdenacaoAgenda.faixa(StatusEntrevista.APROVADO))
        assertEquals(4, OrdenacaoAgenda.faixa(StatusEntrevista.REPROVADO))
        assertEquals(5, OrdenacaoAgenda.faixa(StatusEntrevista.NAO_COMPARECEU))
        assertEquals(6, OrdenacaoAgenda.faixa(StatusEntrevista.CANCELADA))
        assertEquals(7, OrdenacaoAgenda.faixa(StatusEntrevista.ENCERRADA))
    }

    @Test
    fun `decididas e encerradas vao para tras das agendadas`() {
        val lista = listOf(
            entrevista("Encerrada", 9, status = StatusEntrevista.ENCERRADA),
            entrevista("Reprovado", 10, status = StatusEntrevista.REPROVADO),
            entrevista("Aprovado", 11, status = StatusEntrevista.APROVADO),
            entrevista("Feita", 12, status = StatusEntrevista.CONCLUIDA),
            entrevista("Agendada", 16),
        )

        assertEquals(
            listOf("Agendada", "Feita", "Aprovado", "Reprovado", "Encerrada"),
            nomes(ordenar(lista, agora(8))),
        )
    }

    private fun ordenar(lista: List<Entrevista>, agora: Instant) =
        OrdenacaoAgenda.ordenarParaExibicao(lista, agora, zona)
}
