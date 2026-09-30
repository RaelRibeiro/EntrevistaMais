package com.example.entrevistador.ui.roteiro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entrevistador.data.repository.RoteiroRepository
import com.example.entrevistador.data.repository.VagaRepository
import com.example.entrevistador.domain.model.Pergunta
import com.example.entrevistador.domain.model.RespostaAutomatica
import com.example.entrevistador.domain.model.Roteiro
import com.example.entrevistador.domain.model.TipoResposta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoRoteiro(
    val carregando: Boolean = true,
    /** Roteiro em edição: o da vaga se ela tiver um, o padrão do processo se não. */
    val roteiro: Roteiro? = null,
    val editando: Boolean = false,
    val rascunhoTitulo: String = "",
    val perguntas: List<Pergunta> = emptyList(),
    val salvo: Boolean = false,
    val mensagem: String? = null,
    /** Verdadeiro quando esta tela edita o roteiro de uma vaga, não o padrão. */
    val ehDaVaga: Boolean = false,
    val tituloVaga: String = "",
    /**
     * A vaga ainda não tem roteiro próprio e está herdando o padrão do
     * processo. Só nesse caso faz sentido oferecer "voltar ao padrão".
     */
    val usaRoteiroPadrao: Boolean = false,
)

/**
 * Edição do roteiro: lista de perguntas, na ordem em que serão feitas.
 *
 * A mesma tela atende dois casos:
 *  - [vagaId] nulo: o roteiro padrão do processo, que serve de base para todos.
 *  - [vagaId] preenchido: o roteiro próprio da vaga. Ele começa como cópia do
 *    padrão e, a partir daí, só vale para aquela vaga.
 *
 * Uma vaga que nunca foi editada não tem roteiro guardado: ela mostra o padrão
 * e continua acompanhando as mudanças dele. A cópia só nasce quando o
 * recrutador edita.
 */
