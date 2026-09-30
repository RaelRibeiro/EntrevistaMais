package com.example.entrevistador.di

import android.content.Context
import com.example.entrevistador.data.auth.AuthRepository
import com.example.entrevistador.data.auth.SessionStore
import com.example.entrevistador.data.local.EntrevistadorDatabase
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
    val sessionStore: SessionStore
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    private val database: EntrevistadorDatabase by lazy { EntrevistadorDatabase.obter(appContext) }

    override val sessionStore: SessionStore by lazy { SessionStore(appContext) }

    override val definicoesRepository: DefinicoesRepository by lazy { DefinicoesRepository(appContext) }

    // Ponto único de troca do login. A implementação deste provider é a local
    // (offline) por padrão; com a flag do Firebase ligada, o arquivo de mesmo
    // nome em src/firebase/java substitui este e devolve a FirebaseAuthRepository.
    override val authRepository: AuthRepository by lazy {
        AuthRepositoryProvider.criar(appContext, sessionStore)
    }

    override val entrevistaRepository: EntrevistaRepository by lazy {
        EntrevistaRepository(
            database.entrevistaDao(),
            definicoesRepository,
            formularioRepository,
        )
    }

    override val roteiroRepository: RoteiroRepository by lazy {
        RoteiroRepository(database.roteiroDao(), database.perguntaDao())
    }

    override val formularioRepository: EntrevistaFormularioRepository by lazy {
        EntrevistaFormularioRepository(database.respostaDao(), database.experienciaDao())
    }

    override val vagaRepository: VagaRepository by lazy {
        VagaRepository(database.vagaDao(), entrevistaRepository)
    }

    override val anexoRepository: AnexoRepository by lazy { AnexoRepository(appContext) }

    override val curriculoPaginasRepository: CurriculoPaginasRepository by lazy {
        CurriculoPaginasRepository(appContext)
    }
}
