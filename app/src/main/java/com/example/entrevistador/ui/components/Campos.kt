package com.example.entrevistador.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.entrevistador.core.aplicarMascaraTelefone

/**
 * Campo de texto padrão do app, com rótulo, erro e suporte a senha.
 * Concentrar isso aqui evita repetir a mesma configuração em todas as telas.
 */
@Composable
fun CampoTexto(
    valor: String,
    aoAlterar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    erro: String? = null,
    dica: String? = null,
    placeholder: String? = null,
    umaLinha: Boolean = true,
    minimoLinhas: Int = 1,
    senha: Boolean = false,
    numerico: Boolean = false,
    telefone: Boolean = false,
    acaoDoTeclado: ImeAction = ImeAction.Next,
    /**
     * Mostra o valor mas não deixa editar. Usado no roteiro depois que a
     * entrevista é finalizada: as respostas ficam visíveis, mas o registro não
     * pode mais mudar.
     */
    somenteLeitura: Boolean = false,
) {
    var senhaVisivel by remember { mutableStateOf(false) }

    // A máscara do telefone precisa controlar o cursor. Com um `String` simples,
    // o Compose recalcula a seleção a cada reformatação e o dígito novo acaba
    // caindo ATRÁS do número já digitado. Guardando um `TextFieldValue` e fixando
    // a seleção no fim do texto, a escrita sempre segue da esquerda para a direita.
    var campo by remember { mutableStateOf(TextFieldValue(valor, TextRange(valor.length))) }

    // Sincroniza quando o valor muda de fora (ex.: ao carregar o candidato salvo).
    LaunchedEffect(valor) {
        if (campo.text != valor) {
            campo = TextFieldValue(valor, TextRange(valor.length))
        }
    }

    OutlinedTextField(
        value = campo,
        onValueChange = { novo ->
            if (somenteLeitura) return@OutlinedTextField
            if (telefone) {
                val mascarado = aplicarMascaraTelefone(novo.text)
                campo = TextFieldValue(mascarado, TextRange(mascarado.length))
                aoAlterar(mascarado)
            } else {
                campo = novo
                aoAlterar(novo.text)
            }
        },
        readOnly = somenteLeitura,
        label = { Text(rotulo) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = {
            val texto = erro ?: dica
            if (texto != null) Text(texto)
        },
        isError = erro != null,
        singleLine = umaLinha,
        minLines = minimoLinhas,
        visualTransformation = if (senha && !senhaVisivel) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (senha) {
            {
                IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                    Icon(
                        imageVector = if (senhaVisivel) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (senhaVisivel) "Ocultar senha" else "Mostrar senha",
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                senha -> KeyboardType.Password
                numerico -> KeyboardType.Number
                telefone -> KeyboardType.Phone
                else -> KeyboardType.Text
            },
            imeAction = acaoDoTeclado,
        ),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Título de seção, usado para agrupar blocos dentro das telas. */
@Composable
fun TituloSecao(
    texto: String,
    modifier: Modifier = Modifier,
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icone != null) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = texto.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Cartão com título, usado para os blocos de definição e detalhe. */
@Composable
fun CartaoSecao(
    titulo: String,
    modifier: Modifier = Modifier,
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    conteudo: @Composable () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TituloSecao(texto = titulo, icone = icone)
            conteudo()
        }
    }
}
