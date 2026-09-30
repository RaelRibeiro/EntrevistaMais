package com.example.entrevistador.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.entrevistador.domain.model.Experiencia
import com.example.entrevistador.domain.model.Pergunta
import com.example.entrevistador.domain.model.RespostaAutomatica
import com.example.entrevistador.domain.model.TipoCurriculo
import com.example.entrevistador.domain.model.TipoResposta
import com.example.entrevistador.domain.model.Vaga
import com.example.entrevistador.domain.model.Roteiro
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "entrevistas",
    indices = [Index("data"), Index(value = ["data", "ordem"])],
)
data class EntrevistaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val data: LocalDate,
    val inicioMinutos: Int,
    val duracaoMinutos: Int,
    val nome: String,
    val telefone: String,
    val curriculo: String = "",
    val status: com.example.entrevistador.domain.model.StatusEntrevista =
        com.example.entrevistador.domain.model.StatusEntrevista.AGENDADA,
    val ordem: Int = 0,
    val inicioReal: Instant? = null,
    val fimReal: Instant? = null,
    @ColumnInfo(defaultValue = "") val respostasRoteiro: String = "",
    @ColumnInfo(defaultValue = "") val notas: String = "",
    // --- currículo ---
    @ColumnInfo(defaultValue = "RESUMO") val tipoCurriculo: TipoCurriculo = TipoCurriculo.RESUMO,
    /** Caminho do arquivo copiado para o armazenamento interno do app. */
    @ColumnInfo(defaultValue = "") val caminhoCurriculo: String = "",
    /** Nome original do arquivo, para exibir. */
    @ColumnInfo(defaultValue = "") val nomeArquivoCurriculo: String = "",
    /** Vaga oferecida nesta entrevista (opcional). */
    @ColumnInfo(defaultValue = "") val vagaId: Long? = null,
    /**
     * O recrutador moveu este horário na mão.
     *
     * As Definições são o padrão, mas a Reality é que às vezes o candidato
     * chega adiantado ou atrasado. Com esta marca, o recálculo automático
     * (quando uma definição muda, alguém entra ou sai do dia) respeita o
     * horário escolhido em vez de sobrescrever ele.
     */
    @ColumnInfo(defaultValue = "0") val inicioManual: Boolean = false,
)

fun EntrevistaEntity.paraDominio(): com.example.entrevistador.domain.model.Entrevista =
    com.example.entrevistador.domain.model.Entrevista(
        id = id,
        data = data,
        inicioMinutos = inicioMinutos,
        duracaoMinutos = duracaoMinutos,
        nome = nome,
        telefone = telefone,
        curriculo = curriculo,
        status = status,
        ordem = ordem,
        inicioReal = inicioReal,
        fimReal = fimReal,
        respostasRoteiro = respostasRoteiro,
        notas = notas,
        tipoCurriculo = tipoCurriculo,
        caminhoCurriculo = caminhoCurriculo,
        nomeArquivoCurriculo = nomeArquivoCurriculo,
        vagaId = vagaId,
        inicioManual = inicioManual,
    )

fun com.example.entrevistador.domain.model.Entrevista.paraEntidade(): EntrevistaEntity =
    EntrevistaEntity(
        id = id,
        data = data,
        inicioMinutos = inicioMinutos,
        duracaoMinutos = duracaoMinutos,
        nome = nome,
        telefone = telefone,
        curriculo = curriculo,
        status = status,
        ordem = ordem,
        inicioReal = inicioReal,
        fimReal = fimReal,
        respostasRoteiro = respostasRoteiro,
        notas = notas,
        tipoCurriculo = tipoCurriculo,
        caminhoCurriculo = caminhoCurriculo,
        nomeArquivoCurriculo = nomeArquivoCurriculo,
        vagaId = vagaId,
        inicioManual = inicioManual,
    )

@Entity(tableName = "perguntas")
data class PerguntaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val roteiroId: Long,
    val ordem: Int,
    val titulo: String,
    val tipo: TipoResposta = TipoResposta.TEXTO,
    val obrigatoria: Boolean = false,
    val dica: String = "",
    val respostaAutomatica: RespostaAutomatica? = null,
)

fun PerguntaEntity.paraDominio(): Pergunta = Pergunta(
    id = id,
    roteiroId = roteiroId,
    ordem = ordem,
    titulo = titulo,
    tipo = tipo,
    obrigatoria = obrigatoria,
    dica = dica,
    respostaAutomatica = respostaAutomatica,
)

