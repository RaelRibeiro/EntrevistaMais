package com.example.entrevistador.core

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.entrevistador.domain.model.Entrevista
import com.example.entrevistador.domain.model.Experiencia
import com.example.entrevistador.domain.model.Roteiro
import com.example.entrevistador.domain.model.TipoResposta
import com.example.entrevistador.domain.model.Vaga
import java.io.File
import java.time.LocalDate

private const val LARGURA = 595
private const val ALTURA = 842
private const val MARGEM = 46f
private const val LARGURA_UTIL = LARGURA - 2 * MARGEM

object PdfExport {

    fun candidato(
        context: Context,
        entrevista: Entrevista,
        roteiro: Roteiro?,
        respostas: Map<Long, String>,
        experiencias: List<Experiencia>,
        vaga: Vaga?,
    ): File {
        val documento = Escritor()
        documento.titulo(entrevista.nome)
        if (entrevista.categoria.isNotBlank()) {
            documento.subtitulo("Categoria: ${entrevista.categoria}")
        }
        documento.subtitulo("Telefone: ${formatarTelefone(entrevista.telefone)}")
        documento.subtitulo(
            "Entrevista: ${entrevista.data.paraTextoPtBr()} · " +
                "${entrevista.inicioMinutos.minutosParaHora()} às ${entrevista.fimMinutos.minutosParaHora()}",
        )
        documento.subtitulo("Situação: ${entrevista.status.rotulo}")

        if (vaga != null) {
            documento.secao("Vaga")
            documento.paragrafo(vaga.resumoCurto())
            listOfNotNull(
                vaga.tipoContrato.takeIf { it.isNotBlank() }?.let { "Contrato: $it" },
                vaga.horarioTrabalho.takeIf { it.isNotBlank() }?.let { "Jornada: $it" },
                vaga.salarioBeneficios.takeIf { it.isNotBlank() }?.let { "Salário e benefícios: $it" },
                vaga.tempoExperiencia.takeIf { it.isNotBlank() }?.let { "Experiência: $it" },
                vaga.escolaridade.takeIf { it.isNotBlank() }?.let { "Escolaridade: $it" },
                vaga.exigeHabilitacao.takeIf { it.isNotBlank() }?.let { "Habilitação: $it" },
                vaga.resumoAtividades.takeIf { it.isNotBlank() }?.let { "Atividades: $it" },
            ).forEach { documento.paragrafo(it) }
        }

        documento.secao("Currículo")
        if (entrevista.curriculo.isBlank()) {
            documento.paragrafo("Sem resumo do currículo escrito.")
        } else {
            documento.paragrafo(entrevista.curriculo)
        }
        if (entrevista.nomeArquivoCurriculo.isNotBlank()) {
            documento.paragrafo("Arquivo anexado: ${entrevista.nomeArquivoCurriculo}")
        }

        val preenchidas = experiencias.filter { it.preenchida }
        if (preenchidas.isNotEmpty()) {
            documento.secao("Experiência profissional")
            documento.tabela(
                colunas = listOf(
                    "Local" to 2f,
                    "Ano" to 0.9f,
                    "Duração" to 1.1f,
                    "Cargo" to 1.4f,
                    "Motivo da saída" to 1.8f,
                ),
                linhas = preenchidas.map { item ->
                    listOf(item.local, item.ano, item.duracao, item.cargo, item.motivoSaida)
                },
            )
        }

        documento.secao(roteiro?.titulo ?: "Roteiro")
        val perguntas = roteiro?.perguntas.orEmpty()
            .filter { it.tipo != TipoResposta.SECAO && it.tipo != TipoResposta.TABELA_EXPERIENCIAS }
        val respondidas = perguntas.filter { !respostas[it.id].isNullOrBlank() }
        if (respondidas.isEmpty()) {
            documento.paragrafo("Nenhuma resposta registrada neste roteiro.")
        } else {
            respondidas.forEach { pergunta ->
                documento.pergunta(pergunta.titulo)
                documento.resposta(respostas[pergunta.id].orEmpty())
            }
        }

        return finalizar(context, documento, "curriculo-${slug(entrevista.nome)}.pdf")
    }

    fun agenda(
        context: Context,
        data: LocalDate,
        entrevistas: List<Entrevista>,
        vagas: List<Vaga>,
    ): File {
        val documento = Escritor()
        documento.titulo("Agenda de entrevistas")
        documento.subtitulo(data.paraTituloAgenda())
        documento.subtitulo("${entrevistas.size} candidato(s) neste dia")

        if (entrevistas.isEmpty()) {
            documento.paragrafo("Nenhuma entrevista neste dia.")
        } else {
            val porId = vagas.associateBy { it.id }
            entrevistas.sortedBy { it.inicioMinutos }.forEach { item ->
                documento.secao(
                    "${item.inicioMinutos.minutosParaHora()} · ${item.nome}",
                )
                documento.paragrafo(
                    listOfNotNull(
                        item.categoria.takeIf { it.isNotBlank() }?.let { "Categoria: $it" },
                        item.telefone.takeIf { it.isNotBlank() }?.let { "Telefone: ${formatarTelefone(it)}" },
                        item.vagaId?.let { porId[it]?.titulo }?.let { "Vaga: $it" },
                        "Situação: ${item.status.rotulo}",
                    ).joinToString("\n"),
                )
            }
        }

        return finalizar(context, documento, "agenda-${data}.pdf")
    }

    fun compartilhar(context: Context, arquivo: File, titulo: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", arquivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, titulo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, titulo))
    }

    private fun finalizar(context: Context, documento: Escritor, nome: String): File {
        val pasta = File(context.cacheDir, "exportacoes").apply { mkdirs() }
        val arquivo = File(pasta, nome)
        arquivo.outputStream().use { documento.fechar().writeTo(it) }
        return arquivo
    }

    private fun slug(valor: String): String =
        valor.trim().lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "candidato" }
}

private fun LocalDate.paraTextoPtBr(): String = "$dayOfMonth/$monthValue/$year"

private class Escritor {
    private val documento = PdfDocument()
    private var pagina: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = MARGEM
    private var numero = 0

