package com.example.entrevistador.di

import android.content.Context
import com.example.entrevistador.data.auth.AuthRepository
import com.example.entrevistador.data.firebase.Firebase
import com.example.entrevistador.data.firebase.FirestoreEntrevistaDao
import com.example.entrevistador.data.firebase.FirestoreExperienciaDao
import com.example.entrevistador.data.firebase.FirestoreFontes
import com.example.entrevistador.data.firebase.FirestorePerguntaDao
import com.example.entrevistador.data.firebase.FirestoreRespostaDao
import com.example.entrevistador.data.firebase.FirestoreRoteiroDao
import com.example.entrevistador.data.firebase.FirestoreVagaDao
import com.example.entrevistador.data.local.dao.EntrevistaDao
import com.example.entrevistador.data.local.dao.ExperienciaDao
import com.example.entrevistador.data.local.dao.PerguntaDao
import com.example.entrevistador.data.local.dao.RespostaDao
import com.example.entrevistador.data.local.dao.RoteiroDao
import com.example.entrevistador.data.local.dao.VagaDao
import com.example.entrevistador.data.repository.AnexoRepository
import com.example.entrevistador.data.repository.CurriculoPaginasRepository
import com.example.entrevistador.data.repository.DefinicoesRepository
import com.example.entrevistador.data.repository.EntrevistaFormularioRepository
import com.example.entrevistador.data.repository.EntrevistaRepository
import com.example.entrevistador.data.repository.RoteiroRepository
import com.example.entrevistador.data.repository.VagaRepository

/**
 * Injeção de dependência manual.
 *
 * Deliberadamente sem Hilt: o grafo é pequeno e explícito, o build fica mais
 * rápido e não há uma etapa de geração de código para entender. Se um dia o
 * grafo crescer, trocar por Hilt mexe só este arquivo e os ViewModels.
 */
interface AppContainer {
    val authRepository: AuthRepository
    val definicoesRepository: DefinicoesRepository
    val entrevistaRepository: EntrevistaRepository
    val roteiroRepository: RoteiroRepository
    val formularioRepository: EntrevistaFormularioRepository
    val vagaRepository: VagaRepository
    val anexoRepository: AnexoRepository
    val curriculoPaginasRepository: CurriculoPaginasRepository
}

/**
 * Backend do app é o Firebase (mesmo projeto do site):
 *  - login e sessão via [FirebaseAuth];
 *  - dados (entrevistas, vagas, roteiros, respostas) no Firestore
 *    em `usuarios/{uid}`;
 *  - currículos anexados no Firestore.
 *
 * As interfaces `@Dao` continuam sendo o contrato dos repositórios — a troca de
 * Room para Firestore acontece só aqui, e os testes com fakes seguem intactos.
 */
class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    private val fontes: FirestoreFontes by lazy { fontesFirestore(appContext) }

    override val authRepository: AuthRepository by lazy {
        AuthRepositoryProvider.criar(appContext)
    }

    private val entrevistaDao: EntrevistaDao by lazy { FirestoreEntrevistaDao(fontes) }
    private val vagaDao: VagaDao by lazy { FirestoreVagaDao(fontes) }
    private val roteiroDao: RoteiroDao by lazy { FirestoreRoteiroDao(fontes) }
    private val perguntaDao: PerguntaDao by lazy { FirestorePerguntaDao(fontes) }
    private val respostaDao: RespostaDao by lazy { FirestoreRespostaDao(fontes) }
    private val experienciaDao: ExperienciaDao by lazy { FirestoreExperienciaDao(fontes) }

    override val definicoesRepository: DefinicoesRepository by lazy { DefinicoesRepository(fontes) }

    override val formularioRepository: EntrevistaFormularioRepository by lazy {
        EntrevistaFormularioRepository(respostaDao, experienciaDao)
    }

    override val entrevistaRepository: EntrevistaRepository by lazy {
        EntrevistaRepository(
            dao = entrevistaDao,
            definicoesRepository = definicoesRepository,
            formularioRepository = formularioRepository,
        )
    }

    override val roteiroRepository: RoteiroRepository by lazy {
        RoteiroRepository(roteiroDao, perguntaDao)
    }

    override val vagaRepository: VagaRepository by lazy {
        VagaRepository(vagaDao, entrevistaRepository)
    }

    override val anexoRepository: AnexoRepository by lazy {
        val auth = Firebase.auth(appContext)
        AnexoRepository(
            context = appContext,
            firestore = Firebase.firestore(appContext),
            uid = { auth.currentUser?.uid ?: "" },
        )
    }

    override val curriculoPaginasRepository: CurriculoPaginasRepository by lazy {
        CurriculoPaginasRepository(appContext, anexoRepository)
    }
}