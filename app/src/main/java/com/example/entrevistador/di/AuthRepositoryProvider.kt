package com.example.entrevistador.di

import android.content.Context
import com.example.entrevistador.data.auth.AuthRepository
import com.example.entrevistador.data.auth.LocalAuthRepository
import com.example.entrevistador.data.auth.SessionStore

/**
 * Escolhe qual [AuthRepository] usar.
 *
 * Esta é a versão padrão: login local, offline, sem nenhuma configuração.
 * Quando `entrevistador.firebase=true`, o arquivo de mesmo nome em
 * `src/firebase/java` entra no build no lugar deste e devolve a
 * FirebaseAuthRepository. Nenhuma tela precisa mudar.
 */
object AuthRepositoryProvider {

    fun criar(context: Context, sessionStore: SessionStore): AuthRepository =
        LocalAuthRepository(context, sessionStore)
}
