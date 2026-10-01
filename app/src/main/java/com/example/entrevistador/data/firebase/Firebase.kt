package com.example.entrevistador.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Configuração do projeto Firebase usado pelo app.
 *
 * São os mesmos valores do site (Entrevista+): o app autentica com a mesma
 * conta e lê/grava na mesma árvore `usuarios/{uid}` do Firestore.
 *
 * As chaves aqui são públicas por natureza (id do projeto, não segredo); o que
 * protege os dados são as regras do Firestore, que exigem login.
 */
object FirebaseConfig {
    const val CHAVE_API = "AIzaSyBk2nOHu3QDzcSMqZvvOpgpWOZywR_W9nQ"
    const val APP_ID = "1:938870652966:web:88b61b4ca050c7fc89e6e7"
    const val PROJETO = "entrevistamais-a25e1"

    /**
     * Web client ID do projeto (OAuth do "app web").
     *
     * Necessário só para o login com Google por idToken. Enquanto estiver
     * vazio, o botão "Entrar com Google" fica oculto — e-mail/senha funciona.
     */
    const val GOOGLE_SERVER_CLIENT_ID = "938870652966-epdjqscqogdmki95sq950tibvipu6sk4.apps.googleusercontent.com"
}

/**
 * Inicializa o Firebase em código (sem google-services.json) e entrega as
 * instâncias do projeto. O app é criado uma única vez e reutilizado.
 */
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

/**
 * Quem é o usuário dono dos dados (a raiz `usuarios/{uid}`).
 *
 * Toda chamada aos DAOs acontece dentro de uma sessão logada; se por algum
 * motivo o uid não existir, a chamada falha em vez de gravar no perfil errado.
 */
fun interface ProvedorUid {
    fun uid(): String
}

/** Pega o uid direto do [FirebaseAuth] logado no momento. */
class AuthProvedorUid(private val auth: FirebaseAuth) : ProvedorUid {
    override fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão do Firebase.")
}