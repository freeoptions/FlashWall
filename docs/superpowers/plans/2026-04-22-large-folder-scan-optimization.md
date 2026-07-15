# 超大目录导入与扫描优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让超大图片目录在导入时立即显示到 UI，并在后台高效完成扫描，避免文件夹选择后卡住或看似失败。

**Architecture:** 目录添加流程拆成“立即入库显示”与“后台异步扫描”两阶段；扫描器从 `DocumentFile.listFiles()` 升级为基于 `DocumentsContract` / `ContentResolver.query(...)` 的文档树枚举；同时补强数据库索引与空选择保护，确保超大目录和多文件夹切换都稳定。

**Tech Stack:** Kotlin, Jetpack Compose, Room, Android SAF (`DocumentsContract`, `ContentResolver`), Coroutines.

---

### Task 1: 让文件夹添加先成功显示，再后台扫描

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/example/wallpaper/util/FileScanner.kt`

- [ ] **Step 1: 在 `SettingsScreen` 现有导入逻辑中确认不是这里改，而是在 `FolderListScreen()` 的目录返回处理处改成先插入文件夹再后台扫描**

把当前同步扫描代码：
```kotlin
val id = db.wallpaperDao().insertFolder(Folder(name = it.lastPathSegment ?: "未知", uri = newUri))
isScanning = true
FileScanner(context).scanFolder(id, newUri)
isScanning = false
```
改成：
```kotlin
val id = db.wallpaperDao().insertFolder(Folder(name = it.lastPathSegment ?: "未知", uri = newUri))
isScanning = true
launch(Dispatchers.IO) {
    FileScanner(context).scanFolder(id, newUri)
    withContext(Dispatchers.Main) {
        isScanning = false
    }
}
withContext(Dispatchers.Main) {
    Toast.makeText(context, "文件夹已添加，正在后台扫描", Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 2: 运行静态检查思路验证这个改动不会阻塞主线程**

检查 `FolderListScreen()` 中：
- `insertFolder(...)` 仍在协程里执行
- `scanFolder(...)` 切到 `Dispatchers.IO`
- `isScanning = false` 回到主线程

预期：目录加到列表后 UI 立即可见，不需要等完整扫描结束。

- [ ] **Step 3: 提交这一阶段修改**

```bash
git add app/src/main/java/com/example/wallpaper/ui/MainActivity.kt app/src/main/java/com/example/wallpaper/util/FileScanner.kt
git commit -m "feat: add folders immediately and scan in background"
```

### Task 2: 用 DocumentsContract 重写大目录扫描

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/util/FileScanner.kt`

- [ ] **Step 1: 写一个新的子节点枚举函数，替换 `DocumentFile.listFiles()`**

在 `FileScanner.kt` 中新增一个内部函数：
```kotlin
private fun queryChildren(uri: Uri): List<Uri> {
    val children = mutableListOf<Uri>()
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        uri,
        DocumentsContract.getDocumentId(uri)
    )
    context.contentResolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        ),
        null,
        null,
        null
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        while (cursor.moveToNext()) {
            val documentId = cursor.getString(idIndex)
            children += DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
        }
    }
    return children
}
```

- [ ] **Step 2: 用 `queryChildren(...)` 替换扫描主循环中的 `listFiles()`**

把原先：
```kotlin
val currentDir = stack.removeAt(stack.size - 1)
val files = currentDir.listFiles()
for (file in files) {
    if (file.isDirectory) { ... } else if (isImage(file)) { ... }
}
```
改成基于 URI 的实现：
```kotlin
val currentUri = stack.removeAt(stack.size - 1)
val childUris = queryChildren(currentUri)
for (childUri in childUris) {
    val doc = DocumentFile.fromSingleUri(context, childUri) ?: continue
    if (doc.isDirectory) {
        if (includeSubfolders) stack.add(childUri)
    } else if (isImage(doc)) {
        count++
        wallpapers.add(Wallpaper(path = childUri.toString(), folderId = folderId))
    }
}
```

- [ ] **Step 3: 保留批量插入逻辑，确认仍然每 500 条写库一次**

确认以下代码仍存在且不变：
```kotlin
if (wallpapers.size >= 500) {
    db.wallpaperDao().insertWallpapers(wallpapers.toList())
    wallpapers.clear()
}
```

- [ ] **Step 4: 提交扫描器重构**

```bash
git add app/src/main/java/com/example/wallpaper/util/FileScanner.kt
git commit -m "perf: speed up large folder scanning with DocumentsContract"
```

### Task 3: 给壁纸与文件夹查询加护航保护

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/data/Wallpaper.kt`
- Modify: `app/src/main/java/com/example/wallpaper/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/wallpaper/data/WallpaperDao.kt`

- [ ] **Step 1: 给 `folderId` 加索引，提升取消勾选后的查询速度**

把 `Wallpaper.kt` 中的实体索引从：
```kotlin
indices = [Index(value = ["path"], unique = true)]
```
改成：
```kotlin
indices = [
    Index(value = ["path"], unique = true),
    Index(value = ["folderId"]),
    Index(value = ["isMarked"])
]
```

- [ ] **Step 2: 提升数据库版本号，继续沿用当前项目的 destructive migration 策略**

把：
```kotlin
@Database(entities = [Wallpaper::class, Folder::class], version = 2, exportSchema = false)
```
改成：
```kotlin
@Database(entities = [Wallpaper::class, Folder::class], version = 3, exportSchema = false)
```

- [ ] **Step 3: 给随机取图 SQL 增加“没有启用文件夹时直接返回 null”的安全保护**

把 `getRandomWallpaper()` 的 SQL 改成带 `CASE WHEN` 或等价保护，避免 `COUNT(*) = 0` 时做 `% 0`：
```kotlin
@Query("""
SELECT * FROM wallpapers
WHERE folderId IN (SELECT id FROM folders WHERE isSelected = 1)
LIMIT 1 OFFSET (
  CASE
    WHEN (SELECT COUNT(*) FROM wallpapers WHERE folderId IN (SELECT id FROM folders WHERE isSelected = 1)) = 0 THEN 0
    ELSE ABS(RANDOM()) % (SELECT COUNT(*) FROM wallpapers WHERE folderId IN (SELECT id FROM folders WHERE isSelected = 1))
  END
)
""")
suspend fun getRandomWallpaper(): Wallpaper?
```
并在服务层调用处接受 `null` 结果。

- [ ] **Step 4: 提交数据库与查询安全修改**

```bash
git add app/src/main/java/com/example/wallpaper/data/Wallpaper.kt app/src/main/java/com/example/wallpaper/data/AppDatabase.kt app/src/main/java/com/example/wallpaper/data/WallpaperDao.kt
git commit -m "perf: optimize wallpaper queries for large libraries"
```

### Task 4: 加强大目录场景下的进度反馈

**Files:**
- Modify: `app/src/main/java/com/example/wallpaper/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/example/wallpaper/util/FileScanner.kt`

- [ ] **Step 1: 在扫描开始和结束时明确更新进度状态**

确认 `FileScanner.kt` 中保留并修正：
```kotlin
_progress.value = ScanProgress(scanned = 0, total = 0, currentFolderName = rootDoc.name ?: "扫描中", running = true)
...
_progress.value = ScanProgress(scanned = count, total = count, currentFolderName = "扫描完成", running = false)
```
必要时把 `currentFolderName` 更新成当前处理文件名或目录名。

- [ ] **Step 2: 在 `FolderListScreen()` 中强化“后台扫描中”的提示语**

在显示进度条区域保留：
```kotlin
Text(
    text = ScanProgressFormatter.format(scanProgress.scanned, scanProgress.total),
    style = MaterialTheme.typography.labelSmall
)
```
并补一行提示：
```kotlin
Text("大目录首次导入可能需要较长时间，已在后台继续处理", style = MaterialTheme.typography.labelSmall)
```

- [ ] **Step 3: 提交反馈优化**

```bash
git add app/src/main/java/com/example/wallpaper/ui/MainActivity.kt app/src/main/java/com/example/wallpaper/util/FileScanner.kt
git commit -m "ux: improve feedback for long-running folder scans"
```

---

## Self-Review

### Spec coverage
- 已覆盖：先加入列表再后台扫描。
- 已覆盖：替换 `DocumentFile.listFiles()` 为 `DocumentsContract` 查询。
- 已覆盖：多文件夹勾选/取消场景下的大库查询优化。
- 已覆盖：无选中文件夹时的随机取图安全保护。
- 已覆盖：长时间扫描过程中的 UI 提示。

### Placeholder scan
- 没有使用 TBD/TODO/类似 Task N 之类占位描述。
- 每个关键改动都给了明确代码片段或命令。

### Type consistency
- 新扫描实现继续使用 `Wallpaper(path = uri.toString(), folderId = folderId)`。
- 查询优化仍围绕 `folderId`、`isSelected`、`isMarked` 这几个现有字段，没有引入未定义字段。

## Verification Plan

1. 选择一个包含 18000 张图片的大目录，点击“使用此文件夹”。
   - 预期：文件夹立即出现在列表里。
   - 预期：Toast 提示“文件夹已添加，正在后台扫描”。
   - 预期：UI 不冻结。

2. 扫描过程中观察统计卡片。
   - 预期：进度条持续推进。
   - 预期：提示文字说明后台仍在处理。

3. 扫描完成后检查总图片数。
   - 预期：库中图片数量显著增加。

4. 同时勾选 A/B/C 三个文件夹，再取消勾选 A。
   - 预期：后续随机壁纸不再来自 A。
   - 预期：取消勾选不会卡死或崩溃，即使 A 中有 20000 张图。

5. 取消勾选所有文件夹后返回桌面。
   - 预期：不会崩溃。
   - 预期：服务跳过本轮切换，直到重新勾选文件夹。
