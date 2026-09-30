package com.example.entrevistador.data.auth

import com.example.entrevistador.domain.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileUpdateRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
/**
 * Login via Firebase Authentication.
 *
 * Só entra no build quando `entrevistador.firebase=true` em gradle.properties
 * (veja app/build.gradle.kts) — assim o projeto continua compilando e rodando
 * sem o google-services.json.
 */
class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    override val usuarioAtual: Flow<Usuario?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.paraUsuario())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun criarConta(nome: String, email: String, senha: String): ResultadoAuth {
        if (nome.isBlank()) return ResultadoAuth.Erro("Informe o seu nome.")
        if (!email.matches(LocalAuthRepository.REGEX_EMAIL)) {
            return ResultadoAuth.Erro("Informe um e-mail válido.")
        }
        if (senha.length < LocalAuthRepository.MINIMO_SENHA) {
            return ResultadoAuth.Erro("A senha precisa ter ao menos ${LocalAuthRepository.MINIMO_SENHA} caracteres.")
        }

        return executar {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), senha)
        }.also { resultado ->
            if (resultado is ResultadoAuth.Sucesso) {
                firebaseAuth.currentUser?.updateProfile(
                    userProfileUpdateRequest { displayName = nome.trim() }
                )
            }
        }
    }

    override suspend fun entrar(email: String, senha: String): ResultadoAuth = executar {
        firebaseAuth.signInWithEmailAndPassword(email.trim(), senha)
    }

    override suspend fun atualizarNome(nome: String): ResultadoAuth {
        if (nome.isBlank()) return ResultadoAuth.Erro("Informe um nome.")
        return executar {
            firebaseAuth.currentUser?.updateProfile(
                userProfileUpdateRequest { displayName = nome.trim() }
            ) ?: throw IllegalStateException("Sessão expirada.")
        }
    }

    override suspend fun sair() {
        firebaseAuth.signOut()
    }

    override suspend fun redefinirSenha(email: String): ResultadoAuth {
        if (!email.matches(LocalAuthRepository.REGEX_EMAIL)) {
            return ResultadoAuth.Erro("Informe um e-mail válido.")
        }
        return executar {
            firebaseAuth.sendPasswordResetEmail(email.trim())
        }
    }

    private suspend fun executar(bloco: suspend () -> Unit): ResultadoAuth = try {
        bloco()
        ResultadoAuth.Sucesso
    } catch (e: Exception) {
        ResultadoAuth.Erro(e.traduzir())
    }

    private fun Exception.traduzir(): String = when {
        message?.contains("email address is already in use", ignoreCase = true) == true ->
            "Já existe uma conta com este e-mail."
        message?.contains("invalid email", ignoreCase = true) == true ->
            "E-mail inválido."
        message?.contains("weak password", ignoreCase = true) == true ->
            "Senha fraca demais."
        message?.contains("network", ignoreCase = true) == true ->
            "Sem conexão com a internet."
        message?.contains("not found", ignoreCase = true) == true ->
            "E-mail ou senha incorretos."
        message?.contains("wrong password", ignoreCase = true) == true ->
            "E-mail ou senha incorretos."
        message?.contains("too many requests", ignoreCase = true) == true ->
            "Muitas tentativas. Aguarde um instante e tente de novo."
        else -> message ?: "Não foi possível concluir a operação. Tente novamente."
    }

    private fun FirebaseUser.paraUsuario() = Usuario(
        uid = uid,
        nome = displayName.orEmpty().ifBlank { email?.substringBefore('@').orEmpty() },
        email = email.orEmpty(),
    )
}
