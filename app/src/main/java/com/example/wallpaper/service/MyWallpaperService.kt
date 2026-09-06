package com.example.wallpaper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.wallpaper.util.WallpaperPicker
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyWallpaperService : WallpaperService() {
    private val tag = "MyWallpaperService"

    companion object {
        @Volatile
        var currentWallpaperUri: String? = null

        private const val SCREEN_TRIGGER_DEBOUNCE_MS = 1500L
        private const val DEFAULT_INTERVAL_SECONDS = 5
    }

    override fun onCreateEngine(): Engine {
        return MyEngine()
    }

    inner class MyEngine : Engine() {
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val handler = Handler(Looper.getMainLooper())
        private var loadJob: Job? = null

        private var hasRenderedWallpaper = false
        private var preloadedBitmap: Bitmap? = null
        private val switchRequestGate = WallpaperSwitchRequestGate()
        private var lastScreenTriggeredSwitchTime = 0L
        private var visibleStartedAt = 0L
        private var isWallpaperVisible = false

        private fun getLastSwitchTime(): Long {
            val prefs = applicationContext.getSharedPreferences("wallpaper_state", MODE_PRIVATE)
            return prefs.getLong("last_switch_time", 0L)
        }

        private fun getCurrentWallpaperPath(): String? {
            val prefs = applicationContext.getSharedPreferences("wallpaper_state", MODE_PRIVATE)
            return prefs.getString("current_wallpaper_path", null)
        }

        private fun saveCurrentWallpaper(path: String, switchTime: Long? = null) {
            val prefs = applicationContext.getSharedPreferences("wallpaper_state", MODE_PRIVATE)
            val editor = prefs.edit().putString("current_wallpaper_path", path)
            if (switchTime != null) {
                editor.putLong("last_switch_time", switchTime)
            }
            editor.apply()
        }

        private fun clearCurrentWallpaper() {
            val prefs = applicationContext.getSharedPreferences("wallpaper_state", MODE_PRIVATE)
            prefs.edit().remove("current_wallpaper_path").apply()
            currentWallpaperUri = null
        }

        private fun getRemainingDelay(): Long {
            val interval = getPreferredInterval()
            val prefs = applicationContext.getSharedPreferences("wallpaper_state", MODE_PRIVATE)
            val savedDelay = prefs.getLong("remaining_delay_ms", interval)
            return savedDelay.coerceIn(0L, interval)
        }

        private fun saveRemainingDelay(delay: Long) {
            val interval = getPreferredInterval()
            val prefs = applicationContext.getSharedPreferences("wallpaper_state", MODE_PRIVATE)
            prefs.edit().putLong("remaining_delay_ms", delay.coerceIn(0L, interval)).apply()
        }

        private fun resetRemainingDelay() {
            saveRemainingDelay(getPreferredInterval())
        }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val prefs = applicationContext.getSharedPreferences("settings", MODE_PRIVATE)
                if (prefs.getBoolean("switch_on_screen_on", false)) {
                    val now = System.currentTimeMillis()
                    if (now - lastScreenTriggeredSwitchTime >= SCREEN_TRIGGER_DEBOUNCE_MS) {
                        lastScreenTriggeredSwitchTime = now
                        Log.d(tag, "Screen state changed, switching wallpaper")
                        loadNextWallpaper(updateSchedule = true)
                    }
                }
            }
        }

        private val switchRunnable = object : Runnable {
            override fun run() {
                loadNextWallpaper(updateSchedule = true)
            }
        }

        private fun getPreferredInterval(): Long {
            val prefs = applicationContext.getSharedPreferences("settings", MODE_PRIVATE)
            return (prefs.getInt("interval", DEFAULT_INTERVAL_SECONDS).toLong() * 1000L)
                .coerceAtLeast(4000L)
        }

        private fun scheduleNext(delay: Long) {
            handler.removeCallbacks(switchRunnable)
            handler.postDelayed(switchRunnable, delay)
        }

        private fun scheduleNextFromNow() {
            scheduleNext(getPreferredInterval())
        }

        private fun beginVisibleCountdown(delay: Long) {
            val safeDelay = delay.coerceAtLeast(0L)
            if (safeDelay <= 0L) {
                loadNextWallpaper(updateSchedule = true)
            } else {
                visibleStartedAt = System.currentTimeMillis()
                scheduleNext(safeDelay)
            }
        }

        private fun resumeCountdownOrSwitchNow() {
            beginVisibleCountdown(getRemainingDelay())
        }

        private fun pauseVisibleCountdown() {
            handler.removeCallbacks(switchRunnable)
            if (visibleStartedAt <= 0L) return

            val elapsedWhileVisible = (System.currentTimeMillis() - visibleStartedAt).coerceAtLeast(0L)
            val updatedRemaining = (getRemainingDelay() - elapsedWhileVisible).coerceAtLeast(0L)
            saveRemainingDelay(updatedRemaining)
            visibleStartedAt = 0L
        }

        private fun restoreCurrentWallpaperOrStartCycle() {
            val currentPath = getCurrentWallpaperPath()
            if (currentPath != null && isReadableWallpaperPath(currentPath)) {
                drawWallpaper(currentPath)
                currentWallpaperUri = currentPath
                if (isWallpaperVisible) {
                    resumeCountdownOrSwitchNow()
                }
            } else {
                if (currentPath != null) {
                    Log.w(tag, "Saved wallpaper path is no longer readable: $currentPath")
                    clearCurrentWallpaper()
                }
                loadNextWallpaper(updateSchedule = isWallpaperVisible)
            }
        }

        private fun restoreCurrentWallpaperIfPossible() {
            val currentPath = getCurrentWallpaperPath() ?: return
            if (!isReadableWallpaperPath(currentPath)) {
                Log.w(tag, "Saved wallpaper path is no longer readable: $currentPath")
                clearCurrentWallpaper()
                return
            }
            drawWallpaper(currentPath)
            currentWallpaperUri = currentPath
        }

        init {
            setTouchEventsEnabled(false)
            renderFallbackFrame()
            restoreCurrentWallpaperIfPossible()
            if (getLastSwitchTime() == 0L) {
                loadNextWallpaper(updateSchedule = false)
            } else {
                restoreCurrentWallpaperOrStartCycle()
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                applicationContext.registerReceiver(receiver, filter)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            renderFallbackFrame()
            restoreCurrentWallpaperIfPossible()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            preloadedBitmap?.let { renderBitmap(it) } ?: renderFallbackFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            Log.d(tag, "onVisibilityChanged: $visible")
            isWallpaperVisible = visible
            if (visible) {
                preloadedBitmap?.let { renderBitmap(it) } ?: renderFallbackFrame()
                val prefs = applicationContext.getSharedPreferences("settings", MODE_PRIVATE)
                val pendingRefresh = prefs.getBoolean("pending_refresh_on_visible", false)

                if (pendingRefresh) {
                    Log.d(tag, "Pending refresh triggered due to folder change")
                    prefs.edit().putBoolean("pending_refresh_on_visible", false).apply()
                    loadNextWallpaper(updateSchedule = true)
                } else if (!hasRenderedWallpaper || preloadedBitmap == null) {
                    restoreCurrentWallpaperOrStartCycle()
                } else {
                    resumeCountdownOrSwitchNow()
                }
            } else {
                pauseVisibleCountdown()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            handler.removeCallbacksAndMessages(null)
            loadJob?.cancel()
            try {
                applicationContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            scope.cancel()
            recycleBitmap()
        }

        private fun loadNextWallpaper(updateSchedule: Boolean = false) {
            loadJob?.cancel()
            val requestId = switchRequestGate.beginRequest()
            loadJob = scope.launch {
                val picker = WallpaperPicker(applicationContext)
                val nextPath = picker.getRandomWallpaper()
                if (!switchRequestGate.canApply(requestId)) return@launch
                if (nextPath != null) {
                    drawWallpaper(
                        path = nextPath,
                        requestId = requestId,
                        renderFallbackOnFailure = !hasRenderedWallpaper,
                        onRendered = {
                            if (updateSchedule) {
                                val now = System.currentTimeMillis()
                                saveCurrentWallpaper(nextPath, now)
                                resetRemainingDelay()
                                if (isWallpaperVisible) {
                                    visibleStartedAt = now
                                    scheduleNextFromNow()
                                } else {
                                    visibleStartedAt = 0L
                                }
                            } else {
                                saveCurrentWallpaper(nextPath)
                            }
                            currentWallpaperUri = nextPath
                        },
                        onFailed = {
                            if (updateSchedule) {
                                resetRemainingDelay()
                                if (isWallpaperVisible) {
                                    visibleStartedAt = System.currentTimeMillis()
                                    scheduleNextFromNow()
                                } else {
                                    visibleStartedAt = 0L
                                }
                            }
                        }
                    )
                } else {
                    Log.w(tag, "No wallpaper available to draw")
                    if (switchRequestGate.canApply(requestId)) {
                        if (updateSchedule) {
                            resetRemainingDelay()
                            if (isWallpaperVisible) {
                                visibleStartedAt = System.currentTimeMillis()
                                scheduleNextFromNow()
                            } else {
                                visibleStartedAt = 0L
                            }
                        }
                        if (!hasRenderedWallpaper) {
                            renderFallbackFrame()
                        }
                        switchRequestGate.complete(requestId)
                    }
                }
            }
        }

        private fun drawWallpaper(
            path: String,
            requestId: Long = switchRequestGate.beginRequest(),
            renderFallbackOnFailure: Boolean = !hasRenderedWallpaper,
            onRendered: (() -> Unit)? = null,
            onFailed: (() -> Unit)? = null
        ) {
            if (!isReadableWallpaperPath(path)) {
                Log.w(tag, "Wallpaper file is not readable: $path")
                if (switchRequestGate.canApply(requestId)) {
                    if (renderFallbackOnFailure) {
                        renderFallbackFrame()
                    }
                    onFailed?.invoke()
                    switchRequestGate.complete(requestId)
                }
                return
            }

            val imageLoader = ImageLoader(applicationContext)
            val (targetWidth, targetHeight) = getTargetSize()
            val request = ImageRequest.Builder(applicationContext)
                .data(path)
                .size(targetWidth, targetHeight)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .allowHardware(false)
                .build()

            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        imageLoader.execute(request)
                    }
                    if (!switchRequestGate.canApply(requestId)) return@launch
                    if (result is SuccessResult) {
                        val newBitmap = (result.drawable as BitmapDrawable).bitmap
                        recycleBitmap()
                        preloadedBitmap = newBitmap
                        hasRenderedWallpaper = true
                        renderBitmap(newBitmap)
                        onRendered?.invoke()
                        switchRequestGate.complete(requestId)
                    } else {
                        Log.w(tag, "Image request did not return a bitmap: $path")
                        if (renderFallbackOnFailure) {
                            renderFallbackFrame()
                        }
                        onFailed?.invoke()
                        switchRequestGate.complete(requestId)
                    }
                } catch (e: Exception) {
                    if (!switchRequestGate.canApply(requestId)) return@launch
                    Log.e(tag, "Error drawing wallpaper", e)
                    if (renderFallbackOnFailure) {
                        renderFallbackFrame()
                    }
                    onFailed?.invoke()
                    switchRequestGate.complete(requestId)
                }
            }
        }

        private fun isReadableWallpaperPath(path: String): Boolean {
            val filePath = if (path.startsWith("file://")) {
                Uri.parse(path).path
            } else {
                path
            }
            if (filePath.isNullOrBlank()) return false

            val file = File(filePath)
            return file.exists() && file.isFile && file.canRead()
        }

        private fun getTargetSize(): Pair<Int, Int> {
            val frame = surfaceHolder.surfaceFrame
            val displayMetrics = applicationContext.resources.displayMetrics
            val width = frame.width().takeIf { it > 0 } ?: displayMetrics.widthPixels
            val height = frame.height().takeIf { it > 0 } ?: displayMetrics.heightPixels
            return width to height
        }

        private fun renderFallbackFrame() {
            val holder = surfaceHolder
            if (!holder.surface.isValid) return

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.drawColor(Color.BLACK)
            } catch (e: Exception) {
                Log.e(tag, "Fallback render failed", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun renderBitmap(bitmap: Bitmap) {
            val holder = surfaceHolder
            if (!holder.surface.isValid) return

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val dest = Rect(0, 0, canvas.width, canvas.height)
                    val scale: Float
                    var dx = 0f
                    var dy = 0f
                    if (bitmap.width * dest.height() > dest.width() * bitmap.height) {
                        scale = dest.height().toFloat() / bitmap.height.toFloat()
                        dx = (dest.width() - bitmap.width * scale) * 0.5f
                    } else {
                        scale = dest.width().toFloat() / bitmap.width.toFloat()
                        dy = (dest.height() - bitmap.height * scale) * 0.5f
                    }

                    canvas.drawColor(Color.BLACK)
                    canvas.save()
                    canvas.translate(dx, dy)
                    canvas.scale(scale, scale)
                    canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    canvas.restore()
                }
            } catch (e: Exception) {
                Log.e(tag, "Render failed", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun recycleBitmap() {
            preloadedBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
            preloadedBitmap = null
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
        }
    }
}
