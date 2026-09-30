package com.example.entrevistador.ui.agenda

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entrevistador.data.repository.AnexoRepository
import com.example.entrevistador.data.repository.DefinicoesRepository
import com.example.entrevistador.data.repository.EntrevistaRepository
import com.example.entrevistador.data.repository.VagaRepository
import com.example.entrevistador.domain.OrdenacaoAgenda
import com.example.entrevistador.domain.model.DefinicoesPadrao
import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.StatusEntrevista
import com.example.entrevistador.domain.model.TipoCurriculo
import com.example.entrevistador.domain.model.Vaga
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Como o currículo foi escolhido no diálogo. */
enum class ModoCurriculo(val rotulo: String) {
    RESUMO("Resumo"),
    ANEXO("Anexar PDF ou imagem"),
}

data class EstadoAgenda(
    val carregando: Boolean = true,
    val data: LocalDate = LocalDate.now(),
    val entrevistas: List<Entrevista> = emptyList(),
    val definicoes: DefinicoesPadrao = DefinicoesPadrao.PADRAO,
    val vagas: List<Vaga> = emptyList(),
    val mostrarFormulario: Boolean = false,
    val nome: String = "",
    val telefone: String = "",
    val modoCurriculo: ModoCurriculo = ModoCurriculo.RESUMO,
    val resumoCurriculo: String = "",
    val tipoArquivo: TipoCurriculo = TipoCurriculo.PDF,
    val nomeArquivo: String = "",
    val vagaId: Long? = null,
    val erroNome: String? = null,
    val erroGeral: String? = null,
    val salvando: Boolean = false,
    val mostrarEncerradas: Boolean = false,
    /** Candidatos encerrados do dia, escondidos enquanto o toggle está desligado. */
    val encerradas: List<Entrevista> = emptyList(),
) {
    /**
     * Encerrar libera o horário para outro candidato, mas não apaga o registro:
     * por isso a vaga continua ocupada e só some da lista.
     */
    val vagasUsadas: Int get() = entrevistas.size + encerradas.size

    val vagasTotais: Int get() = definicoes.quantidadePorDia

    val diaLotado: Boolean get() = vagasUsadas >= vagasTotais

    /**
     * Quantos candidatos do dia já passaram por entrevista e estão aptos a serem
     * encerrados de uma vez. Zero esconde o atalho "Encerrar avaliados".
     */
    val quantidadeEncerravel: Int
        get() = (entrevistas + encerradas).count { it.status.encerravel }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(
    private val entrevistaRepository: EntrevistaRepository,
    private val definicoesRepository: DefinicoesRepository,
    private val anexoRepository: AnexoRepository,
    private val vagaRepository: VagaRepository,
) : ViewModel() {

    private val _data = MutableStateFlow(LocalDate.now())
    val data: StateFlow<LocalDate> = _data.asStateFlow()

    private val _formulario = MutableStateFlow(Formulario())
    val formulario: StateFlow<Formulario> = _formulario.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    /** Estado do seletor de horário individual de um candidato. */
    private val _horario = MutableStateFlow(Horario())
    val horario: StateFlow<Horario> = _horario.asStateFlow()

    private val _carregando = MutableStateFlow(true)
    val carregando: StateFlow<Boolean> = _carregando.asStateFlow()

    private val entrevistasDoDia = _data
        .flatMapLatest { entrevistaRepository.observarPorData(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vagas: StateFlow<List<Vaga>> = vagaRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mostrarEncerradas = MutableStateFlow(false)
    val mostrarEncerradas: StateFlow<Boolean> = _mostrarEncerradas.asStateFlow()

    val estado: StateFlow<EstadoAgenda> = combine(
        combine(_data, entrevistasDoDia, definicoesRepository.definicoes) { d, e, def -> Triple(d, e, def) },
        combine(_formulario, _carregando, vagas) { f, c, v -> Triple(f, c, v) },
        _mostrarEncerradas,
    ) { (dataSelecionada, entrevistas, definicoes), (formulario, carregando, listaVagas), mostrarEncerradas ->
        val ordenadas = OrdenacaoAgenda.ordenarParaExibicao(entrevistas)
        EstadoAgenda(
            carregando = carregando,
            data = dataSelecionada,
            // A ordem exibida é calculada, não é a ordem de insertion: o que
            // está em andamento e o mais próximo vêm sempre primeiro.
            entrevistas = ordenadas.filterNot { it.status == StatusEntrevista.ENCERRADA },
            definicoes = definicoes,
            vagas = listaVagas,
            mostrarFormulario = formulario.aberto,
            nome = formulario.nome,
            telefone = formulario.telefone,
            modoCurriculo = formulario.modoCurriculo,
            resumoCurriculo = formulario.resumoCurriculo,
            tipoArquivo = formulario.tipoArquivo,
            nomeArquivo = formulario.nomeArquivo,
            vagaId = formulario.vagaId,
            erroNome = formulario.erroNome,
            erroGeral = formulario.erroGeral,
            salvando = formulario.salvando,
            mostrarEncerradas = mostrarEncerradas,
            encerradas = ordenadas.filter { it.status == StatusEntrevista.ENCERRADA },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoAgenda())

    init {
        viewModelScope.launch {
            definicoesRepository.definicoes.first()
            _carregando.value = false
        }
    }

    fun irParaDia(delta: Long) = _data.update { it.plusDays(delta) }

    fun irParaData(nova: LocalDate) {
        _data.value = nova
        // Cada dia tem seus próprios encerrados: manter o toggle ligado ao
        // trocar de data mostraria candidatos que não são daquele dia.
        _mostrarEncerradas.value = false
    }

    fun alternarEncerradas() = _mostrarEncerradas.update { !it }

    fun abrirFormulario() = _formulario.update { it.copy(aberto = true) }

    fun fecharFormulario() {
        _formulario.value = Formulario()
    }

    fun aoAlterarNome(valor: String) = _formulario.update {
        it.copy(nome = valor, erroNome = null, erroGeral = null)
    }

    fun aoAlterarTelefone(valor: String) = _formulario.update { it.copy(telefone = valor) }

    fun aoAlterarResumoCurriculo(valor: String) = _formulario.update {
        it.copy(resumoCurriculo = valor, erroGeral = null)
    }

    fun aoAlterarModoCurriculo(modo: ModoCurriculo) = _formulario.update {
        it.copy(modoCurriculo = modo, erroGeral = null)
    }

    fun aoSelecionarVaga(id: Long?) = _formulario.update { it.copy(vagaId = id) }

    /** Cópia do arquivo escolhido para dentro do app. */
    fun aoSelecionarArquivo(uri: Uri) {
        viewModelScope.launch {
            val tipo = when (anexoRepository.tipoDeArquivo(uri)) {
                TipoCurriculo.PDF -> TipoCurriculo.PDF
                else -> TipoCurriculo.IMAGEM
            }
            when (val resultado = anexoRepository.copiar(uri)) {
                is AnexoRepository.Resultado.Sucesso -> _formulario.update {
                    it.copy(
                        caminhoArquivo = resultado.caminho,
                        nomeArquivo = resultado.nomeOriginal,
                        tipoArquivo = tipo,
                    )
                }

                is AnexoRepository.Resultado.Erro ->
                    _formulario.update { it.copy(erroGeral = resultado.mensagem) }
            }
        }
    }

    fun aoRemoverArquivo() = _formulario.update { it.copy(caminhoArquivo = "", nomeArquivo = "") }

    fun adicionarCandidato() {
        val formulario = _formulario.value
        if (formulario.nome.isBlank()) {
            _formulario.update { it.copy(erroNome = "Informe o nome do candidato.") }
            return
        }

        viewModelScope.launch {
            _formulario.update { it.copy(salvando = true) }
            val resultado = entrevistaRepository.adicionarCandidato(
                data = _data.value,
                nome = formulario.nome,
                telefone = formulario.telefone,
                resumoCurriculo = formulario.resumoCurriculo,
                tipoCurriculo = if (formulario.modoCurriculo == ModoCurriculo.RESUMO) {
                    TipoCurriculo.RESUMO
                } else {
                    formulario.tipoArquivo
                },
                caminhoCurriculo = formulario.caminhoArquivo,
                nomeArquivoCurriculo = formulario.nomeArquivo,
                vagaId = formulario.vagaId,
            )
            when (resultado) {
                is EntrevistaRepository.Resultado.Sucesso -> {
                    _formulario.value = Formulario()
                    _mensagem.value = "Candidato adicionado e horário reservado automaticamente."
                }

                is EntrevistaRepository.Resultado.Erro ->
                    _formulario.update { it.copy(salvando = false, erroGeral = resultado.mensagem) }

                else -> Unit
            }
        }
    }

    /**
     * Remove o candidato, as respostas, a tabela de locais e o currículo anexado.
     * Sem apagar o arquivo, ele ficaria ocupando espaço para sempre.
     */
    fun remover(id: Long) {
        viewModelScope.launch {
            _mensagem.value = when (val resultado = entrevistaRepository.remover(id)) {
                is EntrevistaRepository.Resultado.Erro -> resultado.mensagem
                is EntrevistaRepository.Resultado.Removido -> {
                    if (resultado.caminhoCurriculo.isNotBlank()) {
                        anexoRepository.apagar(resultado.caminhoCurriculo)
                    }
                    "Candidato removido e horários reajustados."
                }

                else -> "Candidato removido e horários reajustados."
            }
        }
    }

    fun moverParaCima(id: Long) = executar("Ordem atualizada.") {
        entrevistaRepository.moverParaCima(id)
    }

    fun moverParaBaixo(id: Long) = executar("Ordem atualizada.") {
        entrevistaRepository.moverParaBaixo(id)
    }

    fun marcarNaoCompareceu(id: Long) = executar("Marcado como não compareceu.") {
        entrevistaRepository.marcarNaoCompareceu(id)
    }

    fun cancelar(id: Long) = executar("Entrevista cancelada.") {
        entrevistaRepository.cancelar(id)
    }

    fun reabrir(id: Long) = executar("Entrevista reaberta.") {
        entrevistaRepository.reabrir(id)
    }

    /** Volta a decisão sem desfazer a entrevista: CONCLUIDA -> decisão -> CONCLUIDA. */
    fun voltarParaConcluida(id: Long) = executar("Decisão desfeita.") {
        entrevistaRepository.voltarParaConcluida(id)
    }

    fun aprovar(id: Long) = executar("Candidato aprovado para a próxima fase.") {
        entrevistaRepository.aprovar(id)
    }

    fun reprovar(id: Long) = executar("Candidato reprovado.") {
        entrevistaRepository.reprovar(id)
    }

    fun encerrar(id: Long) = executar("Candidato encerrado.") {
        entrevistaRepository.encerrar(id)
    }

    /**
     * Atalho para limpar a lista do dia: encerra todo mundo que já passou por
     * entrevista e decisão. Só aparece quando existe alguém elegível.
     */
    fun encerrarAvaliadosDoDia() {
        viewModelScope.launch {
            val quantidade = entrevistaRepository.encerrarAvaliadosDoDia(_data.value)
            _mensagem.value = if (quantidade == 0) {
                "Nenhum candidato avaliado para encerrar."
            } else if (quantidade == 1) {
                "1 candidato encerrado."
            } else {
                "$quantidade candidatos encerrados."
            }
        }
    }

    private fun executar(mensagemDeSucesso: String, bloco: suspend () -> EntrevistaRepository.Resultado) {
        viewModelScope.launch {
            _mensagem.value = when (val resultado = bloco()) {
                is EntrevistaRepository.Resultado.Erro -> resultado.mensagem
                // Exemplo: o horário foi salvo, mas encostou no candidato
                // seguinte. O aviso vale mais que a mensagem de sucesso.
                is EntrevistaRepository.Resultado.Aviso -> resultado.mensagem
                else -> mensagemDeSucesso
            }
        }
    }

    /** Abre o seletor de horário para um candidato da agenda. */
    fun abrirHorario(id: Long) {
        viewModelScope.launch {
            val entrevista = entrevistaRepository.buscarPorId(id) ?: return@launch
            _horario.update {
                it.copy(
                    aberto = true,
                    entrevistaId = id,
                    hora = entrevista.inicioMinutos / 60,
                    minuto = entrevista.inicioMinutos % 60,
                )
            }
        }
    }

    fun fecharHorario() {
        _horario.update { it.copy(aberto = false) }
    }

    fun aoAlterarHora(valor: Int) {
        _horario.update { it.copy(hora = valor) }
    }

    fun aoAlterarMinuto(valor: Int) {
        _horario.update { it.copy(minuto = valor) }
    }

    /** Salva o horário escolhido. O candidato passa a ter horário manual. */
    fun salvarHorario() {
        val atual = _horario.value
        val id = atual.entrevistaId ?: return
        val minutos = atual.hora * 60 + atual.minuto
        _horario.update { it.copy(aberto = false) }
        executar("Horário alterado.") {
            entrevistaRepository.alterarHorario(id, minutos)
        }
    }

    /** Devolve o candidato ao horário calculado pelas Definições. */
    fun voltarAoHorarioCalculado(id: Long) {
        executar("Horário voltou ao cálculo automático.") {
            entrevistaRepository.voltarAoHorarioPadrao(id)
        }
    }

    fun consumirMensagem() {
        _mensagem.value = null
    }

    data class Horario(
        val aberto: Boolean = false,
        val entrevistaId: Long? = null,
        val hora: Int = 8,
        val minuto: Int = 0,
    )

    data class Formulario(
        val aberto: Boolean = false,
        val nome: String = "",
        val telefone: String = "",
        val modoCurriculo: ModoCurriculo = ModoCurriculo.RESUMO,
        val resumoCurriculo: String = "",
        val caminhoArquivo: String = "",
        val nomeArquivo: String = "",
        val tipoArquivo: TipoCurriculo = TipoCurriculo.PDF,
        val vagaId: Long? = null,
        val erroNome: String? = null,
        val erroGeral: String? = null,
        val salvando: Boolean = false,
    )
}
