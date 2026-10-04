package com.example.entrevistador.data.auth

import com.example.entrevistador.domain.model.Usuario
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.flow.Flow

/**
 * Login do usuário.
 *
 * Hoje é sempre o Firebase (mesma conta do site Entrevista+): a [FirebaseAuthRepository]
 * autentica, mantém a sessão e grava a raiz `usuarios/{uid}` dos dados.
 */
interface AuthRepository {

    /** Usuário logado, ou null se não houver sessão. */
    val usuarioAtual: Flow<Usuario?>

    /** Cria a conta. Em caso de falha, devolve [ErroAuth] com a causa. */
    suspend fun criarConta(nome: String, email: String, senha: String): ResultadoAuth

    suspend fun entrar(email: String, senha: String): ResultadoAuth

    /** Entra com o idToken do Google (vindo da tela via GoogleSignIn). */
    suspend fun entrarComGoogle(idToken: String): ResultadoAuth

    /** Atualiza o nome de exibição do usuário logado. */
    suspend fun atualizarNome(nome: String): ResultadoAuth

    suspend fun sair()

    suspend fun redefinirSenha(email: String): ResultadoAuth
}

sealed interface ResultadoAuth {
    data object Sucesso : ResultadoAuth
    data class Erro(val mensagem: String) : ResultadoAuth
}

/**
 * Traduz o erro do Firebase para uma mensagem que faça sentido na tela.
 *
 * Sem isto o app mostra o texto cru do Firebase, em inglês e com
 * "exception" no meio, e o usuário não sabe o que fazer. O caso mais
 * importante é o e-mail que já tem conta: ele aparece tanto no cadastro
 * quanto no login, e é justamente quando o usuário precisa saber que existe
 * uma conta com outra senha.
 */
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
