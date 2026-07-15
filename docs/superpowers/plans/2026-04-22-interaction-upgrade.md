# 交互增强与防误触 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现桌面双击标记功能的跨页误触拦截，并支持用户自定义热区编辑器的参考背景图。

**Architecture:** 
1. 在 `MyWallpaperService` 中监听 `onOffsetsChanged` 以锁定当前桌面页。
2. 在 `MainActivity` 中增加参考图选择与权限持久化逻辑。
3. 更新 `HotspotEditorDialog` 以支持自定义图片加载。

**Tech Stack:** Kotlin, Jetpack Compose, SharedPreferences, Android SAF (Storage Access Framework).

---

### Task 1: 跨页误触拦截逻辑 (MyWallpaperService.kt)

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/service/MyWallpaperService.kt`

- [ ] **Step 1: 增加偏移量监听**
在 `MyEngine` 类中增加变量并重写回调：
```kotlin
private var lastXOffset: Float = 0f
override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
    lastXOffset = xOffset
}
```

- [ ] **Step 2: 在点击时记录并校验偏移量**
修改 `onTouchEvent`：
```kotlin
// 第一次点击时记录
private var touchXOffset: Float = 0f
// ... 在 ACTION_DOWN 且判定为双击第一下时
touchXOffset = lastXOffset

// 第二次点击时校验
val prefs = applicationContext.getSharedPreferences("settings", MODE_PRIVATE)
val disableOnSwipe = prefs.getBoolean("disable_mark_on_swipe", true)
if (disableOnSwipe && Math.abs(lastXOffset - touchXOffset) > 0.001f) {
    Log.d(TAG, "Swipe detected between taps, ignoring double tap")
    lastTouchTime = 0
    return
}
```

- [ ] **Step 3: 提交修改**
```bash
git add app/src/main/java/com/example/wallpaper/service/MyWallpaperService.kt
git commit -m "feat: add same-page lock to prevent mark on swipe"
```

### Task 2: 设置页功能扩展 (MainActivity.kt)

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/ui/MainActivity.kt`

- [ ] **Step 1: 增加 UI 配置项**
在 `SettingsScreen` 中增加“仅在同一桌面页生效”开关和“选择参考图”入口。
- [ ] **Step 2: 实现参考图选择逻辑**
使用 `ActivityResultContracts.OpenDocument` 并调用 `takePersistableUriPermission`。
- [ ] **Step 3: 更新导入导出逻辑**
在 JSON 导出/导入中加入 `disable_mark_on_swipe` 和 `hotspot_reference_uri`。

### Task 3: 编辑器背景优化 (MainActivity.kt)

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/ui/MainActivity.kt`

- [ ] **Step 1: 修改 HotspotEditorDialog 背景加载逻辑**
```kotlin
val referenceUri = prefs.getString("hotspot_reference_uri", "")
// 优先加载 referenceUri 对应的图片
// 如果为空，显示 MockDesktopOverlay
```

- [ ] **Step 2: 提交 UI 修改**
```bash
git add app/src/main/java/com/example/wallpaper/ui/MainActivity.kt
git commit -m "feat: add reference image picker and settings migration update"
```

---

## Verification Plan

### 1. 防误触验证
- 在桌面页 1 点击一下，迅速滑到页 2 点击第二下。
- 观察 Logcat：应显示 `Swipe detected between taps, ignoring double tap`。
- 确认没有弹出“标记成功”提示。

### 2. 参考图验证
- 在设置页点击“选择参考截图”，选一张带有桌面图标的截图。
- 进入“框定区域”，确认背景变成了选中的截图。
- 确认拖拽矩形能对齐截图中的位置。

### 3. 迁移验证
- 导出设置到文件，确认 JSON 中包含新字段。
- 修改设置后导入，确认新字段正确恢复。
