package com.example.entrevistador.domain

import com.example.entrevistador.domain.model.DefinicoesPadrao
import com.example.entrevistador.domain.model.Entrevista

/**
 * Calcula os horários da agenda automaticamente.
 *
 * Regras, na ordem em que são aplicadas a cada candidato:
 *  1. A primeira entrevista começa no "Horário de Início" definido.
 *  2. Cada entrevista ocupa "Duração por Entrevista" minutos, mais
 *     "Intervalo" entre uma e outra.
 *  3. Se o horário cair dentro da janela de almoço, ele é empurrado para o fim
 *     do almoço (ninguém é entrevistado no meio do almoço).
 *  4. Depois de "Quantidade por dia" entrevistas, o dia está lotado — os
 *     candidatos excedentes são ignorados.
 */
object AgendaScheduler {

    const val MINUTOS_NO_DIA = 24 * 60

    /**
     * Devolve os minutos de início de cada entrevista, na ordem em que
     * [candidatos] foram informados. A lista devolvida pode ser menor que a de
     * entrada se o dia lotar.
     */
    fun calcularHorarios(
        definicoes: DefinicoesPadrao,
        candidatos: List<Candidato>,
    ): List<InicioCalculado> {
        val duracao = definicoes.duracaoMinutos.coerceAtLeast(1)
        val intervalo = definicoes.intervaloMinutos.coerceAtLeast(0)
        val limite = definicoes.quantidadePorDia.coerceAtLeast(1)
        val almocoInicio = definicoes.almocoInicioMinutos
        val almocoFim = definicoes.almocoFimMinutos

        var cursor = definicoes.horarioInicioMinutos.coerceIn(0, MINUTOS_NO_DIA - 1)
        val resultado = ArrayList<InicioCalculado>(minOf(candidatos.size, limite))

        for (candidato in candidatos) {
            if (resultado.size >= limite) break

            // Não deixa a entrevista começar dentro (nem cruzar) o almoço.
            if (cursor < almocoFim && cursor + duracao > almocoInicio) {
                cursor = almocoFim
            }

            // A entrevista precisa caber antes da meia-noite: uma entrevista que
            // termina exatamente às 24:00 não é agendável.
            if (cursor + duracao >= MINUTOS_NO_DIA) break

            resultado += InicioCalculado(
                candidato = candidato,
                inicioMinutos = cursor,
                duracaoMinutos = duracao,
            )
            cursor += duracao + intervalo
        }

        return resultado
    }

    /**
     * Recalcula os horários de uma lista de entrevistas já existente, preservando
     * a ordem ([Entrevista.ordem]) e o que já foi preenchido.
     */
    fun recalcular(
        definicoes: DefinicoesPadrao,
        entrevistas: List<Entrevista>,
    ): List<Entrevista> {
        val candidatos = entrevistas
            .sortedBy { it.ordem }
            .map { Candidato(id = it.id, nome = it.nome) }

        val calculados = calcularHorarios(definicoes, candidatos)
        val porId = entrevistas.associateBy { it.id }

        val resultado = ArrayList<Entrevista>(calculados.size)
        for ((indice, item) in calculados.withIndex()) {
            val original = porId[item.candidato.id] ?: continue
            resultado += original.copy(
                ordem = indice,
                inicioMinutos = item.inicioMinutos,
                duracaoMinutos = item.duracaoMinutos,
            )
        }

        // Entrevistas que ficaram fora do limite do dia saem da agenda.
        return resultado
    }

    /** Máximo de candidatos que ainda cabem no dia, ignorando a lista atual. */
    fun capacidadeDoDia(definicoes: DefinicoesPadrao): Int {
        val duracao = definicoes.duracaoMinutos.coerceAtLeast(1)
        val passo = duracao + definicoes.intervaloMinutos.coerceAtLeast(0)
        if (passo <= 0) return definicoes.quantidadePorDia

        var cursor = definicoes.horarioInicioMinutos
        var cabem = 0
        while (cabem < definicoes.quantidadePorDia) {
            if (cursor < definicoes.almocoFimMinutos && cursor + duracao > definicoes.almocoInicioMinutos) {
                cursor = definicoes.almocoFimMinutos
            }
            if (cursor + duracao >= MINUTOS_NO_DIA) break
            cabem++
            cursor += passo
        }
        return cabem
    }

    data class Candidato(
        val id: Long,
        val nome: String,
    )

    data class InicioCalculado(
        val candidato: Candidato,
        val inicioMinutos: Int,
        val duracaoMinutos: Int,
    ) {
        val fimMinutos: Int get() = inicioMinutos + duracaoMinutos
    }
}
