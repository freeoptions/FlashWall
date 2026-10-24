package com.example.wallpaper.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.wallpaper.data.Wallpaper
import com.example.wallpaper.data.WallpaperDao
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Turns a persisted directory value into text that is safe to show in the UI.
 * The persisted value is intentionally never rewritten here: it remains the
 * original file path or content URI used for subsequent file operations.
 */
internal fun displayableDirectoryPath(context: Context, rawValue: String?): String? {
    if (rawValue.isNullOrBlank()) return null

    val uri = runCatching { Uri.parse(rawValue) }.getOrNull()
    if (uri != null && uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
        return displayableDocumentUri(context, uri)
    }

    val filePath = when {
        uri != null && uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> uri.path
        uri?.scheme.isNullOrEmpty() -> rawValue
        else -> null
    }

    return if (filePath != null) {
        displayableFilePath(filePath)
    } else {
        "已选目录（暂不可解析）"
    }
}

/**
 * Returns only the human-readable file name for a persisted wallpaper path or URI.
 * The original value is never rewritten; it remains the value used by file operations.
 */
internal fun displayableFileName(context: Context, rawValue: String?): String {
    if (rawValue.isNullOrBlank()) return "未命名图片"

    val uri = runCatching { Uri.parse(rawValue) }.getOrNull()
    val providerName = if (uri?.scheme?.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) == true) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull()
    } else {
        null
    }

    val documentName = if (uri?.scheme?.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) == true) {
        runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
    } else {
        null
    }

    val pathName = when {
        uri?.scheme?.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) == true -> uri.path
            ?.substringAfterLast('/')
        uri?.scheme.isNullOrEmpty() -> rawValue.substringAfterLast('/', rawValue).substringAfterLast('\\')
        else -> uri?.lastPathSegment
    }

    return sequenceOf(providerName, documentName, pathName, rawValue.substringAfterLast('/'))
        .mapNotNull { it?.takeIf(String::isNotBlank) }
        .map { Uri.decode(it) }
        .firstOrNull()
        ?: "未命名图片"
}

private fun displayableDocumentUri(context: Context, uri: Uri): String {
    val documentId = runCatching {
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.getTreeDocumentId(uri)
        } else {
            DocumentsContract.getDocumentId(uri)
        }
    }.getOrNull()

    val documentFile = runCatching {
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }.getOrNull()

    val parts = documentId?.split(':', limit = 2).orEmpty()
    val volumeId = parts.firstOrNull().orEmpty()
    val relativePath = parts.getOrNull(1).orEmpty()
    val volumeLabel = when (volumeId.lowercase(Locale.ROOT)) {
        "primary" -> "内部存储"
        "home" -> "主目录"
        else -> Uri.decode(volumeId).ifBlank { "存储" }
    }

    val decodedSegments = relativePath
        .split('/')
        .map { Uri.decode(it) }
        .filter { it.isNotBlank() }

    val documentName = runCatching { documentFile?.name?.takeIf { it.isNotBlank() } }.getOrNull()
    if (volumeId.isBlank() && decodedSegments.isEmpty()) {
        return documentName?.let { "已选目录 / $it" } ?: "已选目录（URI 暂不可用）"
    }

    val canAccessDocument = runCatching { documentFile?.exists() == true }.getOrDefault(false)
    if (canAccessDocument || decodedSegments.isNotEmpty() || volumeId.isNotBlank()) {
        return (listOf(volumeLabel) + decodedSegments).joinToString(" / ")
    }

    return documentName?.let { "已选目录 / $it" } ?: "已选目录（URI 暂不可用）"
}

private fun displayableFilePath(path: String): String {
    val normalizedPath = path.replace('\\', '/')
    val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        .replace('\\', '/')
        .trimEnd('/')

    return when {
        normalizedPath == storageRoot -> "内部存储"
        normalizedPath.startsWith("$storageRoot/") -> {
            val relativePath = normalizedPath.removePrefix("$storageRoot/")
            (listOf("内部存储") + relativePath.split('/').filter(String::isNotBlank))
                .joinToString(" / ")
        }
        normalizedPath.isBlank() -> "已选目录（路径为空）"
        else -> normalizedPath
    }
}

