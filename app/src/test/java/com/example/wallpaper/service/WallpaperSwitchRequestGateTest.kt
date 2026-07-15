package com.example.wallpaper.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperSwitchRequestGateTest {
    @Test
    fun staleRequestCannotApplyAfterNewerRequestStarts() {
        val gate = WallpaperSwitchRequestGate()

        val firstRequest = gate.beginRequest()
        val secondRequest = gate.beginRequest()

        assertFalse(gate.canApply(firstRequest))
        assertTrue(gate.canApply(secondRequest))
    }

    @Test
    fun completedRequestCannotApplyAgain() {
        val gate = WallpaperSwitchRequestGate()

        val request = gate.beginRequest()

        assertTrue(gate.canApply(request))
        gate.complete(request)
        assertFalse(gate.canApply(request))
    }
}
