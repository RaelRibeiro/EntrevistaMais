package com.example.entrevistador.data.repository

import com.example.entrevistador.data.local.dao.ExperienciaDao
import com.example.entrevistador.data.local.dao.PerguntaDao
import com.example.entrevistador.data.local.dao.RespostaDao
import com.example.entrevistador.data.local.dao.RoteiroDao
import com.example.entrevistador.data.local.dao.VagaDao
import com.example.entrevistador.data.local.entity.ExperienciaEntity
import com.example.entrevistador.data.local.entity.PerguntaEntity
import com.example.entrevistador.data.local.entity.RespostaEntity
import com.example.entrevistador.data.local.entity.RoteiroEntity
import com.example.entrevistador.data.local.entity.VagaEntity
import com.example.entrevistador.data.local.entity.paraDominio
import com.example.entrevistador.data.local.entity.paraEntidade
import com.example.entrevistador.domain.model.Experiencia
import com.example.entrevistador.domain.model.Pergunta
import com.example.entrevistador.domain.model.RespostaAutomatica
import com.example.entrevistador.domain.model.Roteiro
import com.example.entrevistador.domain.model.TipoResposta
import com.example.entrevistador.domain.model.Vaga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Roteiro = uma lista ordenada de perguntas.
 *
 * Guardar como texto livre não funciona para o recrutador: ele precisa pular de
 * pergunta em pergunta durante a chamada e volver para o que já respondeu. Por
 * isso o roteiro é uma lista de [Pergunta] e cada resposta é salva por
 * (entrevista, pergunta) — dá para retomar depois.
 */
