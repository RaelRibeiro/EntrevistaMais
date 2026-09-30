package com.example.entrevistador.domain.model

import java.time.LocalDate

/**
 * Usuário logado. [uid] é o identificador estável da conta (no login local é o
 * hash do e-mail, no Firebase é o uid real), e [email] serve para exibição.
 */
data class Usuario(
    val uid: String,
    val nome: String,
    val email: String,
)
