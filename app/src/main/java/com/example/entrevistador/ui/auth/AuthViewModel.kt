package com.example.entrevistador.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entrevistador.data.auth.AuthRepository
import com.example.entrevistador.data.auth.ResultadoAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Campos da tela de login e de criação de conta. */
data class EstadoAuth(
    val nome: String = "",
    val email: String = "",
    val senha: String = "",
    val confirmarSenha: String = "",
    val erroNome: String? = null,
    val erroEmail: String? = null,
    val erroSenha: String? = null,
    val erroConfirmarSenha: String? = null,
    val erroGeral: String? = null,
    val carregando: Boolean = false,
    val dicaSenha: String = "Mínimo de 6 caracteres",
) {
    val podeEnviar: Boolean
        get() = email.isNotBlank() && senha.isNotBlank() && !carregando
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoAuth())
    val estado: StateFlow<EstadoAuth> = _estado.asStateFlow()

    fun aoAlterarNome(valor: String) = _estado.update {
        it.copy(nome = valor, erroNome = null, erroGeral = null)
    }

    fun aoAlterarEmail(valor: String) = _estado.update {
        it.copy(email = valor, erroEmail = null, erroGeral = null)
    }

    fun aoAlterarSenha(valor: String) = _estado.update {
        it.copy(senha = valor, erroSenha = null, erroGeral = null)
    }

    fun aoAlterarConfirmarSenha(valor: String) = _estado.update {
        it.copy(confirmarSenha = valor, erroConfirmarSenha = null, erroGeral = null)
    }

    fun entrar() {
        val atual = _estado.value
        if (!atual.podeEnviar) return

        val erroEmail = if (atual.email.matches(REGEX_EMAIL)) null else "E-mail inválido."
        val erroSenha = if (atual.senha.length >= MINIMO_SENHA) null else "Senha muito curta."
        if (erroEmail != null || erroSenha != null) {
            _estado.update { it.copy(erroEmail = erroEmail, erroSenha = erroSenha) }
            return
        }

        _estado.update { it.copy(carregando = true, erroGeral = null) }
        viewModelScope.launch {
            when (val resultado = authRepository.entrar(atual.email, atual.senha)) {
                is ResultadoAuth.Erro ->
                    _estado.update { it.copy(carregando = false, erroGeral = resultado.mensagem) }

                ResultadoAuth.Sucesso ->
                    // A navegação reage à sessão; aqui só liberamos o formulário.
                    _estado.update { it.copy(carregando = false) }
            }
        }
    }

    fun criarConta() {
        val atual = _estado.value
        if (atual.carregando) return

        val erroNome = if (atual.nome.isNotBlank()) null else "Informe o seu nome."
        val erroEmail = if (atual.email.matches(REGEX_EMAIL)) null else "E-mail inválido."
        val erroSenha = if (atual.senha.length >= MINIMO_SENHA) null else "Mínimo de $MINIMO_SENHA caracteres."
        val erroConfirmar = if (atual.senha == atual.confirmarSenha) null else "As senhas não conferem."

        if (erroNome != null || erroEmail != null || erroSenha != null || erroConfirmar != null) {
            _estado.update {
                it.copy(
                    erroNome = erroNome,
                    erroEmail = erroEmail,
                    erroSenha = erroSenha,
                    erroConfirmarSenha = erroConfirmar,
                )
            }
            return
        }

        _estado.update { it.copy(carregando = true, erroGeral = null) }
        viewModelScope.launch {
            when (
                val resultado = authRepository.criarConta(atual.nome, atual.email, atual.senha)
            ) {
                is ResultadoAuth.Erro ->
                    _estado.update { it.copy(carregando = false, erroGeral = resultado.mensagem) }

                ResultadoAuth.Sucesso ->
                    _estado.update { it.copy(carregando = false) }
            }
        }
    }

    fun esquecerSenha() {
        val email = _estado.value.email
        if (!email.matches(REGEX_EMAIL)) {
            _estado.update { it.copy(erroEmail = "Digite seu e-mail para redefinir a senha.") }
            return
        }
        _estado.update { it.copy(carregando = true) }
        viewModelScope.launch {
            val resultado = authRepository.redefinirSenha(email)
            _estado.update {
                it.copy(
                    carregando = false,
                    erroGeral = when (resultado) {
                        is ResultadoAuth.Erro -> resultado.mensagem
                        ResultadoAuth.Sucesso -> "Se a conta existir, você receberá o e-mail de redefinição."
                    },
                )
            }
        }
    }

    fun sair() {
        viewModelScope.launch { authRepository.sair() }
    }

    fun limparErroGeral() = _estado.update { it.copy(erroGeral = null) }

    companion object {
        const val MINIMO_SENHA = 6
        val REGEX_EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
