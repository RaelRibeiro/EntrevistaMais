package com.example.entrevistador.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Rotas do app. Os argumentos ficam declarados aqui para não repetir string solta. */
object Rotas {
    const val LOGIN = "login"
    const val CRIAR_CONTA = "criarConta"
    const val AGENDA = "agenda"
    const val DEFINICOES = "definicoes"
    const val ROTEIRO = "roteiro"
    const val VAGAS = "vagas"

    const val ARG_ENTREVISTA_ID = "entrevistaId"
    const val ENTREVISTA = "entrevista/{$ARG_ENTREVISTA_ID}"

    const val ARG_VAGA_ID = "vagaId"
    const val ROTEIRO_DA_VAGA = "vaga/{$ARG_VAGA_ID}/roteiro"

    /** Visualizador de currículo do candidato, uma imagem por página. */
    const val CURRICULO = "entrevista/{$ARG_ENTREVISTA_ID}/curriculo"

    fun entrevista(id: Long): String = "entrevista/$id"

    /** Editor do roteiro próprio de uma vaga, que parte do roteiro padrão. */
    fun roteiroDaVaga(id: Long): String = "vaga/$id/roteiro"

    fun curriculo(entrevistaId: Long): String = "entrevista/$entrevistaId/curriculo"
}

data class ItemMenu(
    val rota: String,
    val rotulo: String,
    val icone: ImageVector,
)

val MENU_PRINCIPAL = listOf(
    ItemMenu(Rotas.AGENDA, "Agenda", Icons.Filled.EventNote),
    ItemMenu(Rotas.VAGAS, "Vagas", Icons.Filled.Work),
    ItemMenu(Rotas.DEFINICOES, "Definições", Icons.Filled.Tune),
    ItemMenu(Rotas.ROTEIRO, "Roteiro", Icons.AutoMirrored.Filled.List),
)

val ICONE_VOLTAR = Icons.AutoMirrored.Filled.ArrowBack
