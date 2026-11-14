package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.*
import org.junit.Test

class StatementImportSourceTest {
    @Test fun sourceLabelsPreservePublishedNormalizationAndKeepAccountsIndependent() {
        val personal = statementSourceKey("Cartão pessoal")
        assertEquals(personal, statementSourceKey("  CARTÃO   PESSOAL  "))
        assertEquals(personal, statementSourceKey("Carta\u0303o pessoal"))
        assertNotEquals(personal, statementSourceKey("Cartão conjunto"))
        assertNotEquals(personal, statementSourceKey(null))
        assertEquals(statementSourceKey(null), statementSourceKey(" \t "))
    }
}
