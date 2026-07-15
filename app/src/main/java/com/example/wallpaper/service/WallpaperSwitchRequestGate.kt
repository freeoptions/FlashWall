package com.example.wallpaper.service

internal class WallpaperSwitchRequestGate {
    private var activeRequestId = 0L

    @Synchronized
    fun beginRequest(): Long {
        activeRequestId += 1
        return activeRequestId
    }

    @Synchronized
    fun canApply(requestId: Long): Boolean {
        return requestId == activeRequestId
    }

    @Synchronized
    fun complete(requestId: Long) {
        if (requestId == activeRequestId) {
            activeRequestId += 1
        }
    }
}
