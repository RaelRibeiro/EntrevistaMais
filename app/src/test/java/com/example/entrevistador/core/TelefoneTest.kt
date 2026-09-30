package com.example.entrevistador.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A máscara precisa se comportar bem quando o usuário apaga no meio do número —
 * é o caso mais comum de digitação e o que mais buga em apps de formulário.
 */
class TelefoneTest {

    @Test
    fun `campo vazio devolve string vazia`() {
        assertEquals("", aplicarMascaraTelefone(""))
    }

    @Test
    fun `ate dois digitos mostra o abre parenteses`() {
        assertEquals("(3", aplicarMascaraTelefone("3"))
        assertEquals("(35", aplicarMascaraTelefone("35"))
    }

    @Test
    fun `completa o DDD e comeca o numero`() {
        assertEquals("(35) 9", aplicarMascaraTelefone("359"))
        assertEquals("(35) 99", aplicarMascaraTelefone("3599"))
    }

    @Test
    fun `celular de onze digitos fica com cinco digitos antes do traco`() {
        assertEquals("(35) 99999-9999", aplicarMascaraTelefone("35999999999"))
    }

    @Test
    fun `telefone de dez digitos usa quatro digitos antes do traco`() {
        assertEquals("(35) 3333-4444", aplicarMascaraTelefone("3533334444"))
    }

    @Test
    fun `apagar no meio refaz a mascara sem deixar residuo`() {
        // Usuário segura o backspace em cima do DDD: sobra "(5) 99999-9999".
        val apagado = "(5) 99999-9999"

        assertEquals("5999999999", apagado.filter(Char::isDigit))
        assertEquals("5999999999", aplicarMascaraTelefone(apagado).filter(Char::isDigit))
    }

    @Test
    fun `apagar o ultimo digito volta para o formato de dez digitos`() {
        val cheio = aplicarMascaraTelefone("35999999999")
        val semUltimo = cheio.dropLast(1)

        // Sobram 10 dígitos, que é a forma de telefone fixo: 4+4.
        assertEquals("(35) 9999-9999", aplicarMascaraTelefone(semUltimo))
    }

    @Test
    fun `letras e simbolos do teclado sao descartados`() {
        assertEquals("(35) 99999-9999", aplicarMascaraTelefone("(35) 9-9999 9999"))
    }

    @Test
    fun `mais de onze digitos e truncado`() {
        assertEquals("(35) 99999-9999", aplicarMascaraTelefone("359999999999999"))
    }

    @Test
    fun `somenteDigitos remove a formatacao`() {
        assertEquals("35999999999", somenteDigitos("(35) 99999-9999"))
    }

    @Test
    fun `formatarTelefone reaplica a mascara em numero salvo cru`() {
        assertEquals("(35) 99999-9999", formatarTelefone("35999999999"))
    }

    @Test
    fun `formatarTelefone devolve como esta se ja vier mascarado`() {
        assertEquals("(35) 99999-9999", formatarTelefone("(35) 99999-9999"))
    }

    @Test
    fun `formatarTelefone de numero curto fica com mascara parcial`() {
        assertEquals("(35", formatarTelefone("35"))
    }
}
