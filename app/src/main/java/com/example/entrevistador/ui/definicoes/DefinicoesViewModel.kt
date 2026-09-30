package com.example.entrevistador.ui.definicoes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entrevistador.core.minutosParaHora
import com.example.entrevistador.data.repository.DefinicoesRepository
import com.example.entrevistador.data.repository.EntrevistaRepository
import com.example.entrevistador.domain.AgendaScheduler
import com.example.entrevistador.domain.model.DefinicoesPadrao
import com.example.entrevistador.domain.model.ErrosDefinicoes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDefinicoes(
    val carregando: Boolean = true,
    val horarioInicio: Int = DefinicoesPadrao.PADRAO.horarioInicioMinutos,
    val almocoInicio: Int = DefinicoesPadrao.PADRAO.almocoInicioMinutos,
    val almocoFim: Int = DefinicoesPadrao.PADRAO.almocoFimMinutos,
    val quantidade: String = DefinicoesPadrao.PADRAO.quantidadePorDia.toString(),
    val duracao: String = DefinicoesPadrao.PADRAO.duracaoMinutos.toString(),
    val intervalo: String = DefinicoesPadrao.PADRAO.intervaloMinutos.toString(),
    val erros: ErrosDefinicoes = ErrosDefinicoes(),
    val mensagem: String? = null,
    val salvando: Boolean = false,
) {
    val previa: String
        get() {
            val modelo = paraDefinicoes() ?: return "Preencha os campos para ver a prévia da agenda."
            val horarios = AgendaScheduler.calcularHorarios(
                modelo,
                (0 until modelo.quantidadePorDia).map { AgendaScheduler.Candidato(it.toLong(), "") },
            )
            val ultimo = horarios.lastOrNull() ?: return "Sem entrevistas neste dia."
            return "${horarios.size} entrevista(s), das ${modelo.horarioInicioMinutos.minutosParaHora()} " +
                "às ${ultimo.fimMinutos.minutosParaHora()}."
        }

    fun paraDefinicoes(): DefinicoesPadrao? {
        val qtd = quantidade.toIntOrNull() ?: return null
        val dur = duracao.toIntOrNull() ?: return null
        val inter = intervalo.toIntOrNull() ?: return null
        return DefinicoesPadrao(
            horarioInicioMinutos = horarioInicio,
            almocoInicioMinutos = almocoInicio,
            almocoFimMinutos = almocoFim,
            quantidadePorDia = qtd,
            duracaoMinutos = dur,
            intervaloMinutos = inter,
        )
    }
}

class DefinicoesViewModel(
    private val definicoesRepository: DefinicoesRepository,
    private val entrevistaRepository: EntrevistaRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDefinicoes())
    val estado: StateFlow<EstadoDefinicoes> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val atuais = definicoesRepository.definicoes.first()
            _estado.value = EstadoDefinicoes(
                carregando = false,
                horarioInicio = atuais.horarioInicioMinutos,
                almocoInicio = atuais.almocoInicioMinutos,
                almocoFim = atuais.almocoFimMinutos,
                quantidade = atuais.quantidadePorDia.toString(),
                duracao = atuais.duracaoMinutos.toString(),
                intervalo = atuais.intervaloMinutos.toString(),
            )
        }
    }

    fun definirHorarioInicio(minutos: Int) =
        _estado.update { it.copy(horarioInicio = minutos, erros = it.erros.copy(horarioInicio = null)) }

    fun definirAlmocoInicio(minutos: Int) =
        _estado.update { it.copy(almocoInicio = minutos, erros = it.erros.copy(almocoInicio = null)) }

    fun definirAlmocoFim(minutos: Int) =
        _estado.update { it.copy(almocoFim = minutos, erros = it.erros.copy(almocoFim = null)) }

    fun definirQuantidade(valor: String) = _estado.update {
        it.copy(quantidade = valor.filter(Char::isDigit).take(2), erros = it.erros.copy(quantidade = null))
    }

    fun definirDuracao(valor: String) = _estado.update {
        it.copy(duracao = valor.filter(Char::isDigit).take(3), erros = it.erros.copy(duracao = null))
    }

    fun definirIntervalo(valor: String) = _estado.update {
        it.copy(intervalo = valor.filter(Char::isDigit).take(3), erros = it.erros.copy(intervalo = null))
    }

    fun restaurarPadroes() {
        viewModelScope.launch { salvar(DefinicoesPadrao.PADRAO, avisar = "Valores padrão restaurados.") }
    }

    fun salvar() {
        val atual = _estado.value
        val modelo = atual.paraDefinicoes()
        if (modelo == null) {
            _estado.update { it.copy(erros = it.erros.copy(quantidade = "Use apenas números.")) }
            return
        }

        val erros = validar(modelo)
        if (erros.possuiErros) {
            _estado.update { it.copy(erros = erros) }
            return
        }

        viewModelScope.launch { salvar(modelo, avisar = "Definições salvas e agenda reorganizada.") }
    }

    private suspend fun salvar(definicoes: DefinicoesPadrao, avisar: String) {
        _estado.update { it.copy(salvando = true) }
        definicoesRepository.salvar(definicoes)
        reaplicarAgenda(definicoes)
        _estado.update { it.copy(salvando = false, mensagem = avisar) }
    }

    /** Reaplica os horários dos dias que já têm entrevistas. */
    private suspend fun reaplicarAgenda(definicoes: DefinicoesPadrao) {
        // Percorre de hoje até 60 dias à frente: cobre a agenda realisticamente
        // usada e mantém a operação barata.
        for (offset in 0..60) {
            entrevistaRepository.reaplicarDefinicoes(java.time.LocalDate.now().plusDays(offset.toLong()))
        }
    }

    fun consumirMensagem() = _estado.update { it.copy(mensagem = null) }

    private fun validar(d: DefinicoesPadrao): ErrosDefinicoes = ErrosDefinicoes(
        almocoInicio = if (d.almocoFimMinutos <= d.almocoInicioMinutos) {
            "O almoço precisa ter duração positiva."
        } else {
            null
        },
        almocoFim = if (d.almocoFimMinutos <= d.almocoInicioMinutos) "Fim do almoço inválido." else null,
        quantidade = if (d.quantidadePorDia !in DefinicoesPadrao.QUANTIDADE_MIN..DefinicoesPadrao.QUANTIDADE_MAX) {
            "Entre ${DefinicoesPadrao.QUANTIDADE_MIN} e ${DefinicoesPadrao.QUANTIDADE_MAX}."
        } else {
            null
        },
        duracao = if (d.duracaoMinutos !in DefinicoesPadrao.DURACAO_MIN..DefinicoesPadrao.DURACAO_MAX) {
            "Entre ${DefinicoesPadrao.DURACAO_MIN} e ${DefinicoesPadrao.DURACAO_MAX} minutos."
        } else {
            null
        },
        intervalo = if (d.intervaloMinutos < 0 || d.intervaloMinutos > DefinicoesPadrao.INTERVALO_MAX) {
            "Entre 0 e ${DefinicoesPadrao.INTERVALO_MAX} minutos."
        } else {
            null
        },
    )
}
