package com.example.entrevistador.ui.definicoes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entrevistador.core.minutosParaHora
import com.example.entrevistador.ui.components.CampoTexto
import com.example.entrevistador.ui.components.CartaoSecao

/**
 * Tela 2: os valores que a agenda usa para se montar sozinha.
 *
 * Tudo que é alterado aqui é reaplicado aos dias que já têm entrevistas, então o
 * recrutador não precisa reorganizar nada à mão.
 */
@Composable
fun DefinicoesScreen(
    viewModel: DefinicoesViewModel,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(estado.mensagem) {
        estado.mensagem?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumirMensagem()
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Definições padrão",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "A agenda usa estes valores para calcular os horários de cada entrevista.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CartaoSecao(titulo = "Horário do dia", icone = Icons.Filled.Schedule) {
                SeletorHora(
                    rotulo = "Horário de início",
                    descricao = "Primeira entrevista do dia",
                    minutos = estado.horarioInicio,
                    aoEscolher = viewModel::definirHorarioInicio,
                )
                SeletorIntervaloAlmoco(
                    minutosInicio = estado.almocoInicio,
                    minutosFim = estado.almocoFim,
                    aoAlterarInicio = viewModel::definirAlmocoInicio,
                    aoAlterarFim = viewModel::definirAlmocoFim,
                )
            }

            CartaoSecao(titulo = "Entrevistas", icone = Icons.Filled.Today) {
                CampoTexto(
                    valor = estado.quantidade,
                    aoAlterar = viewModel::definirQuantidade,
                    rotulo = "Quantidade por dia",
                    erro = estado.erros.quantidade,
                    dica = "Máximo de candidatos no mesmo dia",
                    numerico = true,
                    placeholder = "6",
                )
                CampoTexto(
                    valor = estado.duracao,
                    aoAlterar = viewModel::definirDuracao,
                    rotulo = "Duração por entrevista",
                    erro = estado.erros.duracao,
                    dica = "Em minutos",
                    numerico = true,
                    placeholder = "45",
                )
                CampoTexto(
                    valor = estado.intervalo,
                    aoAlterar = viewModel::definirIntervalo,
                    rotulo = "Intervalo entre entrevistas",
                    erro = estado.erros.intervalo,
                    dica = "Folga em minutos (0 = sem folga)",
                    numerico = true,
                    placeholder = "0",
                    acaoDoTeclado = ImeAction.Done,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = "Prévia do dia",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = estado.previa,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Button(
                onClick = viewModel::salvar,
                enabled = !estado.salvando && !estado.carregando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (estado.salvando) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(if (estado.salvando) "Salvando..." else "Salvar e reorganizar agenda")
            }

            TextButton(
                onClick = viewModel::restaurarPadroes,
                enabled = !estado.salvando && !estado.carregando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Text("Restaurar valores padrão", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * Campo de hora que abre um relógio de verdade.
 *
 * Antes eram botões de +15/-15, que obrigavam a apertar várias vezes para chegar
 * em 08:47. O seletor permite escolher o minuto diretamente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorHora(
    rotulo: String,
    descricao: String,
    minutos: Int,
    aoEscolher: (Int) -> Unit,
) {
    var relogioAberto by remember { mutableStateOf(false) }

    Column {
        Text(rotulo, style = MaterialTheme.typography.titleMedium)
        Text(
            text = descricao,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // `Surface(onClick = ...)` em vez de `OutlinedTextField` + `.clickable`:
        // o campo de texto engolia o toque e o relógio não abria.
        Surface(
            onClick = { relogioAberto = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = minutos.minutosParaHora(), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { relogioAberto = true }) { Text("Alterar") }
            }
        }
    }

    if (relogioAberto) {
        DialogoRelogio(
            minutos = minutos,
            aoConfirmar = {
                relogioAberto = false
                aoEscolher(it)
            },
            aoFechar = { relogioAberto = false },
        )
    }
}

/**
 * Almoço: início e fim na mesma linha, porque os dois andam juntos.
 * É um único bloco visual "Almoço", e não dois campos soltos.
 */
@Composable
private fun SeletorIntervaloAlmoco(
    minutosInicio: Int,
    minutosFim: Int,
    aoAlterarInicio: (Int) -> Unit,
    aoAlterarFim: (Int) -> Unit,
) {
    var alterandoInicio by remember { mutableStateOf(false) }
    var alterandoFim by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text("Almoço", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Ninguém é entrevistado nesse intervalo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CampoHora(
                rotulo = "Início",
                minutos = minutosInicio,
                aoClicar = { alterandoInicio = true },
                modifier = Modifier.weight(1f),
            )
            CampoHora(
                rotulo = "Fim",
                minutos = minutosFim,
                aoClicar = { alterandoFim = true },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (alterandoInicio) {
        DialogoRelogio(
            minutos = minutosInicio,
            aoConfirmar = {
                alterandoInicio = false
                aoAlterarInicio(it)
            },
            aoFechar = { alterandoInicio = false },
        )
    }
    if (alterandoFim) {
        DialogoRelogio(
            minutos = minutosFim,
            aoConfirmar = {
                alterandoFim = false
                aoAlterarFim(it)
            },
            aoFechar = { alterandoFim = false },
        )
    }
}

/**
 * Campo somente de leitura que mostra a hora e abre o relógio ao toque.
 *
 * Não é um `OutlinedTextField`: um campo de texto, mesmo `readOnly`, consome o
 * toque para posicionar o cursor e o `.clickable` nunca era disparado — por isso
 * o horário do almoço não abria o relógio. Aqui é uma `Surface` com borda e
 * `clickable`, que entrega o toque de fato.
 */
@Composable
private fun CampoHora(
    rotulo: String,
    minutos: Int,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = aoClicar,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = rotulo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = minutos.minutosParaHora(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoRelogio(
    minutos: Int,
    aoConfirmar: (Int) -> Unit,
    aoFechar: () -> Unit,
) {
    val estado = rememberTimePickerState(
        initialHour = (minutos / 60).coerceIn(0, 23),
        initialMinute = (minutos % 60).coerceIn(0, 59),
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Escolher horário") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = estado)
            }
        },
        confirmButton = {
            Button(onClick = { aoConfirmar(estado.hour * 60 + estado.minute) }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = aoFechar) { Text("Cancelar") }
        },
    )
}
