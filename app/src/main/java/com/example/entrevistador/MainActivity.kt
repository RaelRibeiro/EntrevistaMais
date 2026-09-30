package com.example.entrevistador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.entrevistador.di.AppContainer
import com.example.entrevistador.domain.model.Usuario
import com.example.entrevistador.ui.agenda.AgendaScreen
import com.example.entrevistador.ui.agenda.AgendaViewModel
import com.example.entrevistador.ui.auth.AuthViewModel
import com.example.entrevistador.ui.auth.CriarContaScreen
import com.example.entrevistador.ui.auth.LoginScreen
import com.example.entrevistador.ui.curriculo.CurriculoPaginasScreen
import com.example.entrevistador.ui.curriculo.CurriculoPaginasViewModel
import com.example.entrevistador.ui.definicoes.DefinicoesScreen
import com.example.entrevistador.ui.definicoes.DefinicoesViewModel
import com.example.entrevistador.ui.entrevista.EntrevistaScreen
import com.example.entrevistador.ui.entrevista.EntrevistaViewModel
import com.example.entrevistador.ui.navigation.CascaApp
import com.example.entrevistador.ui.navigation.Rotas
import com.example.entrevistador.ui.navigation.abrirEntrevista
import com.example.entrevistador.ui.navigation.navegarPara
import com.example.entrevistador.ui.roteiro.RoteiroScreen
import com.example.entrevistador.ui.roteiro.RoteiroViewModel
import com.example.entrevistador.ui.theme.EntrevistadorTheme
import com.example.entrevistador.ui.vagas.VagaScreen
import com.example.entrevistador.ui.vagas.VagaViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EntrevistadorTheme {
                AppEntrevistador(container = (application as EntrevistadorApplication).container)
            }
        }
    }
}

/**
 * Raiz do app.
 *
 * A sessão é observada do [com.example.entrevistador.data.auth.AuthRepository]
 * (Firebase), e não de um estado local: por isso criar conta, entrar e sair já
 * trocam a tela sem nenhum código extra aqui. [SessaoCarregando] cobre o
 * primeiro acesso à sessão, que é rápido mas assíncrono.
 */
@Composable
fun AppEntrevistador(container: AppContainer) {
    val usuario by container.authRepository.usuarioAtual.collectAsStateWithLifecycle(
        initialValue = Carregando,
    )

    when {
        // Enquanto a primeira leitura do DataStore não volta, mostramos um
        // spinner em vez de piscar a tela de login para quem já está logado.
        usuario == Carregando -> TelaDeEspera()
        usuario == null -> FluxoLogin(container)
        else -> FluxoLogado(container, requireNotNull(usuario))
    }
}

