package com.example.entrevistador.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/** Casca com TopAppBar e barra de navegação inferior para as telas do app logado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CascaApp(
    navController: NavHostController,
    nomeUsuario: String,
    aoSair: () -> Unit,
    conteudo: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val rotaAtual = backStackEntry?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = nomeUsuario.ifBlank { "Entrevistador" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = aoSair) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair da conta")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MENU_PRINCIPAL.forEach { item ->
                    val selecionado = rotaAtual?.hierarchy?.any { it.route == item.rota } == true
                    NavigationBarItem(
                        selected = selecionado,
                        onClick = { navController.navegarPara(item.rota) },
                        icon = { Icon(item.icone, contentDescription = null) },
                        label = { Text(item.rotulo) },
                    )
                }
            }
        },
    ) { padding ->
        conteudo(Modifier.fillMaxSize().padding(padding))
    }
}

fun NavHostController.navegarPara(rota: String) {
    navigate(rota) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Abre a entrevista de um candidato.
 *
 * Não pode usar o mesmo caminho de [navegarPara]: aquele guarda e restaura o
 * estado das telas para a barra inferior, e o Navigation só mantém UM estado
 * salvo por rota. Como toda entrevista compartilha a rota `entrevista/{id}`, o
 * ViewModelStore de um candidato era restaurado ao abrir outro — e o candidato
 * novo aparecia com os dados do anterior, mesmo de quem já tinha sido apagado.
 * Aqui cada abertura parte de um back stack limpo, com estado próprio.
 */
fun NavHostController.abrirEntrevista(id: Long) {
    navigate(Rotas.entrevista(id))
}
