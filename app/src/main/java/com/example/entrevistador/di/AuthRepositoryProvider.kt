package com.example.entrevistador.di

import android.content.Context
import com.example.entrevistador.data.auth.AuthRepository
import com.example.entrevistador.data.firebase.AuthProvedorUid
import com.example.entrevistador.data.firebase.Firebase
import com.example.entrevistador.data.firebase.FirebaseAuthRepository
import com.example.entrevistador.data.firebase.FirestoreFontes

object AuthRepositoryProvider {

    fun criar(context: Context): AuthRepository {
        val appContext = context.applicationContext
        return FirebaseAuthRepository(
            auth = Firebase.auth(appContext),
            firestore = Firebase.firestore(appContext),
        )
    }
}

internal fun fontesFirestore(context: Context): FirestoreFontes {
    val appContext = context.applicationContext
    return FirestoreFontes(
        firestore = Firebase.firestore(appContext),
        uidProvider = AuthProvedorUid(Firebase.auth(appContext)),
    )
}
