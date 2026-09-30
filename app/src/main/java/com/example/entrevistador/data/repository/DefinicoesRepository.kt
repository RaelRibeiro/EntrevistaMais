package com.example.entrevistador.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.entrevistador.domain.model.DefinicoesPadrao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStoreDefinicoes: DataStore<Preferences> by preferencesDataStore("definicoes")

/**
 * Só o que a [EntrevistaRepository] precisa saber.
 *
 * Existe para permitir testar o cálculo de agenda sem abrir o DataStore — o
 * repositório em si continua sendo quem lê e grava as preferências.
 */
interface ProvedorDefinicoes {
    suspend fun obter(): DefinicoesPadrao
}

/** Persiste os valores padrão definidos pelo recrutador. */
class DefinicoesRepository(private val context: Context) : ProvedorDefinicoes {

    private object Chaves {
        val horarioInicio = intPreferencesKey("horario_inicio")
        val almocoInicio = intPreferencesKey("almoco_inicio")
        val almocoFim = intPreferencesKey("almoco_fim")
        val quantidadePorDia = intPreferencesKey("quantidade_por_dia")
        val duracao = intPreferencesKey("duracao_minutos")
        val intervalo = intPreferencesKey("intervalo_minutos")
    }

    val definicoes: Flow<DefinicoesPadrao> = context.dataStoreDefinicoes.data.map { prefs ->
        DefinicoesPadrao(
            horarioInicioMinutos = prefs[Chaves.horarioInicio] ?: DefinicoesPadrao.PADRAO.horarioInicioMinutos,
            almocoInicioMinutos = prefs[Chaves.almocoInicio] ?: DefinicoesPadrao.PADRAO.almocoInicioMinutos,
            almocoFimMinutos = prefs[Chaves.almocoFim] ?: DefinicoesPadrao.PADRAO.almocoFimMinutos,
            quantidadePorDia = prefs[Chaves.quantidadePorDia] ?: DefinicoesPadrao.PADRAO.quantidadePorDia,
            duracaoMinutos = prefs[Chaves.duracao] ?: DefinicoesPadrao.PADRAO.duracaoMinutos,
            intervaloMinutos = prefs[Chaves.intervalo] ?: DefinicoesPadrao.PADRAO.intervaloMinutos,
        )
    }

    override suspend fun obter(): DefinicoesPadrao = definicoes.first()

    suspend fun salvar(definicoes: DefinicoesPadrao) {
        context.dataStoreDefinicoes.edit { prefs ->
            prefs[Chaves.horarioInicio] = definicoes.horarioInicioMinutos
            prefs[Chaves.almocoInicio] = definicoes.almocoInicioMinutos
            prefs[Chaves.almocoFim] = definicoes.almocoFimMinutos
            prefs[Chaves.quantidadePorDia] = definicoes.quantidadePorDia
            prefs[Chaves.duracao] = definicoes.duracaoMinutos
            prefs[Chaves.intervalo] = definicoes.intervaloMinutos
        }
    }
}
