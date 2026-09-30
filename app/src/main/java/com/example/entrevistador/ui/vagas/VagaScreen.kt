package com.example.entrevistador.ui.vagas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entrevistador.data.repository.VagaRepository
import com.example.entrevistador.domain.model.Vaga
import com.example.entrevistador.ui.components.CampoTexto
import com.example.entrevistador.ui.components.CartaoSecao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Cadastro das vagas.
 *
 * É aqui que o recrutador escreve uma vez as informações da vaga (horário,
 * salário, escolaridade, atividades) e o roteiro passa a mostrá-las sozinho
 * no início de cada entrevista — sem o recrutador repitar para o candidato.
 */
class VagaViewModel(private val repository: VagaRepository) : ViewModel() {

    private val _formulario = MutableStateFlow<Vaga?>(null)
    val formulario: StateFlow<Vaga?> = _formulario.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    val vagas: StateFlow<List<Vaga>> = repository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Quantos candidatos já estão em cada vaga — alimenta o aviso de limite.
     *
     * Vem direto do banco como Flow para acompanhar também a entrada e a saída
     * de candidatos. Antes, a contagem só era refeita quando a lista de vagas
     * mudava, e o aviso ficava desatualizado depois de adicionar alguém.
     */
    val contagemPorVaga: StateFlow<Map<Long, Int>> = repository.observarContagemPorVaga()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun abrirNova() {
        _formulario.value = Vaga(titulo = "")
    }

    fun abrirEdicao(vaga: Vaga) {
        _formulario.value = vaga
    }

    fun fechar() {
        _formulario.value = null
    }

    fun aoAlterar(vaga: Vaga) {
        _formulario.value = vaga
    }

    fun salvar() {
        val vaga = _formulario.value ?: return
        if (vaga.titulo.isBlank()) {
            _mensagem.value = "Informe o título da vaga."
            return
        }
        viewModelScope.launch {
            repository.salvar(vaga)
            _formulario.value = null
            _mensagem.value = "Vaga salva."
        }
    }

    fun excluir(vaga: Vaga) {
        viewModelScope.launch {
            repository.excluir(vaga.id)
            _mensagem.value = "Vaga excluída."
        }
    }

    fun consumirMensagem() {
        _mensagem.value = null
    }
}

@androidx.compose.runtime.Composable
fun VagaScreen(
    viewModel: VagaViewModel,
    aoEditarRoteiroDaVaga: (id: Long, titulo: String) -> Unit = { _, _ -> },
) {
    val vagas by viewModel.vagas.collectAsStateWithLifecycle()
    val formulario by viewModel.formulario.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val contagemPorVaga by viewModel.contagemPorVaga.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumirMensagem()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::abrirNova,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova vaga") },
            )
        },
    ) { padding ->
        if (vagas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Work,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = "Nenhuma vaga cadastrada",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = "Cadastre a vaga uma vez e o roteiro mostra as informações " +
                            "automaticamente no início de cada entrevista.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vagas, key = { it.id }) { vaga ->
                    CartaoVaga(
                        vaga = vaga,
                        candidatos = contagemPorVaga[vaga.id] ?: 0,
                        aoEditar = { viewModel.abrirEdicao(vaga) },
                        aoExcluir = { viewModel.excluir(vaga) },
                        aoEditarRoteiro = { aoEditarRoteiroDaVaga(vaga.id, vaga.titulo) },
                    )
                }
            }
        }
    }

    formulario?.let { vaga ->
        DialogoVaga(
            vaga = vaga,
            aoAlterar = viewModel::aoAlterar,
            aoSalvar = viewModel::salvar,
            aoFechar = viewModel::fechar,
        )
    }
}

