package com.example.entrevistador.ui.roteiro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entrevistador.domain.model.Pergunta
import com.example.entrevistador.domain.model.RespostaAutomatica
import com.example.entrevistador.domain.model.TipoResposta
import com.example.entrevistador.ui.components.CampoTexto
import com.example.entrevistador.ui.components.CartaoSecao

/**
 * Edição do roteiro usado na aba "Roteiro" durante a entrevista.
 *
 * O roteiro é uma lista de perguntas ordenadas. As perguntas marcadas como
 * automáticas já vêm preenchidas na tela da entrevista — o app sabe o dia/hora,
 * o nome do candidato e os dados da vaga.
 */
@Composable
fun RoteiroScreen(
    viewModel: RoteiroViewModel,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var confirmarRestaurar by remember { mutableStateOf(false) }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumirMensagem()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (estado.carregando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (estado.ehDaVaga) "Roteiro da vaga" else "Roteiro de entrevista",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            if (estado.ehDaVaga) {
                CartaoSecao(titulo = estado.tituloVaga) {
                    Text(
                        text = if (estado.usaRoteiroPadrao) {
                            "Esta vaga está usando o roteiro padrão do processo. Assim que você " +
                                "editar, a cópia passa a ser só dela — as outras vagas seguem " +
                                "acompanhando o roteiro padrão."
                        } else {
                            "Roteiro próprio desta vaga. As entrevistas dela usam estas " +
                                "perguntas, e o roteiro padrão do processo não é alterado."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "${estado.perguntas.size} perguntas, na ordem em que serão feitas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (estado.editando) {
                CartaoSecao(titulo = "Editando roteiro") {
                    CampoTexto(
                        valor = estado.rascunhoTitulo,
                        aoAlterar = viewModel::aoAlterarTitulo,
                        rotulo = "Título",
                    )

                    estado.perguntas.forEachIndexed { indice, pergunta ->
                        CartaoPergunta(
                            indice = indice,
                            total = estado.perguntas.size,
                            pergunta = pergunta,
                            aoAlterar = { viewModel.aoAlterarPergunta(indice, it) },
                            aoSubir = { viewModel.moverPergunta(indice, -1) },
                            aoDescer = { viewModel.moverPergunta(indice, 1) },
                            aoRemover = { viewModel.removerPergunta(indice) },
                        )
                    }

                    Button(onClick = viewModel::adicionarPergunta, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Adicionar pergunta", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::salvar) { Text("Salvar roteiro") }
                        TextButton(onClick = viewModel::cancelarEdicao) { Text("Cancelar") }
                    }
                }
            } else {
                CartaoSecao(titulo = estado.roteiro?.titulo ?: "Roteiro") {
                    if (estado.perguntas.isEmpty()) {
                        Text(
                            text = "Nenhuma pergunta cadastrada.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    estado.perguntas.forEachIndexed { indice, pergunta ->
                        LinhaPerguntaLeitura(indice = indice, pergunta = pergunta)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::editar) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text("Editar roteiro", modifier = Modifier.padding(start = 8.dp))
                        }
                        if (!estado.ehDaVaga || !estado.usaRoteiroPadrao) {
                            TextButton(onClick = { confirmarRestaurar = true }) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                                Text(
                                    text = if (estado.ehDaVaga) {
                                        "Voltar ao padrão"
                                    } else {
                                        "Restaurar"
                                    },
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmarRestaurar) {
        AlertDialog(
            onDismissRequest = { confirmarRestaurar = false },
            title = {
                Text(if (estado.ehDaVaga) "Voltar ao roteiro padrão?" else "Restaurar perguntas?")
            },
            text = {
                Text(
                    if (estado.ehDaVaga) {
                        "O roteiro próprio desta vaga é apagado e ela volta a seguir o roteiro " +
                            "padrão do processo. As perguntas respondidas nas entrevistas " +
                            "anteriores deixam de aparecer, porque estavam ligadas às perguntas " +
                            "que serão removidas."
                    } else {
                        "As perguntas voltam ao conteúdo original do processo seletivo."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarRestaurar = false
                    viewModel.restaurarPadrao()
                }) { Text(if (estado.ehDaVaga) "Voltar ao padrão" else "Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarRestaurar = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun LinhaPerguntaLeitura(indice: Int, pergunta: Pergunta) {
    if (pergunta.tipo == TipoResposta.SECAO) {
        TituloSecaoRoteiro(texto = pergunta.titulo)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${indice + 1}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(pergunta.titulo, style = MaterialTheme.typography.bodyLarge)
            val detalhe = detalheDaPergunta(pergunta)
            if (detalhe.isNotBlank()) {
                Text(
                    text = detalhe,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CartaoPergunta(
    indice: Int,
    total: Int,
    pergunta: Pergunta,
    aoAlterar: (Pergunta) -> Unit,
    aoSubir: () -> Unit,
    aoDescer: () -> Unit,
    aoRemover: () -> Unit,
) {
    var menuTipo by remember { mutableStateOf(false) }
    var menuAutomatica by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${indice + 1} de $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = aoSubir, enabled = indice > 0) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir pergunta")
            }
            IconButton(onClick = aoDescer, enabled = indice < total - 1) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Descer pergunta")
            }
            IconButton(onClick = aoRemover) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover pergunta")
            }
        }

        CampoTexto(
            valor = pergunta.titulo,
            aoAlterar = { aoAlterar(pergunta.copy(titulo = it)) },
            rotulo = "Pergunta",
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Tipo",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
            Box {
                TextButton(onClick = { menuTipo = true }) { Text(pergunta.tipo.rotulo) }
                DropdownMenu(expanded = menuTipo, onDismissRequest = { menuTipo = false }) {
                    TipoResposta.entries.forEach { tipo ->
                        DropdownMenuItem(
                            text = { Text(tipo.rotulo) },
                            onClick = { menuTipo = false; aoAlterar(pergunta.copy(tipo = tipo)) },
                        )
                    }
                }
            }
            Box {
                TextButton(onClick = { menuAutomatica = true }) {
                    Text(pergunta.respostaAutomatica?.rotulo ?: "Automático")
                }
                DropdownMenu(
                    expanded = menuAutomatica,
                    onDismissRequest = { menuAutomatica = false },
                ) {
                    RespostaAutomatica.entries.forEach { opcao ->
                        DropdownMenuItem(
                            text = { Text(opcao.rotulo) },
                            onClick = {
                                menuAutomatica = false
                                aoAlterar(pergunta.copy(respostaAutomatica = opcao))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun detalheDaPergunta(pergunta: Pergunta): String = listOfNotNull(
    pergunta.tipo.rotulo.takeIf { it != "Texto" },
    pergunta.respostaAutomatica?.rotulo,
).joinToString(" · ")

@Composable
private fun TituloSecaoRoteiro(texto: String) {
    Text(
        text = texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}
