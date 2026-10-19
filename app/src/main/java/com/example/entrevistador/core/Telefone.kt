package com.example.entrevistador.core

fun aplicarMascaraTelefone(entrada: String): String {
    val digitos = entrada.filter(Char::isDigit).take(11)
    if (digitos.isEmpty()) return ""

    return when {
        digitos.length <= 2 -> "(${digitos}"
        digitos.length <= 6 -> "(${digitos.take(2)}) ${digitos.drop(2)}"
        digitos.length <= 10 -> "(${digitos.take(2)}) ${digitos.substring(2, 6)}-${digitos.drop(6)}"
        else -> "(${digitos.take(2)}) ${digitos.substring(2, 7)}-${digitos.drop(7)}"
    }
}

fun somenteDigitos(telefone: String): String = telefone.filter(Char::isDigit)

fun formatarTelefone(telefone: String): String {
    if (telefone.isBlank()) return ""
    val digitos = telefone.filter(Char::isDigit)

    if (digitos.length != telefone.length) return telefone
    return aplicarMascaraTelefone(telefone)
}