internal fun writeTextToDirectory(
    context: Context,
    rawDirectory: String,
    fileName: String,
    contents: String
): String {
    val uri = runCatching { Uri.parse(rawDirectory) }.getOrNull()
    if (uri != null && uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
        val directory = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("无法解析导出目录")
        if (!directory.isDirectory) throw IOException("导出目录不可用")

        directory.findFile(fileName)?.delete()
        val target = directory.createFile("application/json", fileName)
            ?: throw IOException("无法创建导出文件")
        context.contentResolver.openOutputStream(target.uri)?.use { output ->
            output.write(contents.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("无法写入导出文件")
        return target.name ?: fileName
    }

    val directoryPath = when {
        uri != null && uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> uri.path
        uri?.scheme.isNullOrEmpty() -> rawDirectory
        else -> null
    } ?: throw IOException("导出目录格式无效")

    val directory = File(directoryPath)
    if (!directory.isDirectory) throw IOException("导出目录不可用")
    File(directory, fileName).writeText(contents, Charsets.UTF_8)
    return fileName
}

internal fun isDirectoryAvailable(context: Context, rawDirectory: String): Boolean {
    val uri = runCatching { Uri.parse(rawDirectory) }.getOrNull()
    if (uri != null && uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
        return runCatching {
            DocumentFile.fromTreeUri(context, uri)?.let { it.exists() && it.isDirectory } == true
        }.getOrDefault(false)
    }

    val directoryPath = filePathOf(rawDirectory, uri) ?: return false
    return File(directoryPath).isDirectory
}

/** Checks the real location, including subfolders, rather than a possibly stale folderId. */
internal fun isWallpaperInDirectory(context: Context, rawPath: String, rawDirectory: String): Boolean {
    val imageUri = runCatching { Uri.parse(rawPath) }.getOrNull() ?: return false
    val directoryUri = runCatching { Uri.parse(rawDirectory) }.getOrNull() ?: return false

    fun localFile(rawValue: String, uri: Uri): File? {
        filePathOf(rawValue, uri)?.let { return File(it) }
        // Only Android's external-storage provider has this known filesystem mapping.
        if (uri.authority != "com.android.externalstorage.documents") return null
        val documentId = runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.getDocumentId(uri)
            } else {
                DocumentsContract.getTreeDocumentId(uri)
            }
        }.getOrNull() ?: return null
        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2 || parts[0] != "primary") return null
        return File(Environment.getExternalStorageDirectory(), parts[1])
    }

    val imageFile = localFile(rawPath, imageUri)
    val directoryFile = localFile(rawDirectory, directoryUri)
    if (imageFile != null && directoryFile != null) {
        return runCatching {
            val directoryPath = directoryFile.canonicalPath.trimEnd(File.separatorChar)
            imageFile.canonicalPath.startsWith(directoryPath + File.separator)
        }.getOrDefault(false)
    }

    // Other document providers decide ancestry themselves; opaque IDs are not file paths.
    if (imageUri.scheme != ContentResolver.SCHEME_CONTENT ||
        directoryUri.scheme != ContentResolver.SCHEME_CONTENT ||
        imageUri.authority != directoryUri.authority
    ) return false
    return runCatching {
        val parentUri = if (DocumentsContract.isTreeUri(directoryUri)) {
            DocumentsContract.buildDocumentUriUsingTree(
                directoryUri,
                DocumentsContract.getTreeDocumentId(directoryUri)
            )
        } else {
            directoryUri
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DocumentsContract.isChildDocument(context.contentResolver, parentUri, imageUri)
        } else {
            val parentId = DocumentsContract.getDocumentId(parentUri)
            DocumentsContract.findDocumentPath(context.contentResolver, imageUri)
                ?.path?.dropLast(1)?.contains(parentId) == true
        }
    }.getOrDefault(false)
}

/**
 * Deletes the real wallpaper file while keeping the database operation separate.
 * A content URI is handled through DocumentFile so it is never treated as a file path.
 */
internal fun deleteWallpaperFile(context: Context, rawPath: String): Boolean {
    val uri = runCatching { Uri.parse(rawPath) }.getOrNull() ?: return false
    return when {
        uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) -> {
            runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() == true }
                .getOrDefault(false)
        }

        uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> {
            uri.path?.let(::File)?.delete() == true
        }

        uri.scheme.isNullOrEmpty() -> File(rawPath).delete()
        else -> false
    }
}

/**
 * Rebuilds marked records from the configured marked-move directory.
 * The directory value is kept as the original file path or tree URI.
 */