@Composable
private fun CartaoVaga(
    vaga: Vaga,
    candidatos: Int,
    aoEditar: () -> Unit,
    aoExcluir: () -> Unit,
    aoEditarRoteiro: () -> Unit,
) {
    var confirmar by remember { mutableStateOf(false) }

    // O limite não bloqueia: o recrutador pode querer passar dele, o app avisa.
    val limite = vaga.limiteCandidatos.takeIf { it > 0 }
    val estourou = limite != null && candidatos > limite

    Card(
        onClick = aoEditar,
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
            Icon(
                imageVector = Icons.Filled.Work,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(vaga.titulo, style = MaterialTheme.typography.titleMedium)
                val resumo = vaga.resumoCurto()
                if (resumo.isNotBlank()) {
                    Text(
                        text = resumo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (limite != null) {
                    Text(
                        text = "$candidatos de $limite candidatos",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (estourou) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (estourou) {
                    Text(
                        text = "Acima do limite de candidatos desta vaga.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = aoEditarRoteiro) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Roteiro desta vaga",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            IconButton(onClick = { confirmar = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir vaga")
            }
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Excluir vaga?") },
            text = { Text("${vaga.titulo} sai do cadastro.") },
            confirmButton = {
                TextButton(onClick = { confirmar = false; aoExcluir() }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun DialogoVaga(
    vaga: Vaga,
    aoAlterar: (Vaga) -> Unit,
    aoSalvar: () -> Unit,
    aoFechar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (vaga.id == 0L) "Nova vaga" else "Editar vaga") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                CampoTexto(
                    valor = vaga.titulo,
                    aoAlterar = { aoAlterar(vaga.copy(titulo = it)) },
                    rotulo = "Título da vaga",
                    placeholder = "Auxiliar administrativo",
                )
                CampoTexto(
                    valor = vaga.empresa,
                    aoAlterar = { aoAlterar(vaga.copy(empresa = it)) },
                    rotulo = "Nome da empresa",
                    placeholder = "Empresa que está contratando",
                )

                SeletorOpcao(
                    rotulo = "Tipo de contrato",
                    valor = vaga.tipoContrato,
                    opcoes = OPCOES_CONTRATO,
                    aoSelecionar = { aoAlterar(vaga.copy(tipoContrato = it)) },
                )

                CartaoSecao(titulo = "Horário de trabalho") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SeletorHora(
                            rotulo = "Entrada",
                            minutos = vaga.entradaMinutos,
                            aoEscolher = { aoAlterar(vaga.copy(entradaMinutos = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        SeletorHora(
                            rotulo = "Saída",
                            minutos = vaga.saidaMinutos,
                            aoEscolher = { aoAlterar(vaga.copy(saidaMinutos = it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                CampoTexto(
                    valor = vaga.salarioBeneficios,
                    aoAlterar = { aoAlterar(vaga.copy(salarioBeneficios = it)) },
                    rotulo = "Salário e benefícios",
                    dica = "Use Enter para pular de linha.",
                    umaLinha = false,
                    minimoLinhas = 3,
                )
                CampoTexto(
                    valor = vaga.tempoExperiencia,
                    aoAlterar = { aoAlterar(vaga.copy(tempoExperiencia = it)) },
                    rotulo = "Tempo de experiência",
                    placeholder = "Mínimo de 1 ano",
                )

                SeletorOpcao(
                    rotulo = "Escolaridade",
                    valor = vaga.escolaridade,
                    opcoes = OPCOESCOLARIDADE,
                    aoSelecionar = { aoAlterar(vaga.copy(escolaridade = it)) },
                )
                SeletorOpcao(
                    rotulo = "Precisa de habilitação?",
                    valor = vaga.exigeHabilitacao,
                    opcoes = OPCOES_SIM_NAO,
                    aoSelecionar = { aoAlterar(vaga.copy(exigeHabilitacao = it)) },
                )
                CampoTexto(
                    valor = vaga.limiteCandidatos.takeIf { it > 0 }?.toString().orEmpty(),
                    aoAlterar = { texto ->
                        aoAlterar(vaga.copy(limiteCandidatos = texto.filter(Char::isDigit).toIntOrNull() ?: 0))
                    },
                    rotulo = "Limite de candidatos",
                    dica = "Deixe vazio para não ter limite. Ao passar do limite, o app avisa.",
                    numerico = true,
                    placeholder = "Sem limite",
                )
                CampoTexto(
                    valor = vaga.resumoAtividades,
                    aoAlterar = { aoAlterar(vaga.copy(resumoAtividades = it)) },
                    rotulo = "Resumo das atividades principais",
                    dica = "Use Enter para pular de linha.",
                    umaLinha = false,
                    minimoLinhas = 4,
                )
            }
        },
        confirmButton = { Button(onClick = aoSalvar) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** Lista suspensa para escolhas fechadas (contrato, escolaridade, sim/não). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorOpcao(
    rotulo: String,
    valor: String,
    opcoes: List<String>,
    aoSelecionar: (String) -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = aberto, onExpandedChange = { aberto = it }) {
        OutlinedTextField(
            value = valor.ifBlank { "Selecione" },
            onValueChange = {},
            readOnly = true,
            label = { Text(rotulo) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(opcao) },
                    onClick = { aberto = false; aoSelecionar(opcao) },
                )
            }
        }
    }
}

/** Botão que abre um relógio de verdade para a entrada/saída da vaga. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorHora(
    rotulo: String,
    minutos: Int,
    aoEscolher: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var aberto by remember { mutableStateOf(false) }

    Surface(
        onClick = { aberto = true },
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (minutos == Vaga.SEM_HORARIO) {
                    "Selecione"
                } else {
                    "%02d:%02d".format(minutos / 60, minutos % 60)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    if (aberto) {
        val estado = rememberTimePickerState(
            initialHour = if (minutos == Vaga.SEM_HORARIO) 8 else (minutos / 60).coerceIn(0, 23),
            initialMinute = if (minutos == Vaga.SEM_HORARIO) 0 else (minutos % 60).coerceIn(0, 59),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { aberto = false },
            title = { Text("Escolher $rotulo") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = estado)
                }
            },
            confirmButton = {
                Button(onClick = {
                    aberto = false
                    aoEscolher(estado.hour * 60 + estado.minute)
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { aberto = false }) { Text("Cancelar") }
            },
        )
    }
}

private val OPCOES_CONTRATO = listOf(
    "CLT", "PJ", "Prestação de serviço", "Estágio", "Temporário", "Autônomo",
)

private val OPCOESCOLARIDADE = listOf(
    "Ensino fundamental completo",
    "Ensino fundamental incompleto",
    "Ensino médio completo",
    "Ensino médio incompleto",
    "Ensino superior completo",
    "Ensino superior incompleto",
    "Pós-graduação",
    "Não exige escolaridade",
)

private val OPCOES_SIM_NAO = listOf("Sim", "Não")
