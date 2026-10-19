package com.example.entrevistador.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseConfig {
    const val CHAVE_API = "AIzaSyBk2nOHu3QDzcSMqZvvOpgpWOZywR_W9nQ"
    const val APP_ID = "1:938870652966:web:88b61b4ca050c7fc89e6e7"
    const val PROJETO = "entrevistamais-a25e1"

    const val GOOGLE_SERVER_CLIENT_ID = "938870652966-epdjqscqogdmki95sq950tibvipu6sk4.apps.googleusercontent.com"
}

object Firebase {

    @Volatile
    private var app: FirebaseApp? = null

    private fun app(context: Context): FirebaseApp {
        app?.let { return it }
        synchronized(this) {
            app?.let { return it }
            val opcoes = FirebaseOptions.Builder()
                .setApiKey(FirebaseConfig.CHAVE_API)
                .setApplicationId(FirebaseConfig.APP_ID)
                .setProjectId(FirebaseConfig.PROJETO)
                .build()
            val novo = FirebaseApp.initializeApp(context.applicationContext, opcoes)!!
            app = novo
            return novo
        }
    }

    fun auth(context: Context): FirebaseAuth = FirebaseAuth.getInstance(app(context))

    fun firestore(context: Context): FirebaseFirestore = FirebaseFirestore.getInstance(app(context))
}

fun interface ProvedorUid {
    fun uid(): String
}

class AuthProvedorUid(private val auth: FirebaseAuth) : ProvedorUid {
    override fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão do Firebase.")
}
