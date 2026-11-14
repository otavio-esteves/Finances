package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.*
import org.junit.Test

class StatementImportSourceTest {
    @Test fun equivalentLabelsKeepTheSameClaimWhileDifferentAccountsAndContentRemainIndependent() {
        val personal = scopedStatementFingerprint("content-a", "Cartão pessoal")
        assertEquals(personal, scopedStatementFingerprint("content-a", "  CARTAO   PESSOAL  "))
        assertEquals(personal, scopedStatementFingerprint("content-a", "Cartão\u00a0\u00a0pessoal"))
        assertNotEquals(personal, scopedStatementFingerprint("content-a", "Cartão conjunto"))
        assertNotEquals(personal, scopedStatementFingerprint("content-b", "Cartão pessoal"))
        assertNotEquals(personal, scopedStatementFingerprint("content-a", null))
        assertEquals(scopedStatementFingerprint("content-a", null), scopedStatementFingerprint("content-a", " \t "))
    }
}
