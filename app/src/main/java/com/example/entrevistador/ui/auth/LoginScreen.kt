package com.example.entrevistador.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entrevistador.data.firebase.FirebaseConfig
import com.example.entrevistador.ui.components.CampoTexto
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/** Telas 1 e 2: entrar na conta e criar conta nova. */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    aoIrParaCriarConta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(estado.erroGeral) {
        estado.erroGeral?.let {
            snackbarHost.showSnackbar(it)
            viewModel.limparErroGeral()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Cabecalho()

                CampoTexto(
                    valor = estado.email,
                    aoAlterar = viewModel::aoAlterarEmail,
                    rotulo = "E-mail",
                    erro = estado.erroEmail,
                    modifier = Modifier.padding(top = 28.dp),
                )

                CampoTexto(
                    valor = estado.senha,
                    aoAlterar = viewModel::aoAlterarSenha,
                    rotulo = "Senha",
                    erro = estado.erroSenha,
                    dica = estado.dicaSenha,
                    senha = true,
                    acaoDoTeclado = ImeAction.Done,
                    modifier = Modifier.padding(top = 12.dp),
                )

                BotaoEntrar(
                    carregando = estado.carregando,
                    habilitado = estado.podeEnviar,
                    aoClicar = viewModel::entrar,
                    modifier = Modifier.padding(top = 20.dp),
                )

                BotaoGoogle(
                    carregando = estado.carregando,
                    aoClicar = viewModel::entrarComGoogle,
                    modifier = Modifier.padding(top = 12.dp),
                )

                TextButton(
                    onClick = viewModel::esquecerSenha,
                    enabled = !estado.carregando,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Esqueci minha senha")
                }

                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ainda não tem conta?", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = aoIrParaCriarConta, enabled = !estado.carregando) {
                        Text("Criar agora")
                    }
                }
            }
        }
    }
}

@Composable
fun CriarContaScreen(
    viewModel: AuthViewModel,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(estado.erroGeral) {
        estado.erroGeral?.let {
            snackbarHost.showSnackbar(it)
            viewModel.limparErroGeral()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = aoVoltar, enabled = !estado.carregando) {
                Text("Voltar")
            }

            Text(
                text = "Criar conta",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Seus dados ficam guardados neste aparelho. Você entra e sai quantas vezes quiser.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CampoTexto(
                valor = estado.nome,
                aoAlterar = viewModel::aoAlterarNome,
                rotulo = "Nome do recrutador",
                erro = estado.erroNome,
                placeholder = "Como quer ser chamado",
                modifier = Modifier.padding(top = 12.dp),
            )

            CampoTexto(
                valor = estado.email,
                aoAlterar = viewModel::aoAlterarEmail,
                rotulo = "E-mail",
                erro = estado.erroEmail,
            )

            CampoTexto(
                valor = estado.senha,
                aoAlterar = viewModel::aoAlterarSenha,
                rotulo = "Senha",
                erro = estado.erroSenha,
                dica = estado.dicaSenha,
                senha = true,
            )

            CampoTexto(
                valor = estado.confirmarSenha,
                aoAlterar = viewModel::aoAlterarConfirmarSenha,
                rotulo = "Confirmar senha",
                erro = estado.erroConfirmarSenha,
                senha = true,
                acaoDoTeclado = ImeAction.Done,
            )

            Button(
                onClick = viewModel::criarConta,
                enabled = !estado.carregando,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                if (estado.carregando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Criar conta")
                }
            }
        }
    }
}

@Composable
private fun Cabecalho() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Entrevistador",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Organize suas entrevistas e nunca mais perca o horário de um candidato.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun BotaoEntrar(
    carregando: Boolean,
    habilitado: Boolean,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = aoClicar,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (carregando) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Entrar")
        }
    }
}

/**
 * Entra com o Google (mesmo método do site). Fica oculto enquanto o web client
 * ID não estiver configurado no [FirebaseConfig] — e-mail/senha já funciona.
 */
@Composable
private fun BotaoGoogle(
    carregando: Boolean,
    aoClicar: (idToken: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idCliente = FirebaseConfig.GOOGLE_SERVER_CLIENT_ID
    if (idCliente.isBlank()) return

    val contexto = LocalContext.current
    val clienteGoogle = remember(idCliente) {
        GoogleSignIn.getClient(
            contexto,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(idCliente)
                .requestEmail()
                .build()
        )
    }
    val lancaGoogle = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val conta = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(resultado.data)
                .getResult(ApiException::class.java)
        }.getOrNull()
        conta?.idToken?.let(aoClicar)
    }

    OutlinedButton(
        onClick = { lancaGoogle.launch(clienteGoogle.signInIntent) },
        enabled = !carregando,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("Entrar com Google")
    }
}
