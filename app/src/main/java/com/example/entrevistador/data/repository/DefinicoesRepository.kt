package com.example.entrevistador.data.repository

import com.example.entrevistador.data.firebase.FirestoreFontes
import com.example.entrevistador.data.firebase.fluxo
import com.example.entrevistador.domain.model.DefinicoesPadrao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Só o que a [EntrevistaRepository] precisa saber.
 *
 * Existe para permitir testar o cálculo de agenda sem abrir o Firestore — o
 * repositório em si continua sendo quem lê e grava os valores.
 */
interface ProvedorDefinicoes {
    suspend fun obter(): DefinicoesPadrao
}

/**
 * Valores padrão do recrutador, guardados no Firestore (`usuarios/{uid}`).
 *
 * É o mesmo critério de antes: qualquer mudança aqui é aplicada na agenda por
 * quem consome (os horários são recalculados via [EntrevistaRepository]).
 */
class DefinicoesRepository internal constructor(private val fontes: FirestoreFontes) : ProvedorDefinicoes {

    private val documento get() = fontes.definicoes()

    val definicoes: Flow<DefinicoesPadrao> = documento.fluxo { documentoSalvo ->
        documentoSalvo?.paraDefinicoes() ?: DefinicoesPadrao.PADRAO
    }

    /** Lê do Firestore direto (uma viagem), sem esperar pelo Flow. */
    override suspend fun obter(): DefinicoesPadrao {
        val documentoSalvo = runCatching { documento.get().await() }.getOrNull()
        return documentoSalvo?.let { it.paraDefinicoes() } ?: DefinicoesPadrao.PADRAO
    }

    suspend fun salvar(definicoes: DefinicoesPadrao) {
        documento.set(
            mapOf(
                "horarioInicioMinutos" to definicoes.horarioInicioMinutos,
                "almocoInicioMinutos" to definicoes.almocoInicioMinutos,
                "almocoFimMinutos" to definicoes.almocoFimMinutos,
                "quantidadePorDia" to definicoes.quantidadePorDia,
                "duracaoMinutos" to definicoes.duracaoMinutos,
                "intervaloMinutos" to definicoes.intervaloMinutos,
            )
        ).await()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.paraDefinicoes(): DefinicoesPadrao =
        DefinicoesPadrao(
            horarioInicioMinutos = (getLong("horarioInicioMinutos") ?: DefinicoesPadrao.PADRAO.horarioInicioMinutos.toLong()).toInt(),
            almocoInicioMinutos = (getLong("almocoInicioMinutos") ?: DefinicoesPadrao.PADRAO.almocoInicioMinutos.toLong()).toInt(),
            almocoFimMinutos = (getLong("almocoFimMinutos") ?: DefinicoesPadrao.PADRAO.almocoFimMinutos.toLong()).toInt(),
            quantidadePorDia = (getLong("quantidadePorDia") ?: DefinicoesPadrao.PADRAO.quantidadePorDia.toLong()).toInt(),
            duracaoMinutos = (getLong("duracaoMinutos") ?: DefinicoesPadrao.PADRAO.duracaoMinutos.toLong()).toInt(),
            intervaloMinutos = (getLong("intervaloMinutos") ?: DefinicoesPadrao.PADRAO.intervaloMinutos.toLong()).toInt(),
        )
}