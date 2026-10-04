package com.example.entrevistador.ui.entrevista

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entrevistador.data.repository.AnexoRepository
import com.example.entrevistador.data.repository.EntrevistaFormularioRepository
import com.example.entrevistador.data.repository.EntrevistaRepository
import com.example.entrevistador.data.repository.RoteiroRepository
import com.example.entrevistador.data.repository.VagaRepository
import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.Experiencia
import com.example.entrevistador.domain.model.Pergunta
import com.example.entrevistador.domain.model.RespostaAutomatica
import com.example.entrevistador.domain.model.Roteiro
import com.example.entrevistador.domain.model.StatusEntrevista
import com.example.entrevistador.domain.model.TipoCurriculo
import com.example.entrevistador.domain.model.TipoResposta
import com.example.entrevistador.domain.model.Vaga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Qual das abas está aberta durante a entrevista. */
enum class AbaEntrevista(val rotulo: String) {
    CURRICULO("Currículo"),
    ROTEIRO("Roteiro"),
}

/** Uma linha da tabela de locais, já combinada com a entrevista. */
data class LinhaExperiencia(
    val id: Long = 0L,
    val local: String = "",
    val ano: String = "",
    val duracao: String = "",
    val cargo: String = "",
    val motivoSaida: String = "",
)

data class EstadoEntrevista(
    val carregando: Boolean = true,
    val entrevista: Entrevista? = null,
    val roteiro: Roteiro? = null,
    val vaga: Vaga? = null,
    val aba: AbaEntrevista = AbaEntrevista.CURRICULO,
    val emAndamento: Boolean = false,
    val decorridoMs: Long = 0L,
    val tempoExcedido: Boolean = false,
    val respostas: String = "",
    val respostasPorPergunta: Map<Long, String> = emptyMap(),
    val experiencias: List<LinhaExperiencia> = listOf(LinhaExperiencia()),
    val curriculo: String = "",
    val curriculoEditando: Boolean = false,
    val mensagem: String? = null,
) {
    val duracaoPrevistaMinutos: Int get() = entrevista?.duracaoMinutos ?: 0

    /** Perguntas que não são tabela nem seção: as que viram campo na tela. */
    val perguntasDoFormulario: List<Pergunta>
        get() = roteiro?.perguntas.orEmpty().filter {
            it.tipo != TipoResposta.SECAO && it.tipo != TipoResposta.TABELA_EXPERIENCIAS
        }

    /** "Dia e horário" da própria entrevista, usado no autopreenchimento. */
    val diaEHora: String
        get() {
            val entrevista = entrevista ?: return ""
            val data = entrevista.data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))
            val hora = "%02dh%02d".format(entrevista.horaInicio.hour, entrevista.horaInicio.minute)
            return "$data às $hora"
        }
}

class EntrevistaViewModel(
    private val entrevistaRepository: EntrevistaRepository,
    private val roteiroRepository: RoteiroRepository,
    private val formularioRepository: EntrevistaFormularioRepository,
    private val vagaRepository: VagaRepository,
    private val anexoRepository: AnexoRepository,
    private val entrevistaId: Long,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoEntrevista())
    val estado: StateFlow<EstadoEntrevista> = _estado.asStateFlow()

    private var cronometro: Job? = null

    init {
        viewModelScope.launch {
            val entrevista = entrevistaRepository.buscarPorId(entrevistaId)
            if (entrevista == null) {
                _estado.update {
                    it.copy(carregando = false, mensagem = "Entrevista não encontrada.")
                }
                return@launch
            }

            // A entrevista segue o roteiro próprio da vaga quando existe; caso
            // contrário, o roteiro padrão do processo.
            val roteiro = roteiroRepository.obterParaEntrevista(entrevista.vagaId)
            val respostas = formularioRepository.observarRespostas(entrevistaId)
            val experiencias = formularioRepository.observarExperiencias(entrevistaId)
            val vaga = entrevista.vagaId?.let { vagaRepository.buscarPorId(it) }

            _estado.update {
                it.copy(
                    carregando = false,
                    entrevista = entrevista,
                    roteiro = roteiro,
                    vaga = vaga,
                    respostas = entrevista.respostasRoteiro,
                    curriculo = entrevista.curriculo,
                    emAndamento = entrevista.status == StatusEntrevista.EM_ANDAMENTO,
                    respostasPorPergunta = respostas.first(),
                    experiencias = experiencias.first()
                        .takeIf { lista -> lista.isNotEmpty() }
                        ?.map { linha ->
                            LinhaExperiencia(
                                id = linha.id,
                                local = linha.local,
                                ano = linha.ano,
                                duracao = linha.duracao,
                                cargo = linha.cargo,
                                motivoSaida = linha.motivoSaida,
                            )
                        }
                        ?: listOf(LinhaExperiencia()),
                )
            }

            // As respostas e a tabela são observadas: o recrutador pode voltar
            // para a tela e continuar de onde parou, inclusive após fechar o app.
            viewModelScope.launch {
                respostas.collect { mapa -> _estado.update { it.copy(respostasPorPergunta = mapa) } }
            }
            viewModelScope.launch {
                experiencias.collect { lista ->
                    _estado.update { atual ->
                        if (lista.isEmpty() && atual.experiencias.size <= 1) return@update atual
                        atual.copy(
                            experiencias = lista.map {
                                LinhaExperiencia(
                                    id = it.id,
                                    local = it.local,
                                    ano = it.ano,
                                    duracao = it.duracao,
                                    cargo = it.cargo,
                                    motivoSaida = it.motivoSaida,
                                )
                            }
                        )
                    }
                }
            }

            if (entrevista.status == StatusEntrevista.EM_ANDAMENTO) {
                val inicio = entrevista.inicioReal ?: Instant.now()
                _estado.update {
                    it.copy(decorridoMs = (Instant.now().toEpochMilli() - inicio.toEpochMilli()).coerceAtLeast(0))
                }
                iniciarCronometro()
            }
        }
    }

    fun trocarAba(aba: AbaEntrevista) = _estado.update { it.copy(aba = aba) }

    /** Botão INICIAR: grava o horário real e liga o cronômetro. */
    fun iniciar() {
        val entrevista = _estado.value.entrevista ?: return
        if (_estado.value.emAndamento) return

        viewModelScope.launch {
            when (val resultado = entrevistaRepository.iniciar(entrevista.id)) {
                is EntrevistaRepository.Resultado.Erro ->
                    _estado.update { it.copy(mensagem = resultado.mensagem) }

                else -> {
                    _estado.update {
                        it.copy(
                            entrevista = entrevistaRepository.buscarPorId(entrevista.id),
                            emAndamento = true,
                            decorridoMs = 0L,
                            tempoExcedido = false,
                        )
                    }
                    iniciarCronometro()
                }
            }
        }
    }

    fun finalizar() {
        val entrevista = _estado.value.entrevista ?: return
        pararCronometro()
        viewModelScope.launch {
            when (val resultado = entrevistaRepository.finalizar(entrevista.id)) {
                is EntrevistaRepository.Resultado.Erro ->
                    _estado.update { it.copy(mensagem = resultado.mensagem) }

                else ->
                    _estado.update {
                        it.copy(
                            entrevista = entrevistaRepository.buscarPorId(entrevista.id),
                            emAndamento = false,
                        )
                    }
            }
        }
    }

    fun aoAlterarRespostas(valor: String) = _estado.update { it.copy(respostas = valor) }

    /** Aprova o candidato para a próxima fase, direto da tela da entrevista. */
    fun aprovar() = decidir("Candidato aprovado para a próxima fase.") {
        entrevistaRepository.aprovar(it)
    }

    /** Reprova o candidato na fase atual. */
    fun reprovar() = decidir("Candidato reprovado.") {
        entrevistaRepository.reprovar(it)
    }

    /** Encerra o candidato: some da lista do dia, mas o registro é preservado. */
    fun encerrar() = decidir("Candidato encerrado.") {
        entrevistaRepository.encerrar(it)
    }

    /**
     * Reabre um candidato encerrado ou já avaliado: volta a ficar agendado e
     * editável, para refazer a entrevista sem recriar o candidato.
     */
    fun reabrir() {
        val entrevista = _estado.value.entrevista ?: return
        viewModelScope.launch {
            _estado.update {
                when (val resultado = entrevistaRepository.reabrir(entrevista.id)) {
                    is EntrevistaRepository.Resultado.Erro -> it.copy(mensagem = resultado.mensagem)
                    else -> it.copy(
                        entrevista = entrevistaRepository.buscarPorId(entrevista.id),
                        emAndamento = false,
                        decorridoMs = 0L,
                        tempoExcedido = false,
                        mensagem = "Candidato reaberto. Pode ajustar as respostas e iniciar de novo.",
                    )
                }
            }
        }
    }

    private fun decidir(mensagemDeSucesso: String, bloco: suspend (Long) -> EntrevistaRepository.Resultado) {
        val entrevista = _estado.value.entrevista ?: return
        viewModelScope.launch {
            _estado.update {
                when (val resultado = bloco(entrevista.id)) {
                    is EntrevistaRepository.Resultado.Erro -> it.copy(mensagem = resultado.mensagem)
                    else -> it.copy(
                        entrevista = entrevistaRepository.buscarPorId(entrevista.id),
                        mensagem = mensagemDeSucesso,
                    )
                }
            }
        }
    }

    fun salvarRespostas() {
        val entrevista = _estado.value.entrevista ?: return
        viewModelScope.launch {
            entrevistaRepository.salvarRespostas(entrevista.id, _estado.value.respostas)
            _estado.update { it.copy(mensagem = "Roteiro salvo.") }
        }
    }

    fun aoAlterarCurriculo(valor: String) = _estado.update { it.copy(curriculo = valor) }

    fun editarCurriculo() = _estado.update { it.copy(curriculoEditando = true) }

    fun cancelarEdicaoCurriculo() = _estado.update {
        it.copy(curriculoEditando = false, curriculo = it.entrevista?.curriculo.orEmpty())
    }

    fun salvarCurriculo() {
        val entrevista = _estado.value.entrevista ?: return
        val novoTexto = _estado.value.curriculo
        viewModelScope.launch {
            entrevistaRepository.salvarCurriculo(entrevista.id, novoTexto)
            _estado.update {
                it.copy(
                    curriculoEditando = false,
                    entrevista = entrevistaRepository.buscarPorId(entrevista.id),
                    mensagem = "Currículo atualizado.",
                )
            }
        }
    }

    /** Anexa um PDF ou imagem ao currículo da entrevista. */
    fun anexarCurriculo(uri: Uri) {
        val entrevista = _estado.value.entrevista ?: return
        viewModelScope.launch {
            val tipo = when (anexoRepository.tipoDeArquivo(uri)) {
                TipoCurriculo.PDF -> TipoCurriculo.PDF
                else -> TipoCurriculo.IMAGEM
            }
            when (val resultado = anexoRepository.copiar(uri)) {
                is AnexoRepository.Resultado.Sucesso -> {
                    // O endereço do currículo no Firestore é o que fica gravado
                    // na entrevista; assim ele abre em qualquer aparelho (e no site).
                    val caminho = runCatching {
                        anexoRepository.enviarParaNuvem(resultado.caminho, resultado.nomeOriginal)
                    }.getOrDefault(resultado.caminho)
                    entrevistaRepository.salvarCurriculoAnexado(
                        entrevista.id,
                        tipo,
                        caminho,
                        resultado.nomeOriginal,
                    )
                    _estado.update {
                        it.copy(
                            entrevista = entrevistaRepository.buscarPorId(entrevista.id),
                            mensagem = "Currículo anexado.",
                        )
                    }
                }

                is AnexoRepository.Resultado.Erro ->
                    _estado.update { it.copy(mensagem = resultado.mensagem) }
            }
        }
    }

    fun consumirMensagem() = _estado.update { it.copy(mensagem = null) }

    // --- roteiro estruturado ---

    /**
     * Valor exibido no campo: o que o recrutador digitou ou, se ainda estiver
     * vazio, o preenchimento automático do app.
     */
    fun valorExibido(pergunta: Pergunta): String {
        val digitado = _estado.value.respostasPorPergunta[pergunta.id].orEmpty()
        if (digitado.isNotBlank()) return digitado
        return respostaAutomatica(pergunta)
    }

    fun aoAlterarResposta(pergunta: Pergunta, valor: String) {
        val entrevista = _estado.value.entrevista ?: return
        _estado.update { atual ->
            atual.copy(respostasPorPergunta = atual.respostasPorPergunta + (pergunta.id to valor))
        }
        viewModelScope.launch {
            formularioRepository.salvarResposta(entrevista.id, pergunta.id, valor)
        }
    }

    fun aoLimparResposta(pergunta: Pergunta) {
        val entrevista = _estado.value.entrevista ?: return
        _estado.update { atual ->
            atual.copy(respostasPorPergunta = atual.respostasPorPergunta - pergunta.id)
        }
        viewModelScope.launch {
            formularioRepository.limparResposta(entrevista.id, pergunta.id)
        }
    }

    /** O que o app escreve sozinho, sem o recrutador precisar digitar. */
    fun respostaAutomatica(pergunta: Pergunta): String {
        val atual = _estado.value
        return when (pergunta.respostaAutomatica) {
            RespostaAutomatica.DIA_E_HORA -> atual.diaEHora
            RespostaAutomatica.NOME_CANDIDATO -> atual.entrevista?.nome.orEmpty()
            RespostaAutomatica.TELEFONE_CANDIDATO ->
                com.example.entrevistador.core.formatarTelefone(atual.entrevista?.telefone.orEmpty())

            RespostaAutomatica.DADOS_DA_VAGA -> atual.vaga?.let(::textoDaVaga).orEmpty()
            else -> ""
        }
    }

    fun textoDaVaga(vaga: Vaga): String = listOfNotNull(
        vaga.empresa.takeIf { it.isNotBlank() }?.let { "Empresa: $it" },
        vaga.titulo.takeIf { it.isNotBlank() }?.let { "Vaga: $it" },
        vaga.horarioTrabalho.takeIf { it.isNotBlank() }?.let { "Horário de trabalho: $it" },
        vaga.tipoContrato.takeIf { it.isNotBlank() }?.let { "Tipo de contrato: $it" },
        vaga.salarioBeneficios.takeIf { it.isNotBlank() }?.let { "Salário e benefícios: $it" },
        vaga.tempoExperiencia.takeIf { it.isNotBlank() }?.let { "Tempo de experiência: $it" },
        vaga.escolaridade.takeIf { it.isNotBlank() }?.let { "Escolaridade: $it" },
        vaga.exigeHabilitacao.takeIf { it.isNotBlank() }?.let { "Habilitação: $it" },
        vaga.resumoAtividades.takeIf { it.isNotBlank() }?.let { "Atividades principais: $it" },
    ).joinToString("\n")

    // --- tabela de locais ---

    fun aoAlterarExperiencia(indice: Int, linha: LinhaExperiencia) {
        _estado.update { atual ->
            val copia = atual.experiencias.toMutableList()
            if (indice !in copia.indices) return@update atual
            copia[indice] = linha
            atual.copy(experiencias = copia)
        }
        persistirExperiencia(indice)
    }

    fun adicionarLinhaExperiencia() {
        _estado.update { it.copy(experiencias = it.experiencias + LinhaExperiencia()) }
    }

    fun removerLinhaExperiencia(indice: Int) {
        val entrevista = _estado.value.entrevista ?: return
        val linha = _estado.value.experiencias.getOrNull(indice) ?: return

        _estado.update { atual ->
            val copia = atual.experiencias.toMutableList()
            if (indice !in copia.indices) return@update atual
            copia.removeAt(indice)
            // Sempre sobra uma linha em branco: a tabela nunca fica sem linha.
            if (copia.isEmpty()) copia.add(LinhaExperiencia())
            atual.copy(experiencias = copia)
        }

        if (linha.id != 0L) {
            viewModelScope.launch {
                formularioRepository.removerExperiencia(
                    Experiencia(id = linha.id, entrevistaId = entrevista.id)
                )
            }
        }
    }

    /** Salva a linha; o id vem da própria linha, então editar e criar usam o mesmo caminho. */
    private fun persistirExperiencia(indice: Int) {
        val entrevista = _estado.value.entrevista ?: return
        val linha = _estado.value.experiencias.getOrNull(indice) ?: return
        viewModelScope.launch {
            val id = formularioRepository.salvarExperiencia(
                Experiencia(
                    id = linha.id,
                    entrevistaId = entrevista.id,
                    ordem = indice,
                    local = linha.local,
                    ano = linha.ano,
                    duracao = linha.duracao,
                    cargo = linha.cargo,
                    motivoSaida = linha.motivoSaida,
                )
            )
            // Guarda o id devolvido para que a próxima edição atualize a linha certa.
            if (linha.id == 0L && id != 0L) {
                _estado.update { atual ->
                    val copia = atual.experiencias.toMutableList()
                    if (indice in copia.indices) copia[indice] = copia[indice].copy(id = id)
                    atual.copy(experiencias = copia)
                }
            }
        }
    }

    private fun iniciarCronometro() {
        cronometro?.cancel()
        cronometro = viewModelScope.launch {
            while (true) {
                val duracaoMs = (_estado.value.duracaoPrevistaMinutos * 60_000L)
                _estado.update { atual ->
                    val proximo = atual.decorridoMs + 250
                    atual.copy(
                        decorridoMs = proximo,
                        tempoExcedido = duracaoMs in 1..proximo,
                    )
                }
                delay(250)
            }
        }
    }

    private fun pararCronometro() {
        cronometro?.cancel()
        cronometro = null
    }

    override fun onCleared() {
        pararCronometro()
    }
}
