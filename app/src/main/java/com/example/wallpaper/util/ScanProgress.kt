package com.example.wallpaper.util

data class ScanProgress(
    val scanned: Int = 0,
    val total: Int = 0,
    val currentFolderName: String = "",
    val running: Boolean = false
)
