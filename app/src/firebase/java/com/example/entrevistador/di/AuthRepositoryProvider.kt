package com.example.entrevistador.di

import android.content.Context
import com.example.entrevistador.data.auth.AuthRepository
import com.example.entrevistador.data.auth.FirebaseAuthRepository
import com.example.entrevistador.data.auth.SessionStore
import com.google.firebase.auth.FirebaseAuth

/**
 * Versão deste provider usada quando `entrevistador.firebase=true`.
 *
 * Substitui o arquivo de mesmo nome em src/main/java — só um dos dois entra no
 * build, então a troca acontece sem tocar em nenhuma tela.
 */
object AuthRepositoryProvider {

    fun criar(context: Context, sessionStore: SessionStore): AuthRepository =
        FirebaseAuthRepository(FirebaseAuth.getInstance())
}
