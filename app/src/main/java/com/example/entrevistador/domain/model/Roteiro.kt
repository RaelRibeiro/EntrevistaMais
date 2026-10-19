package com.example.entrevistador.domain.model

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

data class Roteiro(
    val id: Long = 0L,
    val titulo: String,
    val conteudo: String = "",
    val ordem: Int = 0,
    val padrao: Boolean = true,

    val vagaId: Long? = null,
    val perguntas: List<Pergunta> = emptyList(),
) {

    val ehPadraoDoProcesso: Boolean get() = vagaId == null
}

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

enum class RespostaAutomatica(val rotulo: String) {
    DIA_E_HORA("Dia e hora da entrevista"),
    NOME_CANDIDATO("Nome do candidato"),
    TELEFONE_CANDIDATO("Telefone do candidato"),
    DADOS_DA_VAGA("Informações da vaga"),
    NENHUMA("Preencher manualmente"),
}

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

data class Resposta(
    val perguntaId: Long,
    val texto: String,
)

data class Vaga(
    val id: Long = 0L,
    val titulo: String,
    val empresa: String = "",

    val entradaMinutos: Int = SEM_HORARIO,
    val saidaMinutos: Int = SEM_HORARIO,
    val tipoContrato: String = "",
    val salarioBeneficios: String = "",
    val tempoExperiencia: String = "",
    val escolaridade: String = "",
    val exigeHabilitacao: String = "",
    val resumoAtividades: String = "",

    val limiteCandidatos: Int = 0,
    val ativa: Boolean = true,
) {

    val horarioTrabalho: String
        get() = when {
            entradaMinutos != SEM_HORARIO && saidaMinutos != SEM_HORARIO ->
                "${minutosParaTexto(entradaMinutos)} às ${minutosParaTexto(saidaMinutos)}"
            else -> ""
        }

    fun resumoCurto(): String = listOfNotNull(
        titulo.takeIf { it.isNotBlank() },
        empresa.takeIf { it.isNotBlank() }?.let { "Empresa: $it" },
        horarioTrabalho.takeIf { it.isNotBlank() }?.let { "Horário: $it" },
        salarioBeneficios.takeIf { it.isNotBlank() }?.let { "Salário: $it" },
    ).joinToString(" · ")

    companion object {

        const val SEM_HORARIO = -1

        private fun minutosParaTexto(minutos: Int): String =
            "%02d:%02d".format(minutos / 60, minutos % 60)
    }
}