class RoteiroViewModel(
    private val repository: RoteiroRepository,
    private val vagaId: Long? = null,
    private val vagaRepository: VagaRepository? = null,
) : ViewModel() {

    private val _rascunho = MutableStateFlow(Rascunho())
    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    /** Nome da vaga, para o cabeçalho da tela. Vem do cadastro e é observado. */
    private val tituloVaga: Flow<String> = vagaRepository?.observarTodas()
        ?.map { lista -> lista.firstOrNull { it.id == vagaId }?.titulo.orEmpty() }
        ?: flowOf("")

    /** Roteiro que vale para esta tela, e se a vaga já tem um próprio. */
    private data class Fonte(val roteiro: Roteiro?, val proprioDaVaga: Boolean)

    private val fonte: Flow<Fonte> = if (vagaId == null) {
        repository.observarPadrao().map { Fonte(it, proprioDaVaga = true) }
    } else {
        combine(
            repository.observarParaVaga(vagaId),
            repository.observarPadrao(),
        ) { proprio, padrao ->
            Fonte(roteiro = proprio ?: padrao, proprioDaVaga = proprio != null)
        }
    }

    val estado: StateFlow<EstadoRoteiro> = combine(
        fonte,
        tituloVaga,
        _rascunho,
    ) { fonteAtual, nomeVaga, rascunho ->
        val roteiro = fonteAtual.roteiro
        EstadoRoteiro(
            carregando = false,
            roteiro = roteiro,
            editando = rascunho.editando,
            rascunhoTitulo = rascunho.titulo.ifEmpty { tituloSugerido(roteiro, nomeVaga) },
            perguntas = if (rascunho.editando) {
                rascunho.perguntas.ifEmpty { roteiro?.perguntas.orEmpty() }
            } else {
                roteiro?.perguntas.orEmpty()
            },
            salvo = rascunho.salvo,
            ehDaVaga = vagaId != null,
            tituloVaga = nomeVaga,
            usaRoteiroPadrao = vagaId != null && !fonteAtual.proprioDaVaga,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoRoteiro())

    /**
     * Título sugerido ao criar o roteiro da vaga: parte do título do padrão e
     * ganha o nome da vaga, para não ficar igual ao roteiro de todo mundo.
     */
    private fun tituloSugerido(roteiro: Roteiro?, nomeVaga: String): String {
        val base = roteiro?.titulo.orEmpty()
        if (vagaId == null || nomeVaga.isBlank()) return base
        return "$base — $nomeVaga"
    }

    fun editar() {
        val atual = estado.value
        _rascunho.update {
            it.copy(
                editando = true,
                titulo = atual.rascunhoTitulo,
                perguntas = atual.perguntas,
                salvo = false,
            )
        }
    }

    fun aoAlterarTitulo(valor: String) = _rascunho.update { it.copy(titulo = valor, salvo = false) }

    fun aoAlterarPergunta(indice: Int, pergunta: Pergunta) = _rascunho.update { rascunho ->
        val copia = rascunho.perguntas.toMutableList()
        if (indice !in copia.indices) return@update rascunho
        copia[indice] = pergunta.copy(ordem = indice)
        rascunho.copy(perguntas = copia, salvo = false)
    }

    fun moverPergunta(indice: Int, delta: Int) = _rascunho.update { rascunho ->
        val copia = rascunho.perguntas.toMutableList()
        val destino = indice + delta
        if (indice !in copia.indices || destino !in copia.indices) return@update rascunho
        copia.add(destino, copia.removeAt(indice))
        rascunho.copy(
            perguntas = copia.mapIndexed { posicao, pergunta -> pergunta.copy(ordem = posicao) },
            salvo = false,
        )
    }

    fun adicionarPergunta() = _rascunho.update { rascunho ->
        val nova = Pergunta(
            roteiroId = rascunho.perguntas.firstOrNull()?.roteiroId ?: 0L,
            ordem = rascunho.perguntas.size,
            titulo = "Nova pergunta",
        )
        rascunho.copy(perguntas = rascunho.perguntas + nova, salvo = false)
    }

    fun removerPergunta(indice: Int) = _rascunho.update { rascunho ->
        val copia = rascunho.perguntas.toMutableList()
        if (indice !in copia.indices) return@update rascunho
        copia.removeAt(indice)
        rascunho.copy(
            perguntas = copia.mapIndexed { posicao, pergunta -> pergunta.copy(ordem = posicao) },
            salvo = false,
        )
    }

    fun cancelarEdicao() {
        _rascunho.value = Rascunho()
    }

    fun salvar() {
        val atual = estado.value
        val titulo = atual.rascunhoTitulo.trim()
        if (titulo.isBlank()) {
            _mensagem.value = "Dê um título ao roteiro."
            return
        }

        viewModelScope.launch {
            val perguntas = atual.perguntas.mapIndexed { indice, pergunta ->
                pergunta.copy(ordem = indice)
            }
            if (vagaId != null) {
                repository.salvarParaVaga(vagaId, titulo, perguntas)
                _rascunho.value = Rascunho(salvo = true)
                _mensagem.value = "Roteiro da vaga salvo."
                return@launch
            }

            val base = atual.roteiro
            val id = repository.salvar(
                (base ?: Roteiro(titulo = titulo)).copy(titulo = titulo, padrao = true)
            )
            repository.salvarPerguntas(roteiroId = id, perguntas = perguntas)
            _rascunho.value = Rascunho(salvo = true)
            _mensagem.value = "Roteiro salvo."
        }
    }

    /**
     * Recria as perguntas originais do processo.
     *
     * No roteiro padrão, volta as perguntas do processo seletivo. Numa vaga,
     * apaga o roteiro próprio e ela volta a seguir o padrão — as outras vagas
     * não são tocadas.
     */
    fun restaurarPadrao() {
        viewModelScope.launch {
            if (vagaId != null) {
                repository.voltarAoRoteiroPadrao(vagaId)
                _rascunho.value = Rascunho(salvo = true)
                _mensagem.value = "Esta vaga voltou a usar o roteiro padrão."
                return@launch
            }

            val roteiro = repository.obterPadrao() ?: run {
                repository.garantirPadrao()
                repository.obterPadrao()
            } ?: return@launch

            repository.salvarPerguntas(roteiro.id, RoteiroRepository.perguntasPadrao(roteiro.id))
            _rascunho.value = Rascunho(salvo = true)
            _mensagem.value = "Perguntas originais restauradas."
        }
    }

    fun consumirMensagem() {
        _mensagem.value = null
    }

    private data class Rascunho(
        val editando: Boolean = false,
        val titulo: String = "",
        val perguntas: List<Pergunta> = emptyList(),
        val salvo: Boolean = false,
    )
}