@Composable
private fun TelaDeEspera() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FluxoLogin(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Rotas.LOGIN) {
        composable(Rotas.LOGIN) {
            LoginScreen(
                viewModel = criarViewModel(container) { AuthViewModel(container.authRepository) },
                aoIrParaCriarConta = { navController.navegarPara(Rotas.CRIAR_CONTA) },
            )
        }
        composable(Rotas.CRIAR_CONTA) {
            CriarContaScreen(
                viewModel = criarViewModel(container) { AuthViewModel(container.authRepository) },
                aoVoltar = { navController.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluxoLogado(container: AppContainer, usuario: Usuario) {
    val navController = rememberNavController()
    val authViewModel = criarViewModel(container) { AuthViewModel(container.authRepository) }

    CascaApp(
        navController = navController,
        nomeUsuario = usuario.nome,
        aoSair = authViewModel::sair,
    ) { modifier: Modifier ->
        NavHost(
            navController = navController,
            startDestination = Rotas.AGENDA,
            modifier = modifier,
        ) {
            composable(Rotas.AGENDA) {
                AgendaScreen(
                    viewModel = criarViewModel(container) {
                        AgendaViewModel(
                            container.entrevistaRepository,
                            container.definicoesRepository,
                            container.anexoRepository,
                            container.vagaRepository,
                        )
                    },
                    aoAbrirEntrevista = { id -> navController.abrirEntrevista(id) },
                )
            }

            composable(Rotas.VAGAS) {
                VagaScreen(
                    viewModel = criarViewModel(container) { VagaViewModel(container.vagaRepository) },
                    aoEditarRoteiroDaVaga = { id, titulo ->
                        navController.navigate(Rotas.roteiroDaVaga(id))
                    },
                )
            }

            composable(Rotas.DEFINICOES) {
                DefinicoesScreen(
                    viewModel = criarViewModel(container) {
                        DefinicoesViewModel(
                            container.definicoesRepository,
                            container.entrevistaRepository,
                        )
                    },
                )
            }

            composable(Rotas.ROTEIRO) {
                RoteiroScreen(
                    viewModel = criarViewModel(container) { RoteiroViewModel(container.roteiroRepository) },
                )
            }

            // Roteiro próprio de uma vaga. A chave é a vaga, para que trocar de
            // uma vaga para outra não mostre o rascunho da anterior.
            composable(
                route = Rotas.ROTEIRO_DA_VAGA,
                arguments = listOf(navArgument(Rotas.ARG_VAGA_ID) { type = NavType.LongType }),
            ) { entrada ->
                val vagaId = entrada.arguments?.getLong(Rotas.ARG_VAGA_ID) ?: return@composable
                RoteiroScreen(
                    viewModel = criarViewModel(container, chave = "roteiro-vaga-$vagaId") {
                        RoteiroViewModel(
                            repository = container.roteiroRepository,
                            vagaId = vagaId,
                            vagaRepository = container.vagaRepository,
                        )
                    },
                )
            }

            composable(
                route = Rotas.ENTREVISTA,
                arguments = listOf(navArgument(Rotas.ARG_ENTREVISTA_ID) { type = NavType.LongType }),
            ) { entrada ->
                val id = entrada.arguments?.getLong(Rotas.ARG_ENTREVISTA_ID) ?: return@composable
                EntrevistaScreen(
                    viewModel = criarViewModel(container, chave = "entrevista-$id") {
                        EntrevistaViewModel(
                            container.entrevistaRepository,
                            container.roteiroRepository,
                            container.formularioRepository,
                            container.vagaRepository,
                            container.anexoRepository,
                            id,
                        )
                    },
                    aoVoltar = { navController.popBackStack() },
                    aoAbrirPaginasDoCurriculo = { caminho, nome ->
                        navController.currentBackStackEntry?.savedStateHandle
                            ?.set(CAMINHO_CURRICULO, caminho)
                        navController.currentBackStackEntry?.savedStateHandle
                            ?.set(NOME_CANDIDATO, nome)
                        navController.navigate(Rotas.curriculo(id))
                    },
                )
            }

            // Currículo em tela cheia. O PDF é convertido em imagens pela
            // repository, que guarda cada página no cache.
            composable(
                route = Rotas.CURRICULO,
                arguments = listOf(navArgument(Rotas.ARG_ENTREVISTA_ID) { type = NavType.LongType }),
            ) { entrada ->
                val id = entrada.arguments?.getLong(Rotas.ARG_ENTREVISTA_ID) ?: return@composable
                val caminho = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(CAMINHO_CURRICULO)
                    .orEmpty()
                val nome = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(NOME_CANDIDATO)
                    .orEmpty()

                val viewModel = criarViewModel(container, chave = "curriculo-$id") {
                    CurriculoPaginasViewModel(container.curriculoPaginasRepository)
                }

                LaunchedEffect(caminho, nome) {
                    if (caminho.isNotBlank()) {
                        viewModel.carregar(caminho, nome.ifBlank { "Currículo" })
                    }
                }

                CurriculoPaginasScreen(
                    viewModel = viewModel,
                    aoVoltar = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Fábrica mínima para criar ViewModels com dependências do [AppContainer].
 * Evita trazer Hilt (e uma etapa de geração de código) só para isto.
 */
@Composable
private inline fun <reified T : ViewModel> criarViewModel(
    container: AppContainer,
    chave: String? = null,
    crossinline criar: () -> T,
): T = viewModel(
    key = chave,
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = criar() as VM
    },
)

/** Marcador do estado "ainda não sei se existe sessão" (válido enquanto o uid é vazio). */
private val Carregando = Usuario(uid = "", nome = "", email = "")

/** Chave usada para levar o caminho do currículo até o visualizador. */
private const val CAMINHO_CURRICULO = "caminhoCurriculo"

/** Chave com o nome do candidato, usado no título do visualizador. */
private const val NOME_CANDIDATO = "nomeCandidato"
