package com.example.entrevistador.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entrevistador.core.formatarTelefone
import com.example.entrevistador.core.minutosParaHora
import com.example.entrevistador.core.paraTituloAgenda
import com.example.entrevistador.data.repository.AnexoRepository
import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.StatusEntrevista
import com.example.entrevistador.domain.model.TipoCurriculo
import com.example.entrevistador.domain.model.Vaga
import com.example.entrevistador.ui.components.CampoTexto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tela 3: o dia de entrevistas.
 *
 * O recrutador escolhe a data e vai adicionando candidatos; cada entrada já
 * nasce com um horário calculado pelas Definições padrão.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel,
    aoAbrirEntrevista: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val horario by viewModel.horario.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var mostrarSeletorDeData by remember { mutableStateOf(false) }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumirMensagem()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (!estado.diaLotado) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::abrirFormulario,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Candidato") },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SeletorDeDia(
                titulo = estado.data.paraTituloAgenda(),
                aoAnterior = { viewModel.irParaDia(-1) },
                aoProximo = { viewModel.irParaDia(1) },
                aoHoje = { viewModel.irParaData(java.time.LocalDate.now()) },
                vagasUsadas = estado.vagasUsadas,
                vagasTotais = estado.vagasTotais,
                permitirEscolherData = { mostrarSeletorDeData = true },
            )

            // Ações do dia: encerrar todo mundo já avaliado e consultar os que
            // já foram encerrados.
            if (estado.quantidadeEncerravel > 0 || estado.encerradas.isNotEmpty()) {
                LinhaAcoesDoDia(
                    quantidadeEncerravel = estado.quantidadeEncerravel,
                    quantidadeEncerradas = estado.encerradas.size,
                    mostrarEncerradas = estado.mostrarEncerradas,
                    aoEncerrarAvaliados = viewModel::encerrarAvaliadosDoDia,
                    aoAlternarEncerradas = viewModel::alternarEncerradas,
                )
            }

            val listaVisivel = if (estado.mostrarEncerradas) {
                estado.entrevistas + estado.encerradas
            } else {
                estado.entrevistas
            }

            when {
                estado.carregando -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                listaVisivel.isEmpty() -> AgendaVazia(
                    diaLotado = estado.diaLotado,
                    aoAdicionar = viewModel::abrirFormulario,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(listaVisivel, key = { it.id }) { entrevista ->
                        CartaoEntrevista(
                            entrevista = entrevista,
                            aoIniciar = { aoAbrirEntrevista(entrevista.id) },
                            aoAbrir = { aoAbrirEntrevista(entrevista.id) },
                            aoSubir = { viewModel.moverParaCima(entrevista.id) },
                            aoDescer = { viewModel.moverParaBaixo(entrevista.id) },
                            aoRemover = { viewModel.remover(entrevista.id) },
                            aoCancelar = { viewModel.cancelar(entrevista.id) },
                            aoNaoCompareceu = { viewModel.marcarNaoCompareceu(entrevista.id) },
                            aoReabrir = {
                                if (entrevista.status.decidido || entrevista.status == StatusEntrevista.ENCERRADA) {
                                    viewModel.voltarParaConcluida(entrevista.id)
                                } else {
                                    viewModel.reabrir(entrevista.id)
                                }
                            },
                            aoAprovar = { viewModel.aprovar(entrevista.id) },
                            aoReprovar = { viewModel.reprovar(entrevista.id) },
                            aoEncerrar = { viewModel.encerrar(entrevista.id) },
                            aoAlterarHorario = { viewModel.abrirHorario(entrevista.id) },
                            aoVoltarHorarioPadrao = { viewModel.voltarAoHorarioCalculado(entrevista.id) },
                        )
                    }
                }
            }
        }
    }

    if (mostrarSeletorDeData) {
        DialogoCalendario(
            dataAtual = estado.data,
            aoEscolher = { escolhida ->
                viewModel.irParaData(escolhida)
                mostrarSeletorDeData = false
            },
            aoFechar = { mostrarSeletorDeData = false },
        )
    }

    if (estado.mostrarFormulario) {
        DialogoAdicionarCandidato(
            estado = estado,
            aoAlterarNome = viewModel::aoAlterarNome,
            aoAlterarTelefone = viewModel::aoAlterarTelefone,
            aoAlterarModoCurriculo = viewModel::aoAlterarModoCurriculo,
            aoAlterarResumoCurriculo = viewModel::aoAlterarResumoCurriculo,
            aoSelecionarArquivo = viewModel::aoSelecionarArquivo,
            aoRemoverArquivo = viewModel::aoRemoverArquivo,
            aoSelecionarVaga = viewModel::aoSelecionarVaga,
            aoAlterarHora = viewModel::aoAlterarHoraDoCandidato,
            aoAlterarMinuto = viewModel::aoAlterarMinutoDoCandidato,
            aoVoltarAoHorarioCalculado = viewModel::aoVoltarAoHorarioCalculado,
            aoSalvar = viewModel::adicionarCandidato,
            aoFechar = viewModel::fecharFormulario,
        )
    }

    if (horario.aberto) {
        DialogoHorario(
            hora = horario.hora,
            minuto = horario.minuto,
            aoAlterarHora = viewModel::aoAlterarHora,
            aoAlterarMinuto = viewModel::aoAlterarMinuto,
            aoSalvar = viewModel::salvarHorario,
            aoFechar = viewModel::fecharHorario,
        )
    }
}

