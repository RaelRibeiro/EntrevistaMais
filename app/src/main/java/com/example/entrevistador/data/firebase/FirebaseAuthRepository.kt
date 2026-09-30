package com.example.entrevistador.data.firebase

import com.example.entrevistador.data.auth.AuthRepository
import com.example.entrevistador.data.auth.ResultadoAuth
import com.example.entrevistador.data.auth.paraErroAuth
import com.example.entrevistador.domain.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Login real com o Firebase — a mesma conta do site Entrevista+.
 *
 * A sessão é mantida pelo [FirebaseAuth] (armazenamento próprio, desconta o
 * login ao abrir o app). Sempre que o usuário entra, o perfil é gravado de novo
 * em `usuarios/{uid}` com nome e e-mail, criando a raiz onde o Firestore do app
 * (entrevistas, vagas, roteiros...) e do site compartilham os dados.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
) : AuthRepository {

    override val usuarioAtual: Flow<Usuario?> = callbackFlow {
        val ouvinte = FirebaseAuth.AuthStateListener { authFirebase ->
            trySend(mapaDe(authFirebase.currentUser))
        }
        auth.addAuthStateListener(ouvinte)
        trySend(mapaDe(auth.currentUser))
        awaitClose { auth.removeAuthStateListener(ouvinte) }
    }

    private fun mapaDe(usuario: FirebaseUser?): Usuario? = usuario?.let {
        Usuario(uid = it.uid, nome = it.displayName.orEmpty(), email = it.email.orEmpty())
    }

    override suspend fun criarConta(nome: String, email: String, senha: String): ResultadoAuth = try {
        auth.createUserWithEmailAndPassword(email.trim(), senha).await()
        auth.currentUser?.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(nome.trim()).build()
        )?.await()
        gravarPerfil(nome.trim(), email.trim())
        ResultadoAuth.Sucesso
    } catch (e: Exception) {
        e.paraErroAuth()
    }

    override suspend fun entrar(email: String, senha: String): ResultadoAuth {
        return try {
            val resultado = auth.signInWithEmailAndPassword(email.trim(), senha).await()
            val usuario = resultado.user ?: return ResultadoAuth.Erro("Conta não encontrada.")
            gravarPerfil(usuario.displayName.orEmpty(), usuario.email.orEmpty())
            ResultadoAuth.Sucesso
        } catch (e: Exception) {
            e.paraErroAuth()
        }
    }

    /**
     * Completa o login com o idToken vindo da tela (GoogleSignIn do Google).
     * A tela fica com o fluxo do play-services-auth; aqui só trocamos o token
     * pela credencial do Firebase.
     */
    override suspend fun entrarComGoogle(idToken: String): ResultadoAuth {
        return try {
            val credencial = GoogleAuthProvider.getCredential(idToken, null)
            val usuario = auth.signInWithCredential(credencial).await().user
                ?: return ResultadoAuth.Erro("Não foi possível entrar com a conta Google.")
            gravarPerfil(usuario.displayName.orEmpty(), usuario.email.orEmpty())
            ResultadoAuth.Sucesso
        } catch (e: Exception) {
            e.paraErroAuth()
        }
    }

    override suspend fun atualizarNome(nome: String): ResultadoAuth {
        return try {
            if (nome.isBlank()) return ResultadoAuth.Erro("Informe um nome.")
            val usuario = auth.currentUser ?: return ResultadoAuth.Erro("Sessão expirada.")
            usuario.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(nome.trim()).build()
            )?.await()
            gravarPerfil(nome.trim(), usuario.email.orEmpty())
            ResultadoAuth.Sucesso
        } catch (e: Exception) {
            e.paraErroAuth()
        }
    }

    override suspend fun sair() {
        auth.signOut()
    }

    override suspend fun redefinirSenha(email: String): ResultadoAuth = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        ResultadoAuth.Sucesso
    } catch (e: Exception) {
        e.paraErroAuth()
    }

    /**
     * Cria/atualiza o documento `usuarios/{uid}` com o perfil exibido.
     *
     * É a mesma estrutura que o site usa — é o que liga os dados dos dois
     * produtos sob a mesma conta.
     */
    private suspend fun gravarPerfil(nome: String, email: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("usuarios").document(uid)
            .set(mapOf("nome" to nome, "email" to email))
            .await()
    }
}