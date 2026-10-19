package com.example.entrevistador.domain

import com.example.entrevistador.domain.model.DefinicoesPadrao
import com.example.entrevistador.domain.model.Entrevista

object AgendaScheduler {

    const val MINUTOS_NO_DIA = 24 * 60

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

            if (cursor < almocoFim && cursor + duracao > almocoInicio) {
                cursor = almocoFim
            }

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

        return resultado
    }

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