fun Pergunta.paraEntidade(): PerguntaEntity = PerguntaEntity(
    id = id,
    roteiroId = roteiroId,
    ordem = ordem,
    titulo = titulo,
    tipo = tipo,
    obrigatoria = obrigatoria,
    dica = dica,
    respostaAutomatica = respostaAutomatica,
)

@Entity(
    tableName = "respostas",
    primaryKeys = ["entrevistaId", "perguntaId"],
)
data class RespostaEntity(
    val entrevistaId: Long,
    val perguntaId: Long,
    val texto: String,
)

@Entity(
    tableName = "experiencias",
    indices = [Index("entrevistaId")],
)
data class ExperienciaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entrevistaId: Long,
    val ordem: Int = 0,
    val local: String = "",
    val ano: String = "",
    val duracao: String = "",
    val cargo: String = "",
    val motivoSaida: String = "",
)

fun ExperienciaEntity.paraDominio(): Experiencia = Experiencia(
    id = id,
    entrevistaId = entrevistaId,
    ordem = ordem,
    local = local,
    ano = ano,
    duracao = duracao,
    cargo = cargo,
    motivoSaida = motivoSaida,
)

fun Experiencia.paraEntidade(): ExperienciaEntity = ExperienciaEntity(
    id = id,
    entrevistaId = entrevistaId,
    ordem = ordem,
    local = local,
    ano = ano,
    duracao = duracao,
    cargo = cargo,
    motivoSaida = motivoSaida,
)

@Entity(tableName = "vagas")
data class VagaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val titulo: String,
    // Os defaultValue são obrigatórios: a migração adiciona as colunas com
    // DEFAULT, e o Room compara esse default com o declarado aqui.
    @ColumnInfo(defaultValue = "") val empresa: String = "",
    /** Entrada e saída em minutos desde a meia-noite. */
    @ColumnInfo(defaultValue = "-1") val entradaMinutos: Int = Vaga.SEM_HORARIO,
    @ColumnInfo(defaultValue = "-1") val saidaMinutos: Int = Vaga.SEM_HORARIO,
    @ColumnInfo(defaultValue = "") val tipoContrato: String = "",
    val salarioBeneficios: String = "",
    val tempoExperiencia: String = "",
    val escolaridade: String = "",
    val exigeHabilitacao: String = "",
    val resumoAtividades: String = "",
    @ColumnInfo(defaultValue = "0") val limiteCandidatos: Int = 0,
    val ativa: Boolean = true,
)

fun VagaEntity.paraDominio(): Vaga = Vaga(
    id = id,
    titulo = titulo,
    empresa = empresa,
    entradaMinutos = entradaMinutos,
    saidaMinutos = saidaMinutos,
    tipoContrato = tipoContrato,
    salarioBeneficios = salarioBeneficios,
    tempoExperiencia = tempoExperiencia,
    escolaridade = escolaridade,
    exigeHabilitacao = exigeHabilitacao,
    resumoAtividades = resumoAtividades,
    limiteCandidatos = limiteCandidatos,
    ativa = ativa,
)

fun Vaga.paraEntidade(): VagaEntity = VagaEntity(
    id = id,
    titulo = titulo,
    empresa = empresa,
    entradaMinutos = entradaMinutos,
    saidaMinutos = saidaMinutos,
    tipoContrato = tipoContrato,
    salarioBeneficios = salarioBeneficios,
    tempoExperiencia = tempoExperiencia,
    escolaridade = escolaridade,
    exigeHabilitacao = exigeHabilitacao,
    resumoAtividades = resumoAtividades,
    limiteCandidatos = limiteCandidatos,
    ativa = ativa,
)

@Entity(
    tableName = "roteiros",
    indices = [Index("vagaId")],
)
data class RoteiroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val titulo: String,
    val conteudo: String,
    val ordem: Int = 0,
    val padrao: Boolean = true,
    /**
     * Roteiro próprio de uma vaga.
     *
     * Nulo é o roteiro padrão do processo, que serve de base para todos. Com o
     * id preenchido, o roteiro vale só para aquela vaga e não afeta as demais.
     */
    val vagaId: Long? = null,
)

fun RoteiroEntity.paraDominio(): Roteiro = Roteiro(
    id = id,
    titulo = titulo,
    conteudo = conteudo,
    ordem = ordem,
    padrao = padrao,
    vagaId = vagaId,
)

fun Roteiro.paraEntidade(): RoteiroEntity = RoteiroEntity(
    id = id,
    titulo = titulo,
    conteudo = conteudo,
    ordem = ordem,
    padrao = padrao,
    vagaId = vagaId,
)
