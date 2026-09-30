package com.example.entrevistador.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.entrevistador.domain.model.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStoreSessao: DataStore<Preferences> by preferencesDataStore("sessao")

/**
 * Guarda quem está logado para que o app já abra direto na agenda.
 *
 * Guarda só o uid, o nome e o e-mail — nunca a senha. A senha fica com o
 * [AuthRepository] responsável (hash local ou, no futuro, o Firebase).
 */
class SessionStore(private val context: Context) {

    private object Chaves {
        val uid = stringPreferencesKey("uid")
        val nome = stringPreferencesKey("nome")
        val email = stringPreferencesKey("email")
    }

    val usuario: Flow<Usuario?> = context.dataStoreSessao.data.map { prefs ->
        val uid = prefs[Chaves.uid]
        if (uid.isNullOrBlank()) {
            null
        } else {
            Usuario(
                uid = uid,
                nome = prefs[Chaves.nome].orEmpty(),
                email = prefs[Chaves.email].orEmpty(),
            )
        }
    }

    suspend fun usuarioAtual(): Usuario? = usuario.first()

    suspend fun salvar(usuario: Usuario) {
        context.dataStoreSessao.edit { prefs ->
            prefs[Chaves.uid] = usuario.uid
            prefs[Chaves.nome] = usuario.nome
            prefs[Chaves.email] = usuario.email
        }
    }

    suspend fun atualizarNome(nome: String) {
        context.dataStoreSessao.edit { prefs -> prefs[Chaves.nome] = nome }
    }

    suspend fun limpar() {
        context.dataStoreSessao.edit { it.clear() }
    }
}
