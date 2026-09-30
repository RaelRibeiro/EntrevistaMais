package com.example.entrevistador.ui.entrevista

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entrevistador.core.formatarTelefone
import com.example.entrevistador.core.minutosParaHora
import com.example.entrevistador.core.paraCronometro
import com.example.entrevistador.core.paraTexto
import com.example.entrevistador.data.repository.AnexoRepository
import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.Pergunta
import com.example.entrevistador.domain.model.RespostaAutomatica
import com.example.entrevistador.domain.model.StatusEntrevista
import com.example.entrevistador.domain.model.TipoCurriculo
import com.example.entrevistador.domain.model.TipoResposta
import com.example.entrevistador.domain.model.Vaga
import com.example.entrevistador.ui.components.CampoTexto
import com.example.entrevistador.ui.components.CartaoSecao
import com.example.entrevistador.ui.components.TituloSecao

/**
 * Tela 4: a entrevista em andamento.
 *
 * Antes de começar mostra o perfil do candidato; durante, mostra o cronômetro e
 * as abas Currículo e Roteiro. O roteiro é uma sequência de perguntas — as de
 * resposta automática (dia/hora, nome, dados da vaga) já vêm preenchidas, e a
 * experiência profissional vira uma tabela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrevistaScreen(
    viewModel: EntrevistaViewModel,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    aoAbrirPaginasDoCurriculo: (caminho: String, nome: String) -> Unit = { _, _ -> },
    aoAbrirImagem: (caminho: String) -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var confirmarFinalizacao by remember { mutableStateOf(false) }

    val seletorArquivo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::anexarCurriculo) }

    LaunchedEffect(estado.mensagem) {
        estado.mensagem?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumirMensagem()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = estado.entrevista?.nome ?: "Entrevista",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        val entrevista = estado.entrevista

        if (estado.carregando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (entrevista == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Entrevista não encontrada.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PerfilCandidato(estado, entrevista)

            if (estado.emAndamento) {
                CartaoCronometro(
                    decorridoMs = estado.decorridoMs,
                    tempoExcedido = estado.tempoExcedido,
                    duracaoPrevistaMinutos = estado.duracaoPrevistaMinutos,
                )
            }

            // "encerrável" cobre concluída, aprovada, reprovada e encerrada: em
            // todos esses casos a entrevista já aconteceu e não faz sentido
            // oferecer o botão de começar de novo.
            if (!estado.emAndamento && !entrevista.status.encerravel) {
                BotaoIniciar(
                    aoClicar = viewModel::iniciar,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (entrevista.status.encerravel) {
                ResumoFinalizado(
                    entrevista = entrevista,
                    aoAprovar = { viewModel.aprovar() },
                    aoReprovar = { viewModel.reprovar() },
                    aoEncerrar = { viewModel.encerrar() },
                )
            }

            ToggleAba(
                aba = estado.aba,
                aoTrocar = viewModel::trocarAba,
                habilitada = estado.emAndamento || entrevista.status.encerravel,
            )

            when {
                !estado.emAndamento && !entrevista.status.encerravel ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Text(
                            text = "Toque em Iniciar para começar. A entrevista só começa a contar " +
                                "o tempo a partir daí, e o horário real fica registrado.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }

                estado.aba == AbaEntrevista.CURRICULO -> AbaCurriculo(
                    estado = estado,
                    aoAlterar = viewModel::aoAlterarCurriculo,
                    aoEditar = viewModel::editarCurriculo,
                    aoCancelarEdicao = viewModel::cancelarEdicaoCurriculo,
                    aoSalvar = viewModel::salvarCurriculo,
                    aoAnexar = {
                        seletorArquivo.launch(AnexoRepository.TIPOS_ACEITOS.toTypedArray())
                    },
                    aoAbrirArquivo = {
                        // Imagem abre direto; PDF vai para o visualizador que
                        // converte cada página em imagem.
                        val alvo = estado.entrevista
                        if (alvo != null && alvo.temArquivoCurriculo) {
                            if (alvo.tipoCurriculo == TipoCurriculo.PDF) {
                                aoAbrirPaginasDoCurriculo(alvo.caminhoCurriculo, alvo.nome)
                            } else {
                                aoAbrirImagem(alvo.caminhoCurriculo)
                            }
                        }
                    },
                )

                else -> AbaRoteiro(
                    estado = estado,
                    viewModel = viewModel,
                )
            }

            if (estado.emAndamento) {
                OutlinedButton(
                    onClick = { confirmarFinalizacao = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("Finalizar entrevista", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmarFinalizacao) {
        AlertDialog(
            onDismissRequest = { confirmarFinalizacao = false },
            title = { Text("Finalizar entrevista?") },
            text = { Text("O tempo total é registrado e o roteiro preenchido é salvo.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmarFinalizacao = false
                        viewModel.finalizar()
                    },
                ) { Text("Finalizar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarFinalizacao = false }) { Text("Continuar") }
            },
        )
    }
}

/** Cartão do candidato: nome, telefone com máscara e data/hora da entrevista. */
@Composable
private fun PerfilCandidato(estado: EstadoEntrevista, entrevista: Entrevista) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TituloSecao(texto = "Candidato")

            Text(
                text = entrevista.nome,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Uma linha só, sempre mascarado: quebrar o número no meio
                // atrapalha na hora de ligar.
                Text(
                    text = formatarTelefone(entrevista.telefone),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${entrevista.data.paraTexto()} · " +
                        "${entrevista.inicioMinutos.minutosParaHora()} às " +
                        entrevista.fimMinutos.minutosParaHora(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (estado.vaga != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Work,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = estado.vaga.titulo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CartaoCronometro(
    decorridoMs: Long,
    tempoExcedido: Boolean,
    duracaoPrevistaMinutos: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (tempoExcedido) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null)
            Column {
                Text(
                    text = decorridoMs.paraCronometro(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (tempoExcedido) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
                Text(
                    text = if (tempoExcedido) {
                        "Passou do tempo previsto de $duracaoPrevistaMinutos min"
                    } else {
                        "Tempo previsto: $duracaoPrevistaMinutos min"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun BotaoIniciar(aoClicar: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = aoClicar,
        modifier = modifier.padding(top = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Text("INICIAR", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ResumoFinalizado(
    entrevista: Entrevista,
    aoAprovar: () -> Unit,
    aoReprovar: () -> Unit,
    aoEncerrar: () -> Unit,
) {
    val duracao = entrevista.duracaoRealMs
    val status = entrevista.status
    val encerrada = status == StatusEntrevista.ENCERRADA
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = when {
                    encerrada -> "Candidato encerrado"
                    entrevista.status == StatusEntrevista.APROVADO -> "Candidato aprovado"
                    entrevista.status == StatusEntrevista.REPROVADO -> "Candidato reprovado"
                    else -> "Entrevista concluída"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (duracao != null) {
                Text(
                    text = "Duração real: ${duracao.paraCronometro()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // A decisão só faz sentido depois da entrevista: quem já tem
            // decisão pode trocá-la, e quem foi encerrado volta para concluída.
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!status.decidido && !encerrada) {
                    OutlinedButton(
                        onClick = aoReprovar,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Reprovar") }
                    Button(onClick = aoAprovar) { Text("Aprovar") }
                } else {
                    if (!encerrada) {
                        OutlinedButton(onClick = aoEncerrar) { Text("Encerrar") }
                    }
                }
            }

            Text(
                text = when {
                    encerrada -> "O registro, as respostas e o currículo continuam guardados. " +
                        "Use Ver encerrados na agenda para reabrir."
                    status.decidido -> "Você pode trocar a decisão, encerrar ou voltar a editar a qualquer momento."
                    else -> "Você pode aprovar, reprovar ou encerrar este candidato."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToggleAba(
    aba: AbaEntrevista,
    aoTrocar: (AbaEntrevista) -> Unit,
    habilitada: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        AbaEntrevista.entries.forEachIndexed { indice, item ->
            SegmentedButton(
                selected = aba == item,
                onClick = { aoTrocar(item) },
                enabled = habilitada,
                shape = SegmentedButtonDefaults.itemShape(index = indice, count = AbaEntrevista.entries.size),
            ) {
                Text(item.rotulo)
            }
        }
    }
}

@Composable
private fun AbaCurriculo(
    estado: EstadoEntrevista,
    aoAlterar: (String) -> Unit,
    aoEditar: () -> Unit,
    aoCancelarEdicao: () -> Unit,
    aoSalvar: () -> Unit,
    aoAnexar: () -> Unit,
    aoAbrirArquivo: () -> Unit,
) {
    val entrevista = estado.entrevista ?: return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TituloSecao(
                texto = "Currículo",
                modifier = Modifier.weight(1f),
            )
            if (!estado.curriculoEditando) {
                TextButton(onClick = aoEditar) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text("Editar", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        if (entrevista.temArquivoCurriculo) {
            Card(
                onClick = aoAbrirArquivo,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entrevista.nomeArquivoCurriculo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = if (entrevista.tipoCurriculo == TipoCurriculo.PDF) {
                                "PDF anexado · toque para abrir"
                            } else {
                                "Imagem anexada"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Abrir currículo",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        } else if (!estado.curriculoEditando) {
            OutlinedButton(onClick = aoAnexar, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AttachFile, contentDescription = null)
                Text("Anexar PDF ou imagem", modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (estado.curriculoEditando) {
            CampoTexto(
                valor = estado.curriculo,
                aoAlterar = aoAlterar,
                rotulo = "Resumo do currículo",
                dica = "Se o candidato enviou arquivo, escreva aqui só o essencial.",
                umaLinha = false,
                minimoLinhas = 8,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = aoSalvar) { Text("Salvar") }
                TextButton(onClick = aoCancelarEdicao) { Text("Cancelar") }
            }
        } else if (estado.curriculo.isBlank() && !entrevista.temArquivoCurriculo) {
            Text(
                text = "Nenhum currículo lançado para este candidato. Anexe o PDF/imagem ou " +
                    "toque em Editar para escrever o resumo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(estado.curriculo, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AbaRoteiro(estado: EstadoEntrevista, viewModel: EntrevistaViewModel) {
    val roteiro = estado.roteiro

    if (roteiro == null) {
        Text(
            text = "Nenhum roteiro cadastrado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // Depois de concluída, aprovada, reprovada ou encerrada, a entrevista vira
    // registro: as respostas continuam visíveis, mas não podem mais mudar.
    val somenteLeitura = estado.entrevista?.status != StatusEntrevista.AGENDADA &&
        estado.entrevista?.status != StatusEntrevista.EM_ANDAMENTO

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TituloSecao(texto = roteiro.titulo)

        if (somenteLeitura) {
            CartaoSecao(titulo = "Respostas registradas") {
                Text(
                    text = "Esta entrevista foi finalizada. As respostas abaixo são o " +
                        "registro final e não podem mais ser editadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        BlocoVaga(estado.vaga)

        for (pergunta in roteiro.perguntas) {
            when (pergunta.tipo) {
                TipoResposta.SECAO -> TituloSecao(
                    texto = pergunta.titulo,
                    modifier = Modifier.padding(top = 8.dp),
                )

                TipoResposta.TABELA_EXPERIENCIAS -> TabelaExperiencias(
                    estado = estado,
                    aoAlterar = viewModel::aoAlterarExperiencia,
                    aoAdicionar = viewModel::adicionarLinhaExperiencia,
                    aoRemover = viewModel::removerLinhaExperiencia,
                    somenteLeitura = somenteLeitura,
                )

                else -> CampoPergunta(
                    pergunta = pergunta,
                    estado = estado,
                    aoAlterar = { viewModel.aoAlterarResposta(pergunta, it) },
                    somenteLeitura = somenteLeitura,
                )
            }
        }
    }
}

/** Resumo da vaga no topo do roteiro, preenchido a partir do cadastro de vagas. */
@Composable
private fun BlocoVaga(vaga: Vaga?) {
    if (vaga == null) {
        CartaoSecao(titulo = "Informações da vaga") {
            Text(
                text = "Nenhuma vaga vinculada a esta entrevista. Cadastre a aba Vagas para " +
                    "estas informações aparecerem aqui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    CartaoSecao(titulo = "Informações da vaga", icone = Icons.Filled.Work) {
        Text(vaga.titulo, style = MaterialTheme.typography.titleMedium)
        ItemDaVaga("Horário de trabalho", vaga.horarioTrabalho)
        ItemDaVaga("Salário e benefícios", vaga.salarioBeneficios)
        ItemDaVaga("Tempo de experiência", vaga.tempoExperiencia)
        ItemDaVaga("Escolaridade", vaga.escolaridade)
        ItemDaVaga("Precisa de habilitação", vaga.exigeHabilitacao)
        ItemDaVaga("Resumo das atividades principais", vaga.resumoAtividades)
    }
}

@Composable
private fun ItemDaVaga(rotulo: String, valor: String) {
    if (valor.isBlank()) return
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = valor, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Um campo por pergunta. Pergunta com [RespostaAutomatica] mostra o valor que o
 * app preencheu e permite sobrescrever.
 */
@Composable
private fun CampoPergunta(
    pergunta: Pergunta,
    estado: EstadoEntrevista,
    aoAlterar: (String) -> Unit,
    somenteLeitura: Boolean = false,
) {
    val valor = estado.respostasPorPergunta[pergunta.id].orEmpty()
    val automatico = pergunta.respostaAutomatica?.takeIf { it != RespostaAutomatica.NENHUMA }
    val preenchidoPeloApp = valor.isBlank() && automatico != null &&
        remember(pergunta.id, estado.entrevista?.id, estado.vaga?.id) {
            estado.respostasPorPergunta[pergunta.id].orEmpty()
        }.isBlank() &&
        automaticoValor(estado, automatico).isNotBlank()

    if (pergunta.tipo == TipoResposta.SIM_NAO) {
        OpcoesSimNao(
            rotulo = pergunta.titulo,
            dica = when {
                preenchidoPeloApp -> "Preenchido pelo app — pode editar se precisar."
                pergunta.dica.isNotBlank() -> pergunta.dica
                else -> null
            },
            selecionado = valor.ifBlank {
                if (preenchidoPeloApp) automaticoValor(estado, automatico!!) else ""
            },
            aoSelecionar = aoAlterar,
            habilitado = !somenteLeitura &&
                (automatico == null || preenchidoPeloApp.not() || valor.isNotBlank()),
        )
        return
    }

    CampoTexto(
        valor = valor.ifBlank { if (preenchidoPeloApp) automaticoValor(estado, automatico!!) else "" },
        aoAlterar = aoAlterar,
        rotulo = pergunta.titulo,
        dica = when {
            preenchidoPeloApp -> "Preenchido pelo app — pode editar se precisar."
            pergunta.dica.isNotBlank() -> pergunta.dica
            else -> null
        },
        numerico = pergunta.tipo == TipoResposta.NUMERO,
        umaLinha = pergunta.tipo != TipoResposta.TEXTO_LONGO,
        minimoLinhas = if (pergunta.tipo == TipoResposta.TEXTO_LONGO) 4 else 1,
        somenteLeitura = somenteLeitura,
    )
}

private fun automaticoValor(estado: EstadoEntrevista, tipo: RespostaAutomatica): String =
    when (tipo) {
        RespostaAutomatica.DIA_E_HORA -> estado.diaEHora
        RespostaAutomatica.NOME_CANDIDATO -> estado.entrevista?.nome.orEmpty()
        RespostaAutomatica.TELEFONE_CANDIDATO ->
            formatarTelefone(estado.entrevista?.telefone.orEmpty())

        RespostaAutomatica.DADOS_DA_VAGA -> estado.vaga?.let { vaga ->
            listOfNotNull(
                vaga.horarioTrabalho.takeIf { it.isNotBlank() },
                vaga.salarioBeneficios.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }.orEmpty()

        else -> ""
    }

@Composable
private fun OpcoesSimNao(
    rotulo: String,
    dica: String?,
    selecionado: String,
    aoSelecionar: (String) -> Unit,
    habilitado: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = rotulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        dica?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Sim", "Não", "Talvez").forEach { opcao ->
                FilterChip(
                    selected = selecionado.equals(opcao, ignoreCase = true),
                    onClick = { aoSelecionar(opcao) },
                    label = { Text(opcao) },
                    enabled = habilitado,
                )
            }
        }
    }
}

/**
 * Mini tabela "locais onde trabalhou": Local, Ano, Duração, Cargo e Motivo da
 * saída. Uma linha por emprego, com o botão de adicionar no fim.
 */
@Composable
private fun TabelaExperiencias(
    estado: EstadoEntrevista,
    aoAlterar: (Int, LinhaExperiencia) -> Unit,
    aoAdicionar: () -> Unit,
    aoRemover: (Int) -> Unit,
    somenteLeitura: Boolean = false,
) {
    CartaoSecao(titulo = "Locais onde trabalhou") {
        CabecalhoTabela()
        estado.experiencias.forEachIndexed { indice, linha ->
            LinhaTabela(
                indice = indice,
                linha = linha,
                podeRemover = !somenteLeitura && estado.experiencias.size > 1,
                aoAlterar = aoAlterar,
                aoRemover = aoRemover,
                somenteLeitura = somenteLeitura,
            )
        }
        if (!somenteLeitura) {
            OutlinedButton(onClick = aoAdicionar, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Adicionar local", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun CabecalhoTabela() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Cabecalho("Local", 2f)
        Cabecalho("Ano", 0.8f)
        Cabecalho("Duração", 1f)
        Cabecalho("Cargo", 1.3f)
        Cabecalho("Motivo da saída", 1.6f)
    }
}

@Composable
private fun RowScope.Cabecalho(texto: String, peso: Float) {
    Text(
        text = texto,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(peso),
    )
}

@Composable
private fun LinhaTabela(
    indice: Int,
    linha: LinhaExperiencia,
    podeRemover: Boolean,
    aoAlterar: (Int, LinhaExperiencia) -> Unit,
    aoRemover: (Int) -> Unit,
    somenteLeitura: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Celula(linha.local, 2f, somenteLeitura) { aoAlterar(indice, linha.copy(local = it)) }
        Celula(linha.ano, 0.8f, somenteLeitura) { aoAlterar(indice, linha.copy(ano = it)) }
        Celula(linha.duracao, 1f, somenteLeitura) { aoAlterar(indice, linha.copy(duracao = it)) }
        Celula(linha.cargo, 1.3f, somenteLeitura) { aoAlterar(indice, linha.copy(cargo = it)) }
        Celula(linha.motivoSaida, 1.6f, somenteLeitura) { aoAlterar(indice, linha.copy(motivoSaida = it)) }
        if (podeRemover) {
            IconButton(onClick = { aoRemover(indice) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remover linha",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.Celula(
    valor: String,
    peso: Float,
    somenteLeitura: Boolean = false,
    aoAlterar: (String) -> Unit,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoAlterar,
        readOnly = somenteLeitura,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(peso),
    )
}
