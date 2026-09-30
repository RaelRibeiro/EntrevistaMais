package com.example.entrevistador.di

import android.content.Context
import com.example.entrevistador.data.auth.AuthRepository
import com.example.entrevistador.data.firebase.AuthProvedorUid
import com.example.entrevistador.data.firebase.Firebase
import com.example.entrevistador.data.firebase.FirebaseAuthRepository
import com.example.entrevistador.data.firebase.FirestoreFontes

/**
 * Escolhe qual [AuthRepository] usar.
 *
 * O app usa sempre o Firebase: autentica com a mesma conta do site e grava os
 * dados em `usuarios/{uid}`. Nenhuma tela precisa saber disso — o login é o
 * [FirebaseAuthRepository] e o resto da camada de dados é Firestore.
 */
object AuthRepositoryProvider {

    fun criar(context: Context): AuthRepository {
        val appContext = context.applicationContext
        return FirebaseAuthRepository(
            auth = Firebase.auth(appContext),
            firestore = Firebase.firestore(appContext),
        )
    }
}

/** Constrói as fontes Firestore do usuário (DAOs, definições, storage). */
internal fun fontesFirestore(context: Context): FirestoreFontes {
    val appContext = context.applicationContext
    return FirestoreFontes(
        firestore = Firebase.firestore(appContext),
        uidProvider = AuthProvedorUid(Firebase.auth(appContext)),
    )
}