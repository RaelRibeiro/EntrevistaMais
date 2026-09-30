package com.example.entrevistador.data.auth

import com.example.entrevistador.domain.model.Usuario
import kotlinx.coroutines.flow.Flow

/**
 * Ponto único de troca do login.
 *
 * Hoje o app usa a [LocalAuthRepository] (offline, sem configuração). Para
 * migrar para o Firebase basta ligar a flag `entrevistador.firebase=true` em
 * gradle.properties e colocar o google-services.json: o [AppContainer] passa a
 * injetar a FirebaseAuthRepository e nenhuma tela muda.
 */
interface AuthRepository {

    /** Usuário logado, ou null se não houver sessão. */
    val usuarioAtual: Flow<Usuario?>

    /** Cria a conta. Em caso de falha, devolve [ErroAuth] com a causa. */
    suspend fun criarConta(nome: String, email: String, senha: String): ResultadoAuth

    suspend fun entrar(email: String, senha: String): ResultadoAuth

    /** Atualiza o nome de exibição do usuário logado. */
    suspend fun atualizarNome(nome: String): ResultadoAuth

    suspend fun sair()

    suspend fun redefinirSenha(email: String): ResultadoAuth
}

sealed interface ResultadoAuth {
    data object Sucesso : ResultadoAuth
    data class Erro(val mensagem: String) : ResultadoAuth
}

fun Throwable.paraErroAuth(): ResultadoAuth.Erro =
    ResultadoAuth.Erro(message ?: "Não foi possível concluir a operação. Tente novamente.")
