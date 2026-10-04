package com.example.entrevistador.ui.curriculo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
 * O zoom é feito com dois dedos; para voltar ao tamanho normal, dá para
 * pinçar fechando os dedos de novo.
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

/**
 * Todas as páginas lado a lado, com rolagem e zoom por pinch.
 *
 * A rolagem entre páginas só é liberada quando a página atual está no tamanho
 * normal. Com a página ampliada o arrasto precisa mover o currículo, e não
 * trocar de página — senão dar zoom em um currículo de três páginas impediria
 * justamente de chegar à segunda.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaginasZoom(caminhos: List<String>) {
    val pager = rememberPagerState(pageCount = { caminhos.size })
    var paginaAmpliada by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !paginaAmpliada,
        ) { pagina ->
            PaginaComZoom(
                caminho = caminhos[pagina],
                aoAmpliar = { paginaAmpliada = it },
            )
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
 * O zoom é só por pinça (dois dedos, abrindo e fechando), como se faz em
 * qualquer leitor de PDF. No tamanho normal a página aparece inteira; ao
 * ampliar, ela passa a ser arrastada até onde o recrutador precisar ler.
 */
@Composable
private fun PaginaComZoom(caminho: String, aoAmpliar: (Boolean) -> Unit) {
    val bitmap = remember(caminho) { BitmapFactory.decodeFile(caminho) }

    if (bitmap == null) {
        Text("Página indisponível.", color = Color.White)
        return
    }

    var zoom by remember(caminho) { mutableFloatStateOf(1f) }
    var deslocamento by remember(caminho) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(
                // No tamanho normal este bloco não existe: com ele, o
                // detectTransformGestures engole o arrasto do dedo e a troca de
                // página deixa de funcionar. Só com a página ampliada ele entra,
                // e aí quem recebe o arrasto é a própria página.
                if (zoom > 1f) {
                    Modifier.pointerInput(caminho) {
                        detectTransformGestures { _, pan, _, escala ->
                            zoom = (zoom * escala).coerceIn(1f, ZOOM_MAXIMO)
                            // O pan é o movimento do centro da pinça: num gesto
                            // de dois dedos ele já é o arrasto da página, e num
                            // gesto de um dedo é o arrasto que o recrutador quer.
                            deslocamento += pan
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Com a imagem ajustada à tela, quanto maior o zoom mais ela cresce
        // para fora dos limites; o deslocamento é limitado a essa sobra para
        // o currículo não poder ser arrastado para fora da vista.
        val larguraVisivel = constraints.maxWidth.toFloat()
        val alturaVisivel = constraints.maxHeight.toFloat()
        val escalaConteudo = minOf(
            larguraVisivel / bitmap.width,
            alturaVisivel / bitmap.height,
        )
        val sobraX = ((bitmap.width * escalaConteudo * zoom - larguraVisivel) / 2f)
            .coerceAtLeast(0f)
        val sobraY = ((bitmap.height * escalaConteudo * zoom - alturaVisivel) / 2f)
            .coerceAtLeast(0f)

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = deslocamento.x.coerceIn(-sobraX, sobraX)
                    translationY = deslocamento.y.coerceIn(-sobraY, sobraY)
                },
        )
    }

    // Ao voltar ao tamanho normal o deslocamento antigo ficaria pendurado, e a
    // próxima pinça recomeçaria de onde a última parou em vez do centro.
    LaunchedEffect(zoom) {
        if (zoom <= 1f) deslocamento = Offset.Zero
        aoAmpliar(zoom > 1f)
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
