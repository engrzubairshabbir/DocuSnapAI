package com.docusnap.ai

import com.docusnap.ai.ai.GeminiReasoningEngine
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.data.model.KeyEntities
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeminiReasoningEngineTest {

    private lateinit var engine: GeminiReasoningEngine

    @Before
    fun setUp() {
        engine = GeminiReasoningEngine(apiKey = "")
    }

    @Test
    fun fallbackExtraction_identifiesMedicalPrescription() {
        val ocrSample = """
            DR. TARIQ MAHMOOD (FCPS)
            Shifa International Hospital
            Date: 2026-11-04
            Rx:
            Tab Risek 40mg 1x daily
            Tab Augmentin 625mg 1+1
            Tab Panadol 500mg SOS
        """.trimIndent()

        val result = engine.fallbackExtraction(ocrSample)

        assertEquals(DocumentEntity.TYPE_MEDICAL, result.documentType)
        assertTrue(result.keyEntities.medicines.isNotEmpty())
        assertTrue(result.keyEntities.medicines.any { it.contains("Risek", ignoreCase = true) })
        assertTrue(result.searchableTags.contains("medical"))
    }

    @Test
    fun fallbackExtraction_identifiesUtilityBillAndAmount() {
        val ocrSample = """
            K-ELECTRIC DUPLICATE BILL
            Consumer ID: 0400018938210
            Total Amount: Rs 14820
            Due Date: 2026-11-18
        """.trimIndent()

        val result = engine.fallbackExtraction(ocrSample)

        assertEquals(DocumentEntity.TYPE_UTILITY, result.documentType)
        assertNotNull(result.keyEntities.totalAmount)
        assertEquals(14820.0, result.keyEntities.totalAmount!!, 0.01)
        assertTrue(result.searchableTags.contains("bill"))
    }

    @Test
    fun fallbackExtraction_identifiesExpenseReceipt() {
        val ocrSample = """
            IMTIAZ SUPER MARKET
            Invoice # 93821
            Date: 2026-11-08
            Total: 8578.00
            Paid by Cash
        """.trimIndent()

        val result = engine.fallbackExtraction(ocrSample)

        assertEquals(DocumentEntity.TYPE_RECEIPT, result.documentType)
        assertNotNull(result.keyEntities.totalAmount)
        assertEquals(8578.0, result.keyEntities.totalAmount!!, 0.01)
    }

    @Test
    fun keyEntities_jsonSerializationRoundTrip() {
        val original = KeyEntities(
            doctorName = "Dr. Tariq",
            medicines = listOf("Tab Risek 40mg", "Tab Augmentin 625mg"),
            totalAmount = 3500.0,
            vendorOrHospital = "Chughtai Lab",
            date = "2026-11-10"
        )

        val json = original.toJson()
        val deserialized = KeyEntities.fromJson(json)

        assertEquals(original.doctorName, deserialized.doctorName)
        assertEquals(original.medicines, deserialized.medicines)
        assertEquals(original.totalAmount, deserialized.totalAmount)
        assertEquals(original.vendorOrHospital, deserialized.vendorOrHospital)
        assertEquals(original.date, deserialized.date)
    }
}

