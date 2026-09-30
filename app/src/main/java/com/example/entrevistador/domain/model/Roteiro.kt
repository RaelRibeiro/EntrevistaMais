package com.example.entrevistador.domain.model

/** Como o currículo do candidato foi registrado. */
enum class TipoCurriculo(val rotulo: String) {
    RESUMO("Resumo"),
    PDF("PDF"),
    IMAGEM("Imagem"),
    ;

    companion object {
        fun from(valor: String?): TipoCurriculo =
            entries.firstOrNull { it.name == valor } ?: RESUMO
    }
}

/** Roteiro de entrevista: uma lista ordenada de [Pergunta]. */
data class Roteiro(
    val id: Long = 0L,
    val titulo: String,
    val conteudo: String = "",
    val ordem: Int = 0,
    val padrao: Boolean = true,
    /**
     * Roteiro próprio de uma vaga.
     *
     * Nulo = o roteiro padrão do processo, que vale para toda entrevista sem
     * roteiro próprio. Com o id preenchido, vale apenas para aquela vaga.
     */
    val vagaId: Long? = null,
    val perguntas: List<Pergunta> = emptyList(),
) {
    /** Roteiro padrão é o que serve de base para as vagas. */
    val ehPadraoDoProcesso: Boolean get() = vagaId == null
}

/**
 * Como o campo de uma pergunta se comporta na tela.
 */
enum class TipoResposta(val rotulo: String) {
    TEXTO("Texto"),
    TEXTO_LONGO("Texto longo"),
    NUMERO("Número"),
    SIM_NAO("Sim/Não"),
    DATA("Data"),
    AUTOMATICO("Automático"),
    TABELA_EXPERIENCIAS("Tabela de locais"),
    SECAO("Seção"),
}

/**
 * Uma pergunta do roteiro.
 *
 * [respostaAutomatica] diz o que preencher sozinho: dia/hora da entrevista,
 * nome do candidato, telefone e os dados da vaga. Assim o recrutador não digita
 * de novo o que o app já sabe.
 */
data class Pergunta(
    val id: Long = 0L,
    val roteiroId: Long = 0L,
    val ordem: Int = 0,
    val titulo: String,
    val tipo: TipoResposta = TipoResposta.TEXTO,
    val obrigatoria: Boolean = false,
    val dica: String = "",
    val respostaAutomatica: RespostaAutomatica? = null,
)

/** Valores que o app preenche sozinho, sem o recrutador digitar. */
enum class RespostaAutomatica(val rotulo: String) {
    DIA_E_HORA("Dia e hora da entrevista"),
    NOME_CANDIDATO("Nome do candidato"),
    TELEFONE_CANDIDATO("Telefone do candidato"),
    DADOS_DA_VAGA("Informações da vaga"),
    NENHUMA("Preencher manualmente"),
}

/** Uma linha da tabela "locais onde trabalhou". */
data class Experiencia(
    val id: Long = 0L,
    val entrevistaId: Long = 0L,
    val ordem: Int = 0,
    val local: String = "",
    val ano: String = "",
    val duracao: String = "",
    val cargo: String = "",
    val motivoSaida: String = "",
) {
    val preenchida: Boolean
        get() = listOf(local, ano, duracao, cargo, motivoSaida).any { it.isNotBlank() }
}

/** Resposta de uma pergunta, guardada por entrevista. */
data class Resposta(
    val perguntaId: Long,
    val texto: String,
)

/** Uma vaga cadastrada — as informações que o roteiro exibe ao candidato. */
data class Vaga(
    val id: Long = 0L,
    val titulo: String,
    val empresa: String = "",
    /** Entrada e saída em minutos desde a meia-noite; -1 quando não preenchido. */
    val entradaMinutos: Int = SEM_HORARIO,
    val saidaMinutos: Int = SEM_HORARIO,
    val tipoContrato: String = "",
    val salarioBeneficios: String = "",
    val tempoExperiencia: String = "",
    val escolaridade: String = "",
    val exigeHabilitacao: String = "",
    val resumoAtividades: String = "",
    /** Quantos candidatos a vaga aceita; 0 = sem limite definido. */
    val limiteCandidatos: Int = 0,
    val ativa: Boolean = true,
) {
    /** "Horário de trabalho: 08:00 às 17:00", montado a partir do relógio. */
    val horarioTrabalho: String
        get() = when {
            entradaMinutos != SEM_HORARIO && saidaMinutos != SEM_HORARIO ->
                "${minutosParaTexto(entradaMinutos)} às ${minutosParaTexto(saidaMinutos)}"
            else -> ""
        }

    /** "Horário: 08:00 às 17:00 · Salário: R$ 2.000". */
    fun resumoCurto(): String = listOfNotNull(
        titulo.takeIf { it.isNotBlank() },
        empresa.takeIf { it.isNotBlank() }?.let { "Empresa: $it" },
        horarioTrabalho.takeIf { it.isNotBlank() }?.let { "Horário: $it" },
        salarioBeneficios.takeIf { it.isNotBlank() }?.let { "Salário: $it" },
    ).joinToString(" · ")

    companion object {
        /** Marca "o usuário ainda não escolheu um horário". */
        const val SEM_HORARIO = -1

        private fun minutosParaTexto(minutos: Int): String =
            "%02d:%02d".format(minutos / 60, minutos % 60)
    }
}
