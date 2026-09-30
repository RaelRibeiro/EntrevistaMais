package com.example.entrevistador.ui.curriculo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.entrevistador.data.repository.CurriculoPaginasRepository
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Zoom máximo. Acima disso a imagem perde nitidez e o recrutador só se perde.
 * Um toque vai para 2x, o duplo toque vai para o máximo.
 */
private const val ZOOM_MAXIMO = 6f

/**
 * Visualizador de currículo em PDF, uma imagem por página.
 *
 * O recrutador precisa do currículo em tela cheia, com zoom, para ler o
 * currículo inteiro enquanto fala com o candidato.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculoPaginasScreen(
    viewModel: CurriculoPaginasViewModel,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(estado.titulo, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                estado.carregando -> CircularProgressIndicator(color = Color.White)

                estado.erro != null -> Text(
                    text = estado.erro.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )

                else -> PaginasZoom(estado.paginas)
            }
        }
    }
}

/** Todas as páginas lado a lado, com rolagem e zoom por pinch. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaginasZoom(caminhos: List<String>) {
    val pager = rememberPagerState(pageCount = { caminhos.size })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { pagina ->
            PaginaComZoom(caminhos[pagina])
        }

        // O contador fica sobre a página: o currículo é o que importa aqui.
        if (caminhos.size > 1) {
            Text(
                text = "Página ${pager.currentPage + 1} de ${caminhos.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .background(Color(0x99000000), MaterialTheme.shapes.medium)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Uma página, que abre ocupando a tela e dá zoom por pinch.
 *
 * Começa ajustada à largura da tela. Um toque alterna entre o tamanho
 * ajustado e o tamanho real, que é o que o recrutador quer quando quer ler
 * o texto pequeno sem pinçar.
 */
@Composable
private fun PaginaComZoom(caminho: String) {
    val bitmap = remember(caminho) { BitmapFactory.decodeFile(caminho) }

    if (bitmap == null) {
        Text("Página indisponível.", color = Color.White)
        return
    }

    var zoom by remember(caminho) { mutableFloatStateOf(1f) }
    var deslocamento by remember(caminho) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(caminho) {
                detectTransformGestures { _, pan, _, escala ->
                    zoom = (zoom * escala).coerceIn(1f, ZOOM_MAXIMO)
                    // Arrastar só faz sentido com a página ampliada; no tamanho
                    // ajustado o pan puxaria a imagem para fora da tela.
                    if (zoom > 1f) {
                        deslocamento += pan
                    }
                }
            }
            .pointerInput(caminho) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoom > 1f) {
                            zoom = 1f
                            deslocamento = Offset.Zero
                        } else {
                            zoom = ZOOM_MAXIMO
                        }
                    },
                    onTap = {
                        if (zoom > 1f) {
                            zoom = 1f
                            deslocamento = Offset.Zero
                        } else {
                            zoom = 2f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = deslocamento.x
                    translationY = deslocamento.y
                },
        )
    }
}

class CurriculoPaginasViewModel(
    private val repository: CurriculoPaginasRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    /**
     * Carrega o currículo anexado.
     *
     * Serve tanto para PDF quanto para imagem: o PDF vira uma imagem por
     * página, e a imagem é usada como está. A tela é a mesma nos dois casos.
     */
    fun carregar(caminho: String, titulo: String) {
        if (_estado.value.carregando && _estado.value.titulo == titulo) return
        _estado.update { it.copy(carregando = true, erro = null, titulo = titulo) }

        viewModelScope.launch {
            when (val resultado = repository.paginas(caminho)) {
                is CurriculoPaginasRepository.Resultado.Paginas ->
                    _estado.update {
                        it.copy(carregando = false, paginas = resultado.caminhos, erro = null)
                    }

                is CurriculoPaginasRepository.Resultado.Erro ->
                    _estado.update { it.copy(carregando = false, erro = resultado.mensagem) }
            }
        }
    }

    data class Estado(
        val carregando: Boolean = true,
        val titulo: String = "Currículo",
        val paginas: List<String> = emptyList(),
        val erro: String? = null,
    )

    companion object {
        fun factory(repository: CurriculoPaginasRepository) = viewModelFactory {
            initializer { CurriculoPaginasViewModel(repository) }
        }
    }
}
