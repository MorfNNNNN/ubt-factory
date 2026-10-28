package ru.ubt.familyshield.shared.detect

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractCyrillicTest {
    @Test
    fun cyrillicDomainWithoutSchemeIsFound() {
        assertEquals(listOf("госуслуги-выплаты.рф"), UrlAnalyzer.extract("Получите выплату на госуслуги-выплаты.рф до пятницы"))
    }

    @Test
    fun latinDomainAfterCyrillicWordIsFound() {
        assertEquals(listOf("gosuslugi-pay.ru"), UrlAnalyzer.extract("Срочно:gosuslugi-pay.ru"))
    }
}
