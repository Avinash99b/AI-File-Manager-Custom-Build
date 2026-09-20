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
            "The agent used all 16 steps without finishing. Try a more specific request."
        )
        assertNotNull(hint)
        assertTrue(hint!!.contains("narrower"))
    }

    @Test
    fun missingProviderPointsAtSettings() {
        val hint = failureHint("Provider not configured")
        assertNotNull(hint)
        assertTrue(hint!!.contains("OpenAI compatible"))
    }

    @Test
    fun readDenialMentionsSharedStorage() {
        val hint = failureHint("Read denied: /data/misc/x is outside your shared storage")
        assertNotNull(hint)
        assertTrue(hint!!.contains("/storage/emulated/0"))
    }

    @Test
    fun unknownErrorsProduceNoNoise() {
        assertNull(failureHint("Something entirely unexpected happened"))
    }
}