    private val pincelTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 19f
        isFakeBoldText = true
        color = Color.rgb(20, 33, 61)
    }
    private val pincelSecao = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12.5f
        isFakeBoldText = true
        color = Color.rgb(20, 33, 61)
    }
    private val pincelCorpo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f
        color = Color.rgb(40, 40, 40)
    }
    private val pincelSuave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = Color.rgb(95, 107, 122)
    }
    private val pincelPergunta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f
        isFakeBoldText = true
        color = Color.rgb(40, 40, 40)
    }
    private val pincelLinha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.7f
        color = Color.rgb(200, 200, 200)
    }
    private val pincelLinhaForte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.9f
        color = Color.rgb(130, 130, 130)
    }
    private val pincelTabelaCabecalho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9.5f
        isFakeBoldText = true
        color = Color.rgb(20, 33, 61)
    }
    private val pincelTabela = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9.5f
        color = Color.rgb(40, 40, 40)
    }

    init {
        abrirPagina()
    }

    private fun abrirPagina() {
        pagina?.let { documento.finishPage(it) }
        numero++
        val info = PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create()
        pagina = documento.startPage(info)
        canvas = pagina?.canvas
        y = MARGEM
    }

    fun fechar(): PdfDocument {
        pagina?.let { documento.finishPage(it) }
        pagina = null
        return documento
    }

    private fun caber(altura: Float): Boolean = y + altura <= ALTURA - MARGEM

    fun titulo(texto: String) {
        if (!caber(30f)) abrirPagina()
        canvas?.drawText(texto, MARGEM, y, pincelTitulo)
        y += 27f
    }

    fun subtitulo(texto: String) {
        quebrar(texto, pincelSuave, 14f)
    }

    fun secao(texto: String) {
        if (!caber(42f)) abrirPagina()
        y += 12f
        canvas?.drawText(texto, MARGEM, y, pincelSecao)
        y += 7f
        canvas?.drawLine(MARGEM, y, LARGURA - MARGEM, y, pincelLinha)
        y += 15f
    }

    fun pergunta(texto: String) {
        if (!caber(30f)) abrirPagina()
        y += 6f
        quebrar(texto, pincelPergunta, 14f)
    }

    fun resposta(texto: String) {
        quebrar(texto, pincelCorpo, 14f)
        y += 8f
    }

    fun paragrafo(texto: String) {
        quebrar(texto, pincelCorpo, 14f)
        y += 3f
    }

    fun tabela(colunas: List<Pair<String, Float>>, linhas: List<List<String>>) {
        val espacoCelula = 5f
        val alturaLinha = 12f
        val somaPesos = colunas.sumOf { it.second.toDouble() }.toFloat()
        val larguras = colunas.map { LARGURA_UTIL * it.second / somaPesos }
        val limites = larguras.runningFold(MARGEM) { total, largura -> total + largura }

        fun desenharCabecalho() {
            val quebradas = colunas.mapIndexed { indice, (rotulo, _) ->
                encolher(rotulo, pincelTabelaCabecalho, larguras[indice] - espacoCelula)
            }
            val altura = quebradas.maxOf { it.size } * alturaLinha + espacoCelula * 2
            if (!caber(altura)) abrirPagina()
            val topo = y
            quebradas.forEachIndexed { indice, linhasDaCelula ->
                linhasDaCelula.forEachIndexed { indiceLinha, textoDaCelula ->
                    canvas?.drawText(
                        textoDaCelula,
                        limites[indice] + espacoCelula / 2,
                        topo + espacoCelula + alturaLinha * (indiceLinha + 1) - 3f,
                        pincelTabelaCabecalho,
                    )
                }
            }
            y = topo + altura
            canvas?.drawLine(MARGEM, y, MARGEM + LARGURA_UTIL, y, pincelLinhaForte)
        }

        if (linhas.isEmpty()) return
        desenharCabecalho()

        linhas.forEach { celulas ->
            val quebradas = celulas.mapIndexed { indice, valor ->
                encolher(valor, pincelTabela, larguras[indice] - espacoCelula)
            }
            val altura = quebradas.maxOf { it.size } * alturaLinha + espacoCelula * 2
            if (!caber(altura)) {
                abrirPagina()
                desenharCabecalho()
            }
            val topo = y
            quebradas.forEachIndexed { indice, linhasDaCelula ->
                linhasDaCelula.forEachIndexed { indiceLinha, textoDaCelula ->
                    canvas?.drawText(
                        textoDaCelula,
                        limites[indice] + espacoCelula / 2,
                        topo + espacoCelula + alturaLinha * (indiceLinha + 1) - 3f,
                        pincelTabela,
                    )
                }
            }
            y = topo + altura
            canvas?.drawLine(MARGEM, y, MARGEM + LARGURA_UTIL, y, pincelLinha)
        }
    }

    private fun encolher(texto: String, pincel: Paint, largura: Float): List<String> {
        if (texto.isBlank()) return listOf("")
        val linhas = mutableListOf<String>()
        var atual = StringBuilder()
        texto.split(' ').forEach { palavra ->
            val tentativa = if (atual.isEmpty()) palavra else "$atual $palavra"
            if (pincel.measureText(tentativa) <= largura) {
                atual = StringBuilder(tentativa)
            } else {
                if (atual.isNotEmpty()) linhas.add(atual.toString())
                atual = StringBuilder(palavra)
            }
        }
        if (atual.isNotEmpty()) linhas.add(atual.toString())
        return linhas
    }

    private fun quebrar(texto: String, pincel: Paint, alturaLinha: Float) {
        texto.split('\n').forEach { linhaOriginal ->
            if (linhaOriginal.isBlank()) {
                y += alturaLinha / 2
                return@forEach
            }
            var atual = StringBuilder()
            linhaOriginal.split(' ').forEach { palavra ->
                val tentativa = if (atual.isEmpty()) palavra else "$atual $palavra"
                if (pincel.measureText(tentativa) <= LARGURA_UTIL) {
                    atual = StringBuilder(tentativa)
                } else {
                    desenhar(atual.toString(), pincel, alturaLinha)
                    atual = StringBuilder(palavra)
                }
            }
            if (atual.isNotEmpty()) desenhar(atual.toString(), pincel, alturaLinha)
        }
    }

    private fun desenhar(linha: String, pincel: Paint, alturaLinha: Float) {
        if (!caber(alturaLinha)) abrirPagina()
        canvas?.drawText(linha, MARGEM, y, pincel)
        y += alturaLinha
    }
}
