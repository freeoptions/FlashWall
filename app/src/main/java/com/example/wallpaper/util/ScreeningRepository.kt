package com.example.wallpaper.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.wallpaper.data.Folder
import com.example.wallpaper.data.Wallpaper
import com.example.wallpaper.data.WallpaperDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Indexes one fixed folder for the screening tab while keeping FlashWall's
 * existing wallpaper and marked state as the source of truth.
 */
class ScreeningRepository(
    private val context: Context,
    private val dao: WallpaperDao
) {
    private val wallpaperPicker = WallpaperPicker(context)

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif",
        "avif", "jxl", "tif", "tiff", "jfif", "apng"
    )

    suspend fun scan(
        folder: Folder,
        includeSubfolders: Boolean,
        excludeDirectory: String?,
        onProgress: suspend (ScanProgress) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        documentFileOf(folder.uri)?.let { documentRoot ->
            return@withContext scanDocumentTree(
                folder = folder,
                root = documentRoot,
                includeSubfolders = includeSubfolders,
                excludeDirectory = excludeDirectory,
                onProgress = onProgress
            )
        }

        val root = fileOf(folder.uri)?.canonicalFile
        if (root == null || !root.isDirectory) {
            throw IllegalArgumentException("文件夹不存在或不可访问")
        }

        val scanStarted = System.currentTimeMillis()
        val excluded = fileOf(excludeDirectory)?.canonicalFile
        val existing = dao.getAllWallpapersOnce().associateBy { normalizePath(it.path) }
        // 文件夹页和壁纸轮播使用的是 MediaStore 的 DATA + MIME 查询。
        // 筛选页优先复用这份索引，避免把 /storage/... 路径规范化后误判为不在目录内。
        val indexedImagePaths = wallpaperPicker.getImagePathsInFolder(
            folderPath = folder.uri,
            includeSubfolders = includeSubfolders
        )
        currentCoroutineContext().ensureActive()
        var scanned = 0
        var candidates = 0
        var completed = 0
        val batch = ArrayList<Wallpaper>(200)

        onProgress(
            ScanProgress(
                scanned = 0,
                currentFolderName = folder.name,
                running = true,
                stage = "准备扫描",
                currentPath = root.path,
                candidates = 0,
                completed = 0,
                total = indexedImagePaths.size
            )
        )

        val candidatesToScan: Sequence<ScanCandidate> = if (indexedImagePaths.isNotEmpty()) {
            indexedImagePaths.asSequence().mapNotNull { path ->
                fileOf(path)?.let { file ->
                    ScanCandidate(
                        file = file,
                        storedPath = toStoredPath(path),
                        alreadyIdentifiedAsImage = true
                    )
                }
            }
        } else if (includeSubfolders) {
            root.listFiles()
                ?: throw IllegalStateException("无法读取目录内容，请检查存储权限")
            root.walkTopDown()
                .onEnter { directory -> !isInside(directory, excluded) }
                .filter { it.isFile }
                .map { file -> ScanCandidate(file = file, storedPath = null) }
        } else {
            val directFiles = root.listFiles()
                ?: throw IllegalStateException("无法读取目录内容，请检查存储权限")
            directFiles.asSequence()
                .filter { it.isFile }
                .map { file -> ScanCandidate(file = file, storedPath = null) }
        }

        for (candidate in candidatesToScan) {
            currentCoroutineContext().ensureActive()
            val file = candidate.file
            scanned++
            if (isInside(file, excluded) ||
                (!candidate.alreadyIdentifiedAsImage && !isImageFile(file))
            ) {
                continue
            }

            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: continue
            val storedPath = candidate.storedPath ?: toStoredPath(canonical)
            val previous = existing[normalizePath(storedPath)] ?: existing[storedPath]
            candidates++
            batch += Wallpaper(
                id = previous?.id ?: 0L,
                path = storedPath,
                folderId = folder.id,
                isMarked = previous?.isMarked ?: false,
                lastSeen = System.currentTimeMillis().coerceAtLeast(scanStarted + 1L)
            )
            completed++

            if (batch.size >= 200) {
                dao.insertWallpapers(batch.toList())
                batch.clear()
            }

            if (scanned % 50 == 0) {
                onProgress(
                    ScanProgress(
                        scanned = scanned,
                        currentFolderName = folder.name,
                        running = true,
                        stage = "读取图片",
                        currentPath = canonical.path,
                        candidates = candidates,
                        completed = completed,
                        total = indexedImagePaths.size
                    )
                )
            }
        }

        if (batch.isNotEmpty()) dao.insertWallpapers(batch)
        dao.deleteStaleWallpapers(folder.id, scanStarted)

        onProgress(
            ScanProgress(
                scanned = scanned,
                currentFolderName = folder.name,
                running = false,
                stage = "扫描完成",
                currentPath = root.path,
                candidates = candidates,
                completed = completed,
                total = indexedImagePaths.size
            )
        )
        completed
    }

    private suspend fun scanDocumentTree(
        folder: Folder,
        root: DocumentFile,
        includeSubfolders: Boolean,
        excludeDirectory: String?,
        onProgress: suspend (ScanProgress) -> Unit
    ): Int {
        if (!root.exists() || !root.isDirectory) {
            throw IllegalArgumentException("文件夹不存在或不可访问")
        }

        val scanStarted = System.currentTimeMillis()
        val existing = dao.getAllWallpapersOnce().associateBy { it.path }
        val excludedDocumentId = documentIdOf(excludeDirectory)
        var scanned = 0
        var candidates = 0
        var completed = 0
        val batch = ArrayList<Wallpaper>(200)

        onProgress(
            ScanProgress(
                currentFolderName = folder.name,
                running = true,
                stage = "准备扫描",
                currentPath = folder.name
            )
        )

        suspend fun visit(directory: DocumentFile) {
            directory.listFiles()
                .sortedBy { it.name.orEmpty().lowercase() }
                .forEach { child ->
                    scanned++
                    val childUri = child.uri.toString()
                    if (isExcludedDocument(childUri, excludedDocumentId)) return@forEach

                    if (child.isDirectory) {
                        if (includeSubfolders) visit(child)
                        return@forEach
                    }

                    val extension = child.name
                        ?.substringAfterLast('.', "")
                        ?.lowercase()
                        .orEmpty()
                    val isImage = child.type?.startsWith("image/", ignoreCase = true) == true ||
                        extension in imageExtensions ||
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                            ?.startsWith("image/", ignoreCase = true) == true
                    if (child.isDirectory || !isImage) return@forEach

                    val previous = existing[childUri]
                    candidates++
                    batch += Wallpaper(
                        id = previous?.id ?: 0L,
                        path = childUri,
                        folderId = folder.id,
                        isMarked = previous?.isMarked ?: false,
                        lastSeen = System.currentTimeMillis().coerceAtLeast(scanStarted + 1L)
                    )
                    completed++

                    if (batch.size >= 200) {
                        dao.insertWallpapers(batch.toList())
                        batch.clear()
                    }

                    if (scanned % 50 == 0) {
                        onProgress(
                            ScanProgress(
                                scanned = scanned,
                                currentFolderName = folder.name,
                                running = true,
                                stage = "读取图片",
                                currentPath = child.name.orEmpty(),
                                candidates = candidates,
                                completed = completed
                            )
                        )
                    }
                }
        }

        visit(root)
        if (batch.isNotEmpty()) dao.insertWallpapers(batch)
        dao.deleteStaleWallpapers(folder.id, scanStarted)

        onProgress(
            ScanProgress(
                scanned = scanned,
                currentFolderName = folder.name,
                running = false,
                stage = "扫描完成",
                currentPath = folder.name,
                candidates = candidates,
                completed = completed
            )
        )
        return completed
    }

    private fun documentFileOf(rawValue: String?): DocumentFile? {
        if (rawValue.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(rawValue) }.getOrNull() ?: return null
        if (!uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) return null

        return runCatching {
            if (DocumentsContract.isTreeUri(uri)) {
                DocumentFile.fromTreeUri(context, uri)
            } else {
                DocumentFile.fromSingleUri(context, uri)
            }
        }.getOrNull()
    }

    private fun documentIdOf(rawValue: String?): String? {
        val uri = runCatching { Uri.parse(rawValue.orEmpty()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) return null
        return runCatching {
            if (DocumentsContract.isTreeUri(uri)) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                DocumentsContract.getDocumentId(uri)
            }
        }.getOrNull()
    }

    private fun isExcludedDocument(documentUri: String, excludedDocumentId: String?): Boolean {
        if (excludedDocumentId.isNullOrBlank()) return false
        val documentId = documentIdOf(documentUri) ?: return false
        return documentId == excludedDocumentId ||
            documentId.startsWith("$excludedDocumentId/")
    }

    private fun fileOf(rawValue: String?): File? {
        if (rawValue.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(rawValue) }.getOrNull()
        return when {
            uri != null && uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) ->
                uri.path?.let(::File)
            uri?.scheme.isNullOrEmpty() -> File(rawValue)
            else -> null
        }
    }

    private fun toStoredPath(file: File): String = toStoredPath(file.path)

    private fun toStoredPath(path: String): String {
        return if (path.startsWith("file://")) path
        else if (path.startsWith("/")) "file://$path"
        else path
    }

    private data class ScanCandidate(
        val file: File,
        val storedPath: String?,
        val alreadyIdentifiedAsImage: Boolean = false
    )

    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in imageExtensions ||
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?.startsWith("image/", ignoreCase = true) == true
    }

    private fun normalizePath(path: String): String {
        val file = fileOf(path) ?: return path
        return runCatching { file.canonicalPath }.getOrDefault(file.path)
    }

    private fun isInside(file: File, directory: File?): Boolean {
        if (directory == null) return false
        val filePath = runCatching { file.canonicalPath }.getOrNull() ?: return false
        val directoryPath = runCatching { directory.canonicalPath }.getOrNull() ?: return false
        return filePath == directoryPath || filePath.startsWith(directoryPath + File.separator)
    }
}