class RoteiroRepository(
    private val dao: RoteiroDao,
    private val perguntaDao: PerguntaDao,
) {

    fun observarTodos(): Flow<List<Roteiro>> =
        combine(dao.observarTodos(), perguntaDao.observarTodos()) { roteiros, perguntas ->
            val porRoteiro = perguntas.groupBy { it.roteiroId }
            roteiros.map { roteiro ->
                roteiro.paraDominio().copy(perguntas = porRoteiro[roteiro.id].orEmpty().map { it.paraDominio() })
            }
        }

    fun observarPadrao(): Flow<Roteiro?> = combine(
        dao.observarPadrao(),
        perguntaDao.observarTodos(),
    ) { roteiro, perguntas ->
        roteiro?.paraDominio()?.copy(
            perguntas = perguntas.filter { it.roteiroId == roteiro.id }.map { it.paraDominio() }
        )
    }

    suspend fun obterPadrao(): Roteiro? {
        val roteiro = dao.buscarPadrao() ?: return null
        return roteiro.paraDominio().copy(
            perguntas = perguntaDao.listarDoRoteiro(roteiro.id).map { it.paraDominio() }
        )
    }

    suspend fun salvar(roteiro: Roteiro): Long {
        val id = if (roteiro.id == 0L) {
            dao.inserir(roteiro.paraEntidade())
        } else {
            dao.atualizar(roteiro.paraEntidade())
            roteiro.id
        }
        return id
    }

    suspend fun salvarPerguntas(roteiroId: Long, perguntas: List<Pergunta>) {
        perguntaDao.excluirDoRoteiro(roteiroId)
        if (perguntas.isEmpty()) return
        perguntaDao.inserirTodas(perguntas.map { it.paraEntidade().copy(roteiroId = roteiroId) })
    }

    suspend fun excluir(id: Long) {
        perguntaDao.excluirDoRoteiro(id)
        dao.excluirPorId(id)
    }

    // --- roteiro próprio de uma vaga ---

    /** Roteiro da vaga com as perguntas, ou null se ela ainda usa o padrão. */
    suspend fun obterParaVaga(vagaId: Long): Roteiro? {
        val roteiro = dao.buscarDaVaga(vagaId) ?: return null
        return roteiro.paraDominio().copy(
            perguntas = perguntaDao.listarDoRoteiro(roteiro.id).map { it.paraDominio() }
        )
    }

    fun observarParaVaga(vagaId: Long): Flow<Roteiro?> = combine(
        dao.observarDaVaga(vagaId),
        perguntaDao.observarTodos(),
    ) { roteiro, perguntas ->
        roteiro?.paraDominio()?.copy(
            perguntas = perguntas.filter { it.roteiroId == roteiro.id }.map { it.paraDominio() }
        )
    }

    /**
     * Roteiro que a vaga deve usar na entrevista: o próprio se existir, o
     * padrão do processo caso contrário.
     *
     * Não cria cópia nenhuma — a vaga que nunca foi editada continua
     * acompanhando o roteiro padrão, inclusive nas atualizações dele.
     */
    suspend fun obterParaEntrevista(vagaId: Long?): Roteiro? =
        vagaId?.let { obterParaVaga(it) } ?: obterPadrao()

    /**
     * Cria o roteiro da vaga copiando as perguntas do padrão.
     *
     * É o "pegar como padrão e editar só nesta vaga": a cópia é independente,
     * então mexer aqui não altera o roteiro das outras vagas.
     */
    suspend fun copiarDoPadraoParaVaga(vagaId: Long, titulo: String? = null): Long {
        dao.buscarDaVaga(vagaId)?.let { existente ->
            return existente.id
        }
        val padrao = dao.buscarPadrao()
        val perguntas = padrao
            ?.let { perguntaDao.listarDoRoteiro(it.id).map { p -> p.paraDominio() } }
            .orEmpty()
        val id = dao.inserir(
            RoteiroEntity(
                titulo = titulo ?: padrao?.titulo ?: "Roteiro da vaga",
                conteudo = CONTEUDO_PADRAO,
                ordem = 0,
                padrao = false,
                vagaId = vagaId,
            )
        )
        salvarPerguntas(id, perguntas)
        return id
    }

    /** Grava as perguntas do roteiro da vaga, criando-o se ainda não existir. */
    suspend fun salvarParaVaga(
        vagaId: Long,
        titulo: String,
        perguntas: List<Pergunta>,
    ): Long {
        val existente = dao.buscarDaVaga(vagaId)
        val id = if (existente == null) {
            copiarDoPadraoParaVaga(vagaId, titulo)
        } else {
            dao.atualizar(existente.copy(titulo = titulo))
            existente.id
        }
        salvarPerguntas(id, perguntas)
        return id
    }

    /**
     * Apaga o roteiro próprio da vaga, que volta a seguir o padrão do processo.
     *
     * As perguntas específicas da vaga saem do banco. As respostas já dadas
     * continuam gravadas, ligadas ao id da pergunta antiga, e por isso deixam
     * de aparecer: é o mesmo efeito de apagar um roteiro qualquer, e por isso
     * a confirmação na tela avisa antes.
     */
    suspend fun voltarAoRoteiroPadrao(vagaId: Long) {
        val roteiro = dao.buscarDaVaga(vagaId) ?: return
        perguntaDao.excluirDoRoteiro(roteiro.id)
        dao.excluirPorId(roteiro.id)
    }

    /**
     * Insere o roteiro padrão na primeira execução, já com as perguntas do
     * processo seletivo. Se a base já tem roteiro (usuário do app de antes),
     * preenche as perguntas que faltam sem apagar o que foi customizado.
     */
    suspend fun garantirPadrao() {
        if (dao.contar() == 0) {
            val id = dao.inserir(
                RoteiroEntity(
                    titulo = "Roteiro de Entrevista",
                    conteudo = CONTEUDO_PADRAO,
                    ordem = 0,
                    padrao = true,
                )
            )
            salvarPerguntas(id, perguntasPadrao(id))
        } else {
            val roteiro = dao.buscarPadrao() ?: return
            if (perguntaDao.listarDoRoteiro(roteiro.id).isEmpty()) {
                salvarPerguntas(roteiro.id, perguntasPadrao(roteiro.id))
            }
        }
    }

    companion object {
        val CONTEUDO_PADRAO = "Roteiro de entrevista padrão"

        /**
         * Perguntas do roteiro padrão, na ordem em que devem ser feitas.
         *
         * As de [RespostaAutomatica] já aparecem preenchidas na tela: o app
         * sabe o dia/hora, o nome e os dados da vaga, então o recrutador não
         * digita de novo o que já está no sistema.
         */
        fun perguntasPadrao(roteiroId: Long): List<Pergunta> = listOf(
            secao("Identificação", roteiroId, 0),
            pergunta(
                titulo = "Dia e Horário",
                roteiroId = roteiroId,
                ordem = 1,
                automatica = RespostaAutomatica.DIA_E_HORA,
            ),
            pergunta(
                titulo = "Nome",
                roteiroId = roteiroId,
                ordem = 2,
                automatica = RespostaAutomatica.NOME_CANDIDATO,
            ),
            pergunta(
                titulo = "Onde viu a vaga",
                roteiroId = roteiroId,
                ordem = 3,
                dica = "Indicação, site de vagas, indicação de amigo...",
            ),

            secao("Dados pessoais", roteiroId, 4),
            pergunta("Idade", roteiroId, 5, TipoResposta.NUMERO),
            pergunta("Escolaridade", roteiroId, 6),
            pergunta("Tem habilitação?", roteiroId, 7, TipoResposta.SIM_NAO),
            pergunta("É casado?", roteiroId, 8, TipoResposta.SIM_NAO),
            pergunta("Qual a profissão do marido/esposa?", roteiroId, 9),
            pergunta("Tem filhos?", roteiroId, 10, TipoResposta.SIM_NAO),
            pergunta("Quantos filhos?", roteiroId, 11, TipoResposta.NUMERO),

            secao("Moradia e família", roteiroId, 12),
            pergunta("É daqui de Barbacena mesmo?", roteiroId, 13, TipoResposta.SIM_NAO),
            pergunta("Mora em qual bairro?", roteiroId, 14),
            pergunta("Com quem mora atualmente?", roteiroId, 15, TipoResposta.TEXTO_LONGO),
            pergunta("E a relação familiar é boa?", roteiroId, 16, TipoResposta.SIM_NAO),

            secao("Saúde", roteiroId, 17),
            pergunta(
                titulo = "Faz o uso de alguma medicação, bebida ou cigarro?",
                roteiroId = roteiroId,
                ordem = 18,
                tipo = TipoResposta.TEXTO_LONGO,
            ),
            pergunta(
                titulo = "Já esteve internado(a) nos últimos anos?",
                roteiroId = roteiroId,
                ordem = 19,
                tipo = TipoResposta.SIM_NAO,
            ),
            pergunta(
                titulo = "Está trabalhando em algo atualmente?",
                roteiroId = roteiroId,
                ordem = 20,
                tipo = TipoResposta.SIM_NAO,
            ),

            secao("Relacionamento interpessoal", roteiroId, 21),
            pergunta(
                titulo = "Relacionamento com a equipe (interpessoal)",
                roteiroId = roteiroId,
                ordem = 22,
                tipo = TipoResposta.TEXTO_LONGO,
            ),

            secao("Remuneração", roteiroId, 23),
            pergunta(
                titulo = "Qual seu último salário? Qual sua pretensão salarial?",
                roteiroId = roteiroId,
                ordem = 24,
                tipo = TipoResposta.TEXTO_LONGO,
            ),

            secao("Experiência profissional", roteiroId, 25),
            pergunta(
                titulo = "Gostaria que você falasse agora dos locais onde trabalhou, " +
                    "por quanto tempo e o porquê de ter saído",
                roteiroId = roteiroId,
                ordem = 26,
                tipo = TipoResposta.TABELA_EXPERIENCIAS,
            ),

            secao("Informações da vaga", roteiroId, 27),
            pergunta(
                titulo = "Horário de trabalho, salário e benefícios, tempo de experiência, " +
                    "escolaridade, habilitação e atividades principais",
                roteiroId = roteiroId,
                ordem = 28,
                automatica = RespostaAutomatica.DADOS_DA_VAGA,
                tipo = TipoResposta.TEXTO_LONGO,
            ),
        )

        private fun pergunta(
            titulo: String,
            roteiroId: Long,
            ordem: Int,
            tipo: TipoResposta = TipoResposta.TEXTO,
            automatica: RespostaAutomatica? = null,
            dica: String = "",
        ) = Pergunta(
            roteiroId = roteiroId,
            ordem = ordem,
            titulo = titulo,
            tipo = tipo,
            obrigatoria = false,
            dica = dica,
            respostaAutomatica = automatica,
        )

        private fun secao(titulo: String, roteiroId: Long, ordem: Int) = pergunta(
            titulo = titulo,
            roteiroId = roteiroId,
            ordem = ordem,
            tipo = TipoResposta.SECAO,
        )
    }
}

