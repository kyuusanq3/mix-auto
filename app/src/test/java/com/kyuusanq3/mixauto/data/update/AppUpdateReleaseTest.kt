package com.kyuusanq3.mixauto.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateReleaseTest {

    @Test
    fun headunitSuffixDoesNotLookOlderThanTheSameRelease() {
        assertFalse(isNewerRelease("max-auto-v0.1.15", "0.1.15-headunit", "max-auto-v"))
        assertFalse(isNewerRelease("0.1.15", "0.1.15-headunit", "max-auto-v"))
    }

    @Test
    fun newerMaxAutoTagBeatsInstalledHeadunitBuild() {
        assertTrue(isNewerRelease("max-auto-v0.1.16", "0.1.15-headunit", "max-auto-v"))
        assertEquals("0.1.16", normalizeReleaseVersion("max-auto-v0.1.16", "max-auto-v"))
    }

    @Test
    fun standardTagsStillCompareNumerically() {
        assertTrue(isNewerRelease("v0.1.16", "0.1.15", ""))
        assertFalse(isNewerRelease("0.1.15", "0.1.15", ""))
    }

    @Test
    fun newestMatchingTagIgnoresMixAutoReleasesAndMissingAssets() {
        val releases = listOf(
            ReleaseCandidate("0.1.16", draft = false, assetNames = listOf("mix-auto.apk")),
            ReleaseCandidate("max-auto-v0.1.15", draft = false, assetNames = listOf("max-auto.apk")),
            ReleaseCandidate("max-auto-v0.1.16", draft = false, assetNames = listOf("mix-auto.apk")),
            ReleaseCandidate("max-auto-v0.1.17", draft = true, assetNames = listOf("max-auto.apk")),
        )
        assertEquals(
            "max-auto-v0.1.15",
            newestMatchingTag(releases, "max-auto-v", "max-auto.apk"),
        )
    }

    @Test
    fun noMatchingTagReturnsNull() {
        val releases = listOf(
            ReleaseCandidate("0.1.15", draft = false, assetNames = listOf("mix-auto.apk")),
        )
        assertNull(newestMatchingTag(releases, "max-auto-v", "max-auto.apk"))
    }
}
