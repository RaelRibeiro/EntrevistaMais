package com.example.entrevistador.data.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.entrevistador.domain.model.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.dataStoreContas: DataStore<Preferences> by preferencesDataStore("contas")

/**
 * Login local, 100% offline — é a implementação padrão do app.
 *
 * A senha nunca é guardada em texto puro: geramos um sal aleatório por conta e
 * guardamos apenas SHA-256(sal + senha). Isso é suficiente para um app que roda
 * só no aparelho; se um dia a conta precisar sincronizar entre dispositivos, o
 * ponto de troca é o [AuthRepository] (Firebase).
 */
class LocalAuthRepository(
    private val context: Context,
    private val sessionStore: SessionStore,
) : AuthRepository {

    private object Chaves {
        fun nomeDe(email: String) = stringPreferencesKey("conta_nome_${normalizar(email)}")
        fun salDe(email: String) = stringPreferencesKey("conta_sal_${normalizar(email)}")
        fun hashDe(email: String) = stringPreferencesKey("conta_hash_${normalizar(email)}")
    }

    override val usuarioAtual: Flow<Usuario?> = sessionStore.usuario

    override suspend fun criarConta(nome: String, email: String, senha: String): ResultadoAuth {
        val erro = validar(nome, email, senha)
        if (erro != null) return erro

        val normalizado = normalizar(email)
        if (context.dataStoreContas.data.first()[Chaves.hashDe(normalizado)] != null) {
            return ResultadoAuth.Erro("Já existe uma conta com este e-mail.")
        }

        val sal = gerarSal()
        context.dataStoreContas.edit { prefs ->
            prefs[Chaves.nomeDe(normalizado)] = nome.trim()
            prefs[Chaves.salDe(normalizado)] = sal
            prefs[Chaves.hashDe(normalizado)] = gerarHash(sal, senha)
        }

        sessionStore.salvar(
            Usuario(uid = normalizado, nome = nome.trim(), email = email.trim())
        )
        return ResultadoAuth.Sucesso
    }

    override suspend fun entrar(email: String, senha: String): ResultadoAuth {
        val normalizado = normalizar(email)
        val prefs = context.dataStoreContas.data.first()
        val sal = prefs[Chaves.salDe(normalizado)]
        val hashEsperado = prefs[Chaves.hashDe(normalizado)]

        if (sal == null || hashEsperado == null) {
            return ResultadoAuth.Erro("E-mail ou senha incorretos.")
        }
        if (gerarHash(sal, senha) != hashEsperado) {
            return ResultadoAuth.Erro("E-mail ou senha incorretos.")
        }

        sessionStore.salvar(
            Usuario(
                uid = normalizado,
                nome = prefs[Chaves.nomeDe(normalizado)].orEmpty(),
                email = email.trim(),
            )
        )
        return ResultadoAuth.Sucesso
    }

    override suspend fun entrarComGoogle(idToken: String): ResultadoAuth =
        ResultadoAuth.Erro("Login com Google disponível apenas na versão com Firebase.")

    override suspend fun atualizarNome(nome: String): ResultadoAuth {
        if (nome.isBlank()) return ResultadoAuth.Erro("Informe um nome.")
        val usuario = sessionStore.usuarioAtual() ?: return ResultadoAuth.Erro("Sessão expirada.")

        context.dataStoreContas.edit { it[Chaves.nomeDe(usuario.email)] = nome.trim() }
        sessionStore.atualizarNome(nome.trim())
        return ResultadoAuth.Sucesso
    }

    override suspend fun sair() = sessionStore.limpar()

    override suspend fun redefinirSenha(email: String): ResultadoAuth {
        val normalizado = normalizar(email)
        val prefs = context.dataStoreContas.data.first()
        if (prefs[Chaves.hashDe(normalizado)] == null) {
            return ResultadoAuth.Erro("Não encontramos uma conta com este e-mail.")
        }
        return ResultadoAuth.Erro(
            "Como a senha é guardada apenas neste aparelho, não dá para redefini-la por e-mail. " +
                "Crie uma nova conta ou peça para a conta ser removida."
        )
    }

    private fun validar(nome: String, email: String, senha: String): ResultadoAuth.Erro? = when {
        nome.isBlank() -> ResultadoAuth.Erro("Informe o seu nome.")
        !email.matches(REGEX_EMAIL) -> ResultadoAuth.Erro("Informe um e-mail válido.")
        senha.length < MINIMO_SENHA -> ResultadoAuth.Erro("A senha precisa ter ao menos $MINIMO_SENHA caracteres.")
        else -> null
    }

    private fun gerarSal(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun gerarHash(sal: String, senha: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$sal:$senha".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        const val MINIMO_SENHA = 6
        val REGEX_EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        /** E-mail é a chave da conta, então é sempre guardado em minúsculas. */
        fun normalizar(email: String): String = email.trim().lowercase()
    }
}
