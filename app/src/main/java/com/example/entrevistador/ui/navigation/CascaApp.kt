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
                        text = nomeUsuario.ifBlank { "EntrevistaMais" },
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

fun NavHostController.abrirEntrevista(id: Long) {
    navigate(Rotas.entrevista(id))
}