/**
 * Horário individual de um candidato.
 *
 * Não mostra hora de término: o candidato ocupa uma faixa que a agenda
 * calcula, e o recrutador só precisa do começo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHorario(
    hora: Int,
    minuto: Int,
    aoAlterarHora: (Int) -> Unit,
    aoAlterarMinuto: (Int) -> Unit,
    aoSalvar: () -> Unit,
    aoFechar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Alterar horário") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "O candidato passa a ter horário fixo, mesmo que a ordem da " +
                        "agenda mude.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeletorNumero(
                        valor = hora,
                        intervalo = (0..23).toList(),
                        aoAlterar = aoAlterarHora,
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    SeletorNumero(
                        valor = minuto,
                        intervalo = listOf(0, 15, 30, 45),
                        aoAlterar = aoAlterarMinuto,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = aoSalvar) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** Lista de números que sobe e desce, para o seletor de hora/minuto. */
@Composable
private fun SeletorNumero(
    valor: Int,
    intervalo: List<Int>,
    aoAlterar: (Int) -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { aberto = true }) {
            Text("%02d".format(valor))
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            intervalo.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text("%02d".format(opcao)) },
                    onClick = {
                        aberto = false
                        aoAlterar(opcao)
                    },
                )
            }
        }
    }
}

/** Calendário aberto ao tocar no nome do dia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoCalendario(
    dataAtual: LocalDate,
    aoEscolher: (LocalDate) -> Unit,
    aoFechar: () -> Unit,
) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = dataAtual
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = aoFechar,
        confirmButton = {
            TextButton(
                onClick = {
                    estado.selectedDateMillis?.let { millis ->
                        aoEscolher(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        )
                    }
                },
                enabled = estado.selectedDateMillis != null,
            ) { Text("Escolher") }
        },
        dismissButton = {
            TextButton(onClick = aoFechar) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = estado, showModeToggle = false)
    }
}

@Composable
private fun SeletorDeDia(
    titulo: String,
    vagasUsadas: Int,
    vagasTotais: Int,
    aoAnterior: () -> Unit,
    aoProximo: () -> Unit,
    aoHoje: () -> Unit,
    permitirEscolherData: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = aoAnterior) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Dia anterior")
            }

            TextButton(
                onClick = permitirEscolherData,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Abrir calendário",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = titulo,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable(onClick = permitirEscolherData),
                    )
                }
            }

            IconButton(onClick = aoProximo) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo dia")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = {
                    if (vagasTotais == 0) 0f else (vagasUsadas.toFloat() / vagasTotais).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp),
            )
            Text(
                text = "$vagasUsadas de $vagasTotais",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = aoHoje) { Text("Hoje") }
        }
    }
}

@Composable
private fun AgendaVazia(diaLotado: Boolean, aoAdicionar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (diaLotado) Icons.Filled.EventBusy else Icons.Filled.EventNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = if (diaLotado) "Dia cheio" else "Nenhuma entrevista neste dia",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = if (diaLotado) {
                "Você atingiu o limite de entrevistas por dia definido nas Definições."
            } else {
                "Toque no botão abaixo para adicionar o primeiro candidato. O horário é calculado sozinho."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!diaLotado) {
            Button(
                onClick = aoAdicionar,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Adicionar candidato", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartaoEntrevista(
    entrevista: Entrevista,
    aoIniciar: () -> Unit,
    aoAbrir: () -> Unit,
    aoSubir: () -> Unit,
    aoDescer: () -> Unit,
    aoRemover: () -> Unit,
    aoCancelar: () -> Unit,
    aoNaoCompareceu: () -> Unit,
    aoReabrir: () -> Unit,
    aoAprovar: () -> Unit,
    aoReprovar: () -> Unit,
    aoEncerrar: () -> Unit,
    aoAlterarHorario: () -> Unit,
    aoVoltarHorarioPadrao: () -> Unit,
) {
    var menuAberto by remember { mutableStateOf(false) }
    var confirmarRemocao by remember { mutableStateOf(false) }

    val emAndamento = entrevista.status == StatusEntrevista.EM_ANDAMENTO
    val concluida = entrevista.status == StatusEntrevista.CONCLUIDA
    val decidida = entrevista.status.decidido
    val encerrada = entrevista.status == StatusEntrevista.ENCERRADA

    Card(
        onClick = aoAbrir,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 56.dp)
                    .background(
                        color = when {
                            emAndamento -> MaterialTheme.colorScheme.primaryContainer
                            concluida -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entrevista.inicioMinutos.minutosParaHora(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        emAndamento -> MaterialTheme.colorScheme.onPrimaryContainer
                        concluida -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entrevista.nome,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                // Só o nome e o status. Telefone e horário de término poluíam a
                // lista, e nada ali é usado para decidir quem chamar agora.
                // O horário alterado fica marcado, senão o recrutador não
                // entende por que o candidato saiu da sequência das Definições.
                if (entrevista.inicioManual) {
                    Text(
                        text = "Horário alterado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                SeloStatus(entrevista.status, modifier = Modifier.padding(top = 6.dp))
            }

            when {
                emAndamento -> Button(onClick = aoAbrir) { Text("Abrir") }

                // Concluída ou já decidida: o botão principal vira a decisão,
                // que é o próximo passo do recrutamento. Quando já existe
                // decisão, mostramos a alternativa para corrigir sem refazer.
                concluida -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = aoReprovar) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Reprovar para a próxima fase",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(onClick = aoAprovar) { Text("Aprovar") }
                }

                decidida -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = aoReabrir) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Voltar para concluída")
                    }
                    if (entrevista.status == StatusEntrevista.APROVADO) {
                        OutlinedButton(
                            onClick = aoReprovar,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text("Reprovar") }
                    } else {
                        Button(
                            onClick = aoAprovar,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text("Aprovar") }
                    }
                }

                encerrada -> IconButton(onClick = aoAbrir) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Reabrir candidato encerrado")
                }

                // Cancelada e "não compareceu" não voltam a contar tempo sozinhas:
                // o caminho é reabrir, que devolve a entrevista para AGENDADA.
                entrevista.status == StatusEntrevista.CANCELADA ||
                    entrevista.status == StatusEntrevista.NAO_COMPARECEU ->
                    OutlinedButton(
                        onClick = aoReabrir,
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null)
                        Text("Reabrir", modifier = Modifier.padding(start = 4.dp))
                    }

                else -> Button(
                    onClick = aoIniciar,
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Iniciar", modifier = Modifier.padding(start = 4.dp))
                }
            }

            Box {
                IconButton(onClick = { menuAberto = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Mais ações")
                }
                DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                    DropdownMenuItem(
                        text = { Text("Subir na lista") },
                        leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) },
                        enabled = !emAndamento,
                        onClick = { menuAberto = false; aoSubir() },
                    )
                    DropdownMenuItem(
                        text = { Text("Descer na lista") },
                        leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) },
                        enabled = !emAndamento,
                        onClick = { menuAberto = false; aoDescer() },
                    )
                    DropdownMenuItem(
                        text = { Text("Aprovar para a próxima fase") },
                        leadingIcon = { Icon(Icons.Filled.Check, null) },
                        enabled = (concluida || decidida) && entrevista.status != StatusEntrevista.APROVADO,
                        onClick = { menuAberto = false; aoAprovar() },
                    )
                    DropdownMenuItem(
                        text = { Text("Reprovar") },
                        leadingIcon = { Icon(Icons.Filled.Close, null) },
                        enabled = (concluida || decidida) && entrevista.status != StatusEntrevista.REPROVADO,
                        onClick = { menuAberto = false; aoReprovar() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (entrevista.inicioManual) "Editar horário alterado" else "Alterar horário")
                        },
                        leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                        enabled = !emAndamento,
                        onClick = { menuAberto = false; aoAlterarHorario() },
                    )
                    DropdownMenuItem(
                        text = { Text("Voltar ao horário das Definições") },
                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        enabled = !emAndamento && entrevista.inicioManual,
                        onClick = { menuAberto = false; aoVoltarHorarioPadrao() },
                    )
                    DropdownMenuItem(
                        text = { Text("Encerrar candidato") },
                        leadingIcon = { Icon(Icons.Filled.DoneAll, null) },
                        enabled = !emAndamento && entrevista.status.encerravel,
                        onClick = { menuAberto = false; aoEncerrar() },
                    )
                    DropdownMenuItem(
                        text = { Text("Não compareceu") },
                        enabled = !emAndamento && !entrevista.status.encerravel,
                        onClick = { menuAberto = false; aoNaoCompareceu() },
                    )
                    DropdownMenuItem(
                        text = { Text("Cancelar entrevista") },
                        enabled = !emAndamento && !entrevista.status.encerravel,
                        onClick = { menuAberto = false; aoCancelar() },
                    )
                    DropdownMenuItem(
                        text = { Text("Reabrir") },
                        enabled = !emAndamento && (concluida || decidida || encerrada ||
                            entrevista.status != StatusEntrevista.AGENDADA),
                        onClick = { menuAberto = false; aoReabrir() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remover candidato") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        enabled = !emAndamento,
                        onClick = { menuAberto = false; confirmarRemocao = true },
                    )
                }
            }
        }
    }

    if (confirmarRemocao) {
        AlertDialog(
            onDismissRequest = { confirmarRemocao = false },
            title = { Text("Remover candidato?") },
            text = { Text("${entrevista.nome} sai da agenda e os horários são reajustados.") },
            confirmButton = {
                TextButton(onClick = { confirmarRemocao = false; aoRemover() }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarRemocao = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun LinhaAcoesDoDia(
    quantidadeEncerravel: Int,
    quantidadeEncerradas: Int,
    mostrarEncerradas: Boolean,
    aoEncerrarAvaliados: () -> Unit,
    aoAlternarEncerradas: () -> Unit,
) {
    var confirmarEncerramento by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (quantidadeEncerravel > 0) {
            OutlinedButton(onClick = { confirmarEncerramento = true }) {
                Icon(Icons.Filled.DoneAll, contentDescription = null)
                Text(
                    text = if (quantidadeEncerravel == 1) {
                        "Encerrar 1 avaliado"
                    } else {
                        "Encerrar $quantidadeEncerravel avaliados"
                    },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (quantidadeEncerradas > 0) {
            TextButton(onClick = aoAlternarEncerradas) {
                Text(
                    if (mostrarEncerradas) {
                        "Ocultar encerrados"
                    } else {
                        "Ver $quantidadeEncerradas encerrados"
                    }
                )
            }
        }
    }

    if (confirmarEncerramento) {
        AlertDialog(
            onDismissRequest = { confirmarEncerramento = false },
            title = { Text("Encerrar avaliados?") },
            text = {
                Text(
                    if (quantidadeEncerravel == 1) {
                        "O candidato sai da lista do dia, mas o registro, as respostas " +
                            "e o currículo continuam guardados."
                    } else {
                        "Os $quantidadeEncerravel candidatos avaliados saem da lista do dia, " +
                            "mas os registros, respostas e currículos continuam guardados."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarEncerramento = false
                    aoEncerrarAvaliados()
                }) { Text("Encerrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEncerramento = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SeloStatus(status: StatusEntrevista, modifier: Modifier = Modifier) {
    val (corDoFundo, corDoTexto) = when (status) {
        StatusEntrevista.AGENDADA ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusEntrevista.EM_ANDAMENTO ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusEntrevista.CONCLUIDA ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant

        // Aprovado é o único resultado positivo do fluxo, então usa a cor de
        // destaque. Reprovado e cancelado são negativos, "não compareceu" e
        // "encerrada" são apenas neutros.
        StatusEntrevista.APROVADO ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        StatusEntrevista.REPROVADO,
        StatusEntrevista.CANCELADA ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusEntrevista.NAO_COMPARECEU,
        StatusEntrevista.ENCERRADA ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .background(corDoFundo, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = status.rotulo, style = MaterialTheme.typography.labelSmall, color = corDoTexto)
    }
}

@Composable
private fun DialogoAdicionarCandidato(
    estado: EstadoAgenda,
    aoAlterarNome: (String) -> Unit,
    aoAlterarTelefone: (String) -> Unit,
    aoAlterarModoCurriculo: (ModoCurriculo) -> Unit,
    aoAlterarResumoCurriculo: (String) -> Unit,
    aoSelecionarArquivo: (android.net.Uri) -> Unit,
    aoRemoverArquivo: () -> Unit,
    aoSelecionarVaga: (Long?) -> Unit,
    aoAlterarHora: (Int) -> Unit,
    aoAlterarMinuto: (Int) -> Unit,
    aoVoltarAoHorarioCalculado: () -> Unit,
    aoSalvar: () -> Unit,
    aoFechar: () -> Unit,
) {
    val seletorArquivo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(aoSelecionarArquivo) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Novo candidato") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "O horário já vem calculado pela ordem do dia. " +
                        "Se precisar de outro, é só trocar aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeletorNumero(
                        valor = estado.hora,
                        intervalo = (0..23).toList(),
                        aoAlterar = aoAlterarHora,
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    SeletorNumero(
                        valor = estado.minuto,
                        intervalo = (0..5).map { it * 10 },
                        aoAlterar = aoAlterarMinuto,
                    )
                    if (estado.horarioManual) {
                        TextButton(onClick = aoVoltarAoHorarioCalculado) {
                            Text("Usar o calculado")
                        }
                    }
                }
                CampoTexto(
                    valor = estado.nome,
                    aoAlterar = aoAlterarNome,
                    rotulo = "Nome",
                    erro = estado.erroNome,
                )
                CampoTexto(
                    valor = estado.telefone,
                    aoAlterar = aoAlterarTelefone,
                    rotulo = "Telefone",
                    telefone = true,
                    placeholder = "(35) 99999-9999",
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = estado.modoCurriculo == ModoCurriculo.RESUMO,
                        onClick = { aoAlterarModoCurriculo(ModoCurriculo.RESUMO) },
                        label = { Text("Resumo") },
                    )
                    FilterChip(
                        selected = estado.modoCurriculo == ModoCurriculo.ANEXO,
                        onClick = { aoAlterarModoCurriculo(ModoCurriculo.ANEXO) },
                        label = { Text("Anexar") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }

                when (estado.modoCurriculo) {
                    ModoCurriculo.RESUMO -> CampoTexto(
                        valor = estado.resumoCurriculo,
                        aoAlterar = aoAlterarResumoCurriculo,
                        rotulo = "Resumo do currículo",
                        dica = "Formação, experiência e objetivo do candidato.",
                        umaLinha = false,
                        minimoLinhas = 3,
                    )

                    ModoCurriculo.ANEXO -> BlocoAnexo(
                        nomeArquivo = estado.nomeArquivo,
                        tipoArquivo = estado.tipoArquivo,
                        aoEscolher = {
                            seletorArquivo.launch(AnexoRepository.TIPOS_ACEITOS.toTypedArray())
                        },
                        aoRemover = aoRemoverArquivo,
                    )
                }

                if (estado.vagas.isNotEmpty()) {
                    SeletorVaga(
                        vagas = estado.vagas,
                        vagaSelecionada = estado.vagaId,
                        aoSelecionar = aoSelecionarVaga,
                    )
                }

                if (estado.erroGeral != null) {
                    Text(
                        text = estado.erroGeral,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = aoSalvar, enabled = !estado.salvando) {
                if (estado.salvando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Adicionar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = aoFechar, enabled = !estado.salvando) { Text("Cancelar") }
        },
    )
}

@Composable
private fun BlocoAnexo(
    nomeArquivo: String,
    tipoArquivo: TipoCurriculo,
    aoEscolher: () -> Unit,
    aoRemover: () -> Unit,
) {
    if (nomeArquivo.isBlank()) {
        OutlinedButton(
            onClick = aoEscolher,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null)
            Text("Escolher PDF ou imagem", modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            text = "O arquivo fica guardado no aparelho, então não depende de link.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nomeArquivo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                    )
                    Text(
                        text = tipoArquivo.rotulo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                IconButton(onClick = aoRemover) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remover anexo",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorVaga(vagas: List<Vaga>, vagaSelecionada: Long?, aoSelecionar: (Long?) -> Unit) {
    var aberto by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = aberto,
        onExpandedChange = { aberto = it },
    ) {
        val selecionada = vagas.firstOrNull { it.id == vagaSelecionada }
        OutlinedTextField(
            value = selecionada?.titulo ?: "Sem vaga definida",
            onValueChange = {},
            readOnly = true,
            label = { Text("Vaga") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = aberto,
            onDismissRequest = { aberto = false },
        ) {
            DropdownMenuItem(
                text = { Text("Sem vaga definida") },
                onClick = { aberto = false; aoSelecionar(null) },
            )
            vagas.forEach { vaga ->
                DropdownMenuItem(
                    text = { Text(vaga.titulo) },
                    onClick = { aberto = false; aoSelecionar(vaga.id) },
                )
            }
        }
    }
}
