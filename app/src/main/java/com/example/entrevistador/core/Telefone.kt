package com.example.entrevistador.core

/**
 * Máscara de telefone brasileiro aplicada enquanto o usuário digita.
 *
 * Aceita tanto celular (11 dígitos) quanto fixo (10 dígitos):
 *   11 dígitos -> (35) 99999-9999
 *   10 dígitos -> (35) 3333-4444
 *
 * Só dígitos entram; a formatação é sempre refeita do zero, o que evita o
 * comportamento clássico de a máscara "andar" quando o usuário apaga no meio.
 */
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

/** "(35) 99999-9999" -> "35999999999", para pesquisar/salvar só os dígitos. */
fun somenteDigitos(telefone: String): String = telefone.filter(Char::isDigit)

/** Aplica a máscara a um telefone já salvo, para exibir na agenda. */
fun formatarTelefone(telefone: String): String {
    if (telefone.isBlank()) return ""
    val digitos = telefone.filter(Char::isDigit)
    // Se já vier mascarado de outra fonte, devolve como está.
    if (digitos.length != telefone.length) return telefone
    return aplicarMascaraTelefone(telefone)
}