internal suspend fun syncMarkedMoveDirectory(
    context: Context,
    dao: WallpaperDao,
    rawDirectory: String
) {
    val uri = runCatching { Uri.parse(rawDirectory) }.getOrNull()
    val imagePaths = if (uri != null && uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
        DocumentFile.fromTreeUri(context, uri)
            ?.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && isMarkedArchiveImage(it.name, it.type) }
            ?.map { it.uri.toString() }
            ?.toList()
            .orEmpty()
    } else {
        val directoryPath = filePathOf(rawDirectory, uri)
        File(directoryPath ?: return)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && isMarkedArchiveImage(it.name, null) }
            ?.map { Uri.fromFile(it).toString() }
            ?.toList()
            .orEmpty()
    }

    imagePaths.forEach { path ->
        val existing = dao.getWallpaperByPath(path)
        if (existing == null) {
            dao.insertWallpaper(
                Wallpaper(
                    path = path,
                    folderId = 0,
                    isMarked = true
                )
            )
        } else if (!existing.isMarked) {
            dao.updateWallpaper(existing.copy(isMarked = true))
        }
    }
}

private fun isMarkedArchiveImage(name: String?, mimeType: String?): Boolean {
    if (mimeType?.startsWith("image/", ignoreCase = true) == true) return true
    val extension = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: return false
    return extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
}

/**
 * Moves a wallpaper into either a filesystem directory or a persisted tree URI.
 * A content URI is used for the actual output stream; it is never converted to
 * display text or treated as a filesystem path.
 */
internal fun moveWallpaperToDirectory(
    context: Context,
    sourcePath: String,
    rawDirectory: String
): Boolean = moveWallpaperToDirectoryWithResult(context, sourcePath, rawDirectory) != null

/**
 * Moves a wallpaper and returns the path/URI that should be persisted after the move.
 * The returned value is the real storage reference, never the human-readable directory text.
 */
internal fun moveWallpaperToDirectoryWithResult(
    context: Context,
    sourcePath: String,
    rawDirectory: String
): String? {
    val sourceUri = runCatching { Uri.parse(sourcePath) }.getOrNull() ?: return null
    val sourceFile = when {
        sourceUri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> sourceUri.path?.let(::File)
        sourceUri.scheme.isNullOrEmpty() -> File(sourcePath)
        else -> null
    }
    val sourceDocument = if (sourceFile == null) {
        runCatching { DocumentFile.fromSingleUri(context, sourceUri) }.getOrNull()
    } else {
        null
    }
    val sourceName = sourceFile?.name
        ?: sourceDocument?.name
        ?: "image_${System.currentTimeMillis()}.jpg"

    val targetUri = runCatching { Uri.parse(rawDirectory) }.getOrNull()
    if (targetUri != null && targetUri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
        val targetDirectory = DocumentFile.fromTreeUri(context, targetUri) ?: return null
        if (!targetDirectory.exists() || !targetDirectory.isDirectory) return null

        val existingTarget = targetDirectory.findFile(sourceName)
        if (existingTarget?.uri == sourceUri) return sourcePath
        val targetFile = existingTarget
            ?: targetDirectory.createFile("image/*", sourceName)
            ?: return null
        val copied = runCatching {
            val input = sourceFile?.inputStream() ?: context.contentResolver.openInputStream(sourceUri)
            input?.use { source ->
                context.contentResolver.openOutputStream(targetFile.uri)?.use { output ->
                    source.copyTo(output)
                    true
                } ?: false
            } ?: false
        }.getOrDefault(false)
        if (!copied) {
            if (existingTarget == null) targetFile.delete()
            return null
        }

        val deleted = sourceFile?.delete() ?: (sourceDocument?.delete() == true)
        if (!deleted && existingTarget == null) targetFile.delete()
        return if (deleted) targetFile.uri.toString() else null
    }

    val targetDirectoryPath = filePathOf(rawDirectory, targetUri) ?: return null
    val targetDirectory = File(targetDirectoryPath)
    if (!targetDirectory.isDirectory) return null
    val destination = File(targetDirectory, sourceName)

    if (sourceFile != null && sourceFile.exists()) {
        if (runCatching { sourceFile.canonicalFile == destination.canonicalFile }.getOrDefault(false)) {
            return sourcePath
        }
        return if (sourceFile.renameTo(destination)) Uri.fromFile(destination).toString() else null
    }

    val sourceDocumentFile = sourceDocument ?: return null
    val copied = runCatching {
        val input = context.contentResolver.openInputStream(sourceUri)
        input?.use { source ->
            destination.outputStream().use { output -> source.copyTo(output) }
            true
        } ?: false
    }.getOrDefault(false)
    if (!copied) {
        destination.delete()
        return null
    }
    val deleted = sourceDocumentFile.delete()
    if (!deleted) destination.delete()
    return if (deleted) Uri.fromFile(destination).toString() else null
}

private fun filePathOf(rawValue: String, uri: Uri?): String? {
    return when {
        uri != null && uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> uri.path
        uri?.scheme.isNullOrEmpty() -> rawValue
        else -> null
    }
}
