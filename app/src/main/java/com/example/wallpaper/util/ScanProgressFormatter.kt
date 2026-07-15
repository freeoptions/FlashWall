package com.example.wallpaper.util

object ScanProgressFormatter {
    fun format(scanned: Int, total: Int): String {
        if (total <= 0) {
            return "已扫描 ${scanned} 张"
        }
        val percent = ((scanned.coerceAtLeast(0) * 100f) / total).toInt().coerceIn(0, 100)
        return "扫描进度: ${percent}% (${scanned}/${total})"
    }
}
