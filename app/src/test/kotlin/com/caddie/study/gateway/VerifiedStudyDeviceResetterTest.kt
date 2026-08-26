package com.caddie.study.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies deterministic reset ordering and fail-closed acknowledgements. */
class VerifiedStudyDeviceResetterTest {
    @Test
    fun `resets and recreates every study app before returning home`() {
        val calls = mutableListOf<String>()
        val driver = object : StudyDeviceResetDriver {
            override fun sendReset(target: StudyAppResetTarget): StudyResetResult {
                calls += "reset:${target.packageName}"
                return StudyResetResult(completed = true, resultCode = 7, resultData = "ok")
            }

            override fun recreate(packageName: String): Boolean {
                calls += "recreate:$packageName"
                return true
            }

            override fun openHome(): Boolean {
                calls += "home"
                return true
            }
        }
        val target = StudyAppResetTarget("study.app", "study.RESET", 7, "ok")

        assertTrue(VerifiedStudyDeviceResetter(driver, listOf(target)).reset())
        assertEquals(listOf("reset:study.app", "recreate:study.app", "home"), calls)
    }

    @Test
    fun `fails before recreation when a required acknowledgement is wrong`() {
        val calls = mutableListOf<String>()
        val driver = object : StudyDeviceResetDriver {
            override fun sendReset(target: StudyAppResetTarget): StudyResetResult =
                StudyResetResult(completed = true, resultCode = 0, resultData = null)

            override fun recreate(packageName: String): Boolean {
                calls += packageName
                return true
            }

            override fun openHome(): Boolean = true
        }
        val target = StudyAppResetTarget("study.app", "study.RESET", 7, "ok")

        assertFalse(VerifiedStudyDeviceResetter(driver, listOf(target)).reset())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `allows a completed best effort reset without a legacy acknowledgement`() {
        val driver = object : StudyDeviceResetDriver {
            override fun sendReset(target: StudyAppResetTarget) = StudyResetResult(completed = true)
            override fun recreate(packageName: String) = true
            override fun openHome() = true
        }
        val target = StudyAppResetTarget("study.app", "study.RESET")

        assertTrue(VerifiedStudyDeviceResetter(driver, listOf(target)).reset())
    }
}
