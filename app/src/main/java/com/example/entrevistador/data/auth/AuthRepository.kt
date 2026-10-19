package com.example.entrevistador.data.auth

import com.example.entrevistador.domain.model.Usuario
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val usuarioAtual: Flow<Usuario?>

    suspend fun criarConta(nome: String, email: String, senha: String): ResultadoAuth

    suspend fun entrar(email: String, senha: String): ResultadoAuth

    suspend fun entrarComGoogle(idToken: String): ResultadoAuth

    suspend fun atualizarNome(nome: String): ResultadoAuth

    suspend fun sair()

    suspend fun redefinirSenha(email: String): ResultadoAuth
}

sealed interface ResultadoAuth {
    data object Sucesso : ResultadoAuth
    data class Erro(val mensagem: String) : ResultadoAuth
}

fun Throwable.paraErroAuth(): ResultadoAuth.Erro {
    val codigo = when (this) {
        is FirebaseAuthException -> errorCode.removePrefix("ERROR_").lowercase()
        else -> null
    }
    return ResultadoAuth.Erro(
        when (codigo) {
            "email_already_in_use" ->
                "Este e-mail já tem conta. Entre com a senha que você já usou."
            "invalid_credential", "wrong_password", "user_not_found" ->
                "E-mail ou senha incorretos."
            "weak_password" -> "A senha é fraca demais."
            "invalid_email" -> "E-mail inválido."
            "network_request_failed" ->
                "Sem conexão com a internet. Verifique a rede e tente de novo."
            "too_many_requests" ->
                "Muitas tentativas seguidas. Espere um instante e tente de novo."
            else -> message?.takeIf { it.isNotBlank() }
                ?: "Não foi possível concluir a operação. Tente novamente."
        }
    )
}