/** Respostas e locais preenchidos durante uma entrevista específica. */
class EntrevistaFormularioRepository(
    private val respostaDao: RespostaDao,
    private val experienciaDao: ExperienciaDao,
) {

    fun observarRespostas(entrevistaId: Long): Flow<Map<Long, String>> =
        respostaDao.observarDaEntrevista(entrevistaId)
            .map { lista -> lista.associate { it.perguntaId to it.texto } }

    fun observarExperiencias(entrevistaId: Long): Flow<List<Experiencia>> =
        experienciaDao.observarDaEntrevista(entrevistaId).map { lista -> lista.map { it.paraDominio() } }

    suspend fun obterExperiencias(entrevistaId: Long): List<Experiencia> =
        experienciaDao.listarDaEntrevista(entrevistaId).map { it.paraDominio() }

    suspend fun salvarResposta(entrevistaId: Long, perguntaId: Long, texto: String) {
        if (texto.isBlank()) {
            return
        }
        respostaDao.salvar(RespostaEntity(entrevistaId, perguntaId, texto))
    }

    suspend fun limparResposta(entrevistaId: Long, perguntaId: Long) {
        respostaDao.salvar(RespostaEntity(entrevistaId, perguntaId, ""))
    }

    /** Salva a linha inteira da tabela de locais (insere ou atualiza) e devolve o id. */
    suspend fun salvarExperiencia(experiencia: Experiencia): Long {
        return if (experiencia.id == 0L) {
            experienciaDao.inserir(experiencia.paraEntidade().copy(entrevistaId = experiencia.entrevistaId))
        } else {
            experienciaDao.atualizar(experiencia.paraEntidade())
            experiencia.id
        }
    }

    suspend fun removerExperiencia(experiencia: Experiencia) {
        if (experiencia.id != 0L) {
            experienciaDao.excluir(experiencia.paraEntidade().copy(id = experiencia.id))
        }
    }

    /**
     * Apaga tudo que pertence a uma entrevista: as respostas do roteiro e a
     * tabela de locais. Precisa ser chamado junto da remoção do candidato — sem
     * isso essas linhas ficavam órfãs no banco, apontando para um candidato que
     * não existe mais.
     */
    suspend fun apagarDadosDaEntrevista(entrevistaId: Long) {
        respostaDao.excluirDaEntrevista(entrevistaId)
        experienciaDao.excluirDaEntrevista(entrevistaId)
    }
}

