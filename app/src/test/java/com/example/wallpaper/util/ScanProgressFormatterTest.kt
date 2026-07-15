package com.example.wallpaper.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanProgressFormatterTest {
    @Test
    fun formatsProgressWithPercentAndCounts() {
        val text = ScanProgressFormatter.format(scanned = 25, total = 100)
        assertEquals("扫描进度: 25% (25/100)", text)
    }

    @Test
    fun formatsIndeterminateWhenTotalUnknown() {
        val text = ScanProgressFormatter.format(scanned = 1851, total = 0)
        assertEquals("已扫描 1851 张", text)
    }
}
