package com.example.wakeonwanremotephone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun detectsNormalVersionUpgrades() {
        assertTrue(UpdateManager.isNewerVersion("1.8.0", "1.7.1"))
        assertTrue(UpdateManager.isNewerVersion("1.10.0", "1.9.9"))
        assertTrue(UpdateManager.isNewerVersion("2.0", "1.99.99"))
        assertTrue(UpdateManager.isNewerVersion("1.7.1.1", "1.7.1"))
    }

    @Test
    fun equalOrOlderVersionsAreNotNewer() {
        assertFalse(UpdateManager.isNewerVersion("1.7.1", "1.7.1"))
        assertFalse(UpdateManager.isNewerVersion("1.7.0", "1.7.1"))
        assertFalse(UpdateManager.isNewerVersion("1.7", "1.7.0"))
        assertFalse(UpdateManager.isNewerVersion("", "1.7.1"))
    }

    @Test
    fun stripsLeadingVPrefixAndBuildMetadata() {
        assertTrue(UpdateManager.isNewerVersion("V1.8.0", "1.7.1"))
        assertTrue(UpdateManager.isNewerVersion("v1.8.0", "1.7.1"))
        assertTrue(UpdateManager.isNewerVersion("1.8.0+build.42", "1.7.1"))
        assertFalse(UpdateManager.isNewerVersion("v1.7.1+hotfix", "1.7.1"))
    }

    @Test
    fun prereleaseSuffixIsIgnoredWhenComparingNumbers() {
        // A prerelease of the next version is still an upgrade over the current stable.
        assertTrue(UpdateManager.isNewerVersion("1.8.0-beta.1", "1.7.1"))
        // A prerelease of the same version is not newer than the stable release.
        assertFalse(UpdateManager.isNewerVersion("1.8.0-beta.1", "1.8.0"))
        assertFalse(UpdateManager.isNewerVersion("1.7.1-rc.1", "1.7.1"))
    }
}
