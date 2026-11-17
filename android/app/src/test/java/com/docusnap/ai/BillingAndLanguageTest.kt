package com.docusnap.ai

import com.docusnap.ai.billing.OneTimeSku
import com.docusnap.ai.billing.SubscriptionTier
import com.docusnap.ai.i18n.AppLanguage
import com.docusnap.ai.i18n.AppStrings
import org.junit.Assert.*
import org.junit.Test

class BillingAndLanguageTest {

    @Test
    fun subscriptionTiers_haveCorrectLimitsAndPricing() {
        assertEquals(500, SubscriptionTier.FREE.maxDocuments)
        assertEquals(2000, SubscriptionTier.STARTER.maxDocuments)
        assertEquals(5000, SubscriptionTier.SILVER.maxDocuments)
        assertEquals(10000, SubscriptionTier.GOLD.maxDocuments)
        assertEquals(25000, SubscriptionTier.ENTERPRISE.maxDocuments)
        assertEquals(50000, SubscriptionTier.INFINITY.maxDocuments)

        assertEquals("$0.00", SubscriptionTier.FREE.priceUsd)
        assertEquals("$4.99 / mo", SubscriptionTier.STARTER.priceUsd)
        assertEquals("$9.99 / mo", SubscriptionTier.SILVER.priceUsd)
        assertEquals("$14.99 / mo", SubscriptionTier.GOLD.priceUsd)
        assertEquals("$25.99 / mo", SubscriptionTier.ENTERPRISE.priceUsd)
        assertEquals("$49.99 / mo", SubscriptionTier.INFINITY.priceUsd)
    }

    @Test
    fun oneTimeSkus_cover99To999UsdRange() {
        assertEquals(OneTimeSku.VIP_99, OneTimeSku.nearestForAmount(50))
        assertEquals(OneTimeSku.VIP_99, OneTimeSku.nearestForAmount(99))
        assertEquals(OneTimeSku.VIP_299, OneTimeSku.nearestForAmount(299))
        assertEquals(OneTimeSku.VIP_999, OneTimeSku.nearestForAmount(999))
        assertEquals(OneTimeSku.VIP_999, OneTimeSku.nearestForAmount(1500))
        assertEquals(150_000, OneTimeSku.maxDocsForAmount(999))
    }

    @Test
    fun appStrings_supportsAllPopularLanguages() {
        val languages = listOf(
            AppLanguage.ENGLISH,
            AppLanguage.URDU,
            AppLanguage.ARABIC,
            AppLanguage.SPANISH,
            AppLanguage.FRENCH,
            AppLanguage.GERMAN,
            AppLanguage.CHINESE,
            AppLanguage.HINDI
        )

        for (lang in languages) {
            val title = AppStrings.get(AppStrings.APP_TITLE, lang)
            assertNotNull("Title should not be null for ${lang.nativeName}", title)
            assertTrue("Title should not be blank for ${lang.nativeName}", title.isNotBlank())

            val searchPrompt = AppStrings.get(AppStrings.SEARCH_PLACEHOLDER, lang)
            assertNotNull(searchPrompt)
            assertTrue(searchPrompt.isNotBlank())
        }
    }
}