/** Cadastro das vagas abertas pela empresa. */
class VagaRepository(
    private val dao: VagaDao,
    private val entrevistaRepository: EntrevistaRepository? = null,
) {

    fun observarTodas(): Flow<List<Vaga>> = dao.observarTodas().map { lista -> lista.map { it.paraDominio() } }

    suspend fun buscarPorId(id: Long): Vaga? = dao.buscarPorId(id)?.paraDominio()

    /** Candidatos já inscritos na vaga, para comparar com o limite cadastrado. */
    suspend fun contarCandidatos(vagaId: Long): Int = entrevistaRepository?.contarCandidatosDaVaga(vagaId) ?: 0

    /**
     * Contagem por vaga em forma de Flow, para o aviso de limite acompanhar
     * entrada e saída de candidatos sem precisar recarregar a tela.
     */
    fun observarContagemPorVaga(): Flow<Map<Long, Int>> =
        entrevistaRepository?.observarContagemPorVaga() ?: flowOf(emptyMap())

    suspend fun salvar(vaga: Vaga): Long {
        if (vaga.titulo.isBlank()) return 0L
        return if (vaga.id == 0L) dao.inserir(vaga.paraEntidade()) else {
            dao.atualizar(vaga.paraEntidade())
            vaga.id
        }
    }

    suspend fun excluir(id: Long) = dao.excluirPorId(id)
}
