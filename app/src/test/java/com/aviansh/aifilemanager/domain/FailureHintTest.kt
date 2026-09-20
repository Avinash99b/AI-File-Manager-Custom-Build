package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.ui.components.failureHint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureHintTest {

    @Test
    fun iterationExhaustionSuggestsNarrowingTheRequest() {
        val hint = failureHint(
            "The agent used all 12 reasoning steps without reaching a conclusion."
        )
        assertNotNull(hint)
        assertTrue(hint!!.contains("narrower"))
    }

    @Test
    fun sandboxWriteDenialIsExplainedInUserTerms() {
        val hint = failureHint(
            "PermissionError: Write denied outside workspace: /storage/emulated/0/Download/a.pdf"
        )
        assertNotNull(hint)
        assertTrue(hint!!.contains("propose a plan"))
    }

    @Test
    fun missingProviderPointsAtSettings() {
        val hint = failureHint("Provider not configured")
        assertNotNull(hint)
        assertTrue(hint!!.contains("OpenAI compatible"))
    }

    @Test
    fun readDenialMentionsSharedStorage() {
        val hint = failureHint("Read denied outside allowed roots: /data/misc/x")
        assertNotNull(hint)
        assertTrue(hint!!.contains("/storage/emulated/0"))
    }

    @Test
    fun unknownErrorsProduceNoNoise() {
        assertNull(failureHint("Something entirely unexpected happened"))
    }
}
