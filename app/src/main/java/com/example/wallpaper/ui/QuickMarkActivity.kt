package com.example.wallpaper.ui

import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.wallpaper.R
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.wallpaper.data.AppDatabase
import com.example.wallpaper.service.MyWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickMarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 处理创建快捷方式的请求 (由 FooView 或 桌面发起)
        if (Intent.ACTION_CREATE_SHORTCUT == intent.action) {
            val shortcutIntent = Intent(this, QuickMarkActivity::class.java).apply {
                action = Intent.ACTION_VIEW
            }
            val resultIntent = Intent().apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, "标记当前壁纸")
                val iconResource = Intent.ShortcutIconResource.fromContext(this@QuickMarkActivity, R.mipmap.ic_launcher)
                putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
            return
        }

        val uri = MyWallpaperService.currentWallpaperUri

        if (uri != null) {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val existing = db.wallpaperDao().getWallpaperByPath(uri)
                        if (existing == null) {
                            db.wallpaperDao().insertWallpaper(
                                com.example.wallpaper.data.Wallpaper(
                                    path = uri,
                                    folderId = 0,
                                    isMarked = true
                                )
                            )
                        } else {
                            db.wallpaperDao().markWallpaper(uri, true)
                        }
                    }
                    Toast.makeText(this@QuickMarkActivity, "已标记当前壁纸", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@QuickMarkActivity, "标记失败: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    finish()
                }
            }
        } else {
            Toast.makeText(this, "未能获取当前壁纸路径", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
