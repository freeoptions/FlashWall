# 标记生效范围与快捷标记入口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现“仅首页允许双击标记”的限制，并提供应用快捷方式和外部触发的静默标记入口。

**Architecture:** 
- 在 `MyWallpaperService` 中维护当前壁纸 URI 的静态引用。
- 新增透明 `QuickMarkActivity` 作为统一快捷入口。
- 在 `MyWallpaperService` 的触摸处理逻辑中增加基于 `xOffset` 的过滤。

**Tech Stack:** Kotlin, Android WallpaperService, Room, SharedPreferences, App Shortcuts.

---

### Task 1: 准备数据访问层与 Service 状态引用

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/data/WallpaperDao.kt`
- Modify: `app/src/main/java/com/example/wallpaper/service/MyWallpaperService.kt`

- [ ] **Step 1: 在 WallpaperDao 中增加按路径标记的方法**

```kotlin
@Query("UPDATE wallpapers SET isMarked = 1 WHERE path = :path")
suspend fun markWallpaperByPath(path: String)
```

- [ ] **Step 2: 在 MyWallpaperService 中维护当前壁纸引用**

在 `MyWallpaperService` 的 `Companion Object` 中增加：
```kotlin
@Volatile
var currentWallpaperUri: String? = null
```
在 `updateWallpaper()` 或加载新图逻辑处更新该值。

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/example/wallpaper/data/WallpaperDao.kt \
        app/src/main/java/com/example/wallpaper/service/MyWallpaperService.kt
git commit -m "feat: add markByPath DAO and maintain current wallpaper URI in service"
```

---

### Task 2: 实现 QuickMarkActivity 快捷入口

**Files:**
- Create: `app/src/main/java/com/example/wallpaper/ui/QuickMarkActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/themes.xml` (如果尚未定义透明主题)

- [ ] **Step 1: 定义透明 Activity 主题**

在 `themes.xml` 中添加：
```xml
<style name="Theme.Transparent" parent="Theme.AppCompat.Light.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowContentOverlay">@null</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowIsFloating">true</item>
    <item name="android:backgroundDimEnabled">false</item>
</style>
```

- [ ] **Step 2: 创建 QuickMarkActivity**

逻辑：从 `MyWallpaperService.currentWallpaperUri` 获取 URI，调用 DAO 标记，弹出 Toast，然后 `finish()`。

- [ ] **Step 3: 在 Manifest 中注册 Activity**

```xml
<activity
    android:name=".ui.QuickMarkActivity"
    android:theme="@style/Theme.Transparent"
    android:exported="true"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance" />
```

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/example/wallpaper/ui/QuickMarkActivity.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/values/themes.xml
git commit -m "feat: implement QuickMarkActivity as a silent marking entry point"
```

---

### Task 3: 接入 App Shortcuts 快捷菜单

**Files:**
- Create: `app/src/main/res/xml/shortcuts.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 shortcuts.xml**

```xml
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="mark_current"
        android:enabled="true"
        android:icon="@drawable/ic_mark" 
        android:shortcutShortLabel="@string/shortcut_mark_label"
        android:shortcutLongLabel="@string/shortcut_mark_long_label">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="com.example.wallpaper"
            android:targetClass="com.example.wallpaper.ui.QuickMarkActivity" />
        <categories android:name="android.shortcut.conversation" />
    </shortcut>
</shortcuts>
```
*(注意：需要确保 ic_mark 图标和 string 资源存在)*

- [ ] **Step 2: 在 Manifest 中链接快捷方式资源**

在主 Activity 的 `<meta-data>` 中添加。

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/res/xml/shortcuts.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add app icon shortcut for marking current wallpaper"
```

---

### Task 4: 实现首页限定双击功能

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/example/wallpaper/service/MyWallpaperService.kt`

- [ ] **Step 1: 在设置页增加“仅首页生效”开关**

在 `SettingsScreen` 中增加 `mark_only_on_first_page` 的 `Switch`。

- [ ] **Step 2: 在 Service 中应用过滤逻辑**

在 `MyWallpaperEngine.handleDoubleTap` (或类似命名的逻辑) 中：
```kotlin
val markOnlyOnFirstPage = prefs.getBoolean("mark_only_on_first_page", false)
if (markOnlyOnFirstPage && lastXOffset > 0.01f) {
    return // 忽略非首页的双击
}
```

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/com/example/wallpaper/ui/MainActivity.kt \
        app/src/main/java/com/example/wallpaper/service/MyWallpaperService.kt
git commit -m "feat: add 'mark only on first page' setting and logic"
```

---

### Task 5: 最终验证与回归测试

- [ ] **Step 1: 验证首页限制**
  - 开启开关，确认仅在第一页双击有效。
  - 关闭开关，确认所有页面均有效。

- [ ] **Step 2: 验证快捷标记**
  - 通过长按图标触发，确认 Toast 成功。
  - 模拟 FooView 调用入口类，确认行为一致。

- [ ] **Step 3: 检查代码质量**
  - 确认无明显的资源泄漏（Activity 及时 finish）。
  - 确认 IO 操作都在协程/后台线程。
