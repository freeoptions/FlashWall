package com.example.wallpaper.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder as FolderIcon
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.wallpaper.R
import com.example.wallpaper.data.Folder
import com.example.wallpaper.data.Wallpaper
import com.example.wallpaper.util.ScanProgress
import com.example.wallpaper.util.ScreeningRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val PREF_SCREENING_FOLDER_URI = "screening_folder_uri"
private const val PREF_SCREENING_IMMERSIVE = "screening_immersive"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScreeningScreen() {
    val context = LocalContext.current
    val database = remember { com.example.wallpaper.data.AppDatabase.getDatabase(context) }
    val dao = remember { database.wallpaperDao() }
    val repository = remember { ScreeningRepository(context, dao) }
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    // 筛选目录和轮播目录使用不同的查询语义：筛选页可以读取自己保留的目录记录。
    val folders by dao.getAllFoldersForScreening().collectAsState(initial = emptyList())

    var selectedFolderUri by rememberSaveable {
        mutableStateOf(prefs.getString(PREF_SCREENING_FOLDER_URI, null))
    }
    var immersivePreview by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PREF_SCREENING_IMMERSIVE, true))
    }
    var showFolderBrowser by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(ScanProgress()) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val scanGeneration = remember { AtomicLong(0L) }

    val imagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(
                context,
                "未获得图片读取权限，无法扫描文件夹",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val selectedFolder = folders.firstOrNull { it.uri == selectedFolderUri }
        ?: if (selectedFolderUri.isNullOrBlank()) folders.firstOrNull() else null
    val imagesFlow = remember(selectedFolder?.id) {
        selectedFolder?.let { dao.getWallpapersByFolder(it.id) } ?: flowOf(emptyList())
    }
    val images by imagesFlow.collectAsState(initial = emptyList())

    LaunchedEffect(folders, selectedFolder?.uri) {
        val nextUri = selectedFolder?.uri
        if (nextUri != null && nextUri != selectedFolderUri) {
            selectedFolderUri = nextUri
            prefs.edit().putString(PREF_SCREENING_FOLDER_URI, nextUri).apply()
        }
    }

    fun selectFolderPath(path: String) {
        // 切换目录时让旧扫描失效，避免旧目录的进度和 Toast 串到新目录。
        scanGeneration.incrementAndGet()
        scanJob?.cancel()
        scanJob = null
        scanProgress = ScanProgress()
        scope.launch(Dispatchers.IO) {
            val existingFolder = dao.getFolderByUri(path)
                ?: dao.getScreeningFolderByUri(path)
            val folder = existingFolder ?: run {
                val folderName = java.io.File(path).name.ifBlank { "内部存储" }
                val folderId = dao.insertFolder(
                    Folder(
                        name = folderName,
                        uri = path,
                        isSelected = false,
                        isScreeningFolder = true
                    )
                )
                Folder(
                    id = folderId,
                    name = folderName,
                    uri = path,
                    isSelected = false,
                    isScreeningFolder = true
                )
            }
            withContext(Dispatchers.Main) {
                selectedFolderUri = folder.uri
                prefs.edit().putString(PREF_SCREENING_FOLDER_URI, folder.uri).apply()
                showFolderBrowser = false
                previewIndex = null
            }
        }
    }

    fun scanSelectedFolder() {
        val folder = selectedFolder
        if (folder == null || scanProgress.running || scanJob?.isActive == true) return

        if (!hasScreeningStorageAccess(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                imagePermissionLauncher.launch(permission)
            }
            return
        }

        val requestId = scanGeneration.incrementAndGet()
        // 在进入 IO 查询前就锁住按钮，避免 MediaStore 查询期间再次点击或切换目录。
        scanProgress = ScanProgress(
            currentFolderName = folder.name,
            running = true,
            stage = "准备扫描",
            currentPath = folder.uri
        )

        scanJob = scope.launch {
            try {
                val completed = repository.scan(
                    folder = folder,
                    includeSubfolders = prefs.getBoolean("include_subfolders", true),
                    // 筛选页扫描的是用户明确选中的根目录，不能把“标记移动目录”当成排除目录。
                    // 两者相同的时候，旧逻辑会把整个根目录连同所有图片一起排除，结果就是 0 张。
                    excludeDirectory = null,
                    onProgress = { progress ->
                        if (scanGeneration.get() != requestId) return@scan
                        withContext(Dispatchers.Main) {
                            if (scanGeneration.get() == requestId) scanProgress = progress
                        }
                    }
                )
                if (scanGeneration.get() != requestId) return@launch
                withContext(Dispatchers.Main) {
                    if (scanGeneration.get() == requestId) {
                        android.widget.Toast.makeText(
                            context,
                            "扫描完成：找到 $completed 张图片",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (error: Exception) {
                if (scanGeneration.get() != requestId) return@launch
                withContext(Dispatchers.Main) {
                    if (scanGeneration.get() == requestId) {
                        scanProgress = ScanProgress()
                        android.widget.Toast.makeText(
                            context,
                            "扫描失败：${error.message ?: "未知错误"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    if (previewIndex != null && images.isNotEmpty()) {
        Dialog(
            onDismissRequest = { previewIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                ScreeningPreview(
                    items = images,
                    initialIndex = previewIndex ?: 0,
                    onBack = { previewIndex = null },
                    initialImmersive = immersivePreview,
                    onImmersiveChange = {
                        immersivePreview = it
                        prefs.edit().putBoolean(PREF_SCREENING_IMMERSIVE, it).apply()
                    },
                    onMark = { image ->
                        scope.launch(Dispatchers.IO) {
                            dao.markWallpaper(image.path, true)
                        }
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("筛选") },
                actions = {
                    IconButton(onClick = ::scanSelectedFolder, enabled = selectedFolder != null && !scanProgress.running) {
                        Icon(Icons.Default.Refresh, contentDescription = "扫描")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                )
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("固定桌面预览", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "在真实桌面布局中判断这张壁纸是否合适",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 30 &&
                                !android.os.Environment.isExternalStorageManager()
                            ) {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                        ).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                    )
                                } catch (error: Exception) {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                        )
                                    )
                                }
                            } else {
                                showFolderBrowser = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !scanProgress.running,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FolderIcon, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            selectedFolder?.let { displayableDirectoryPath(context, it.uri) ?: it.name }
                                ?: "选择图片文件夹",
                            modifier = Modifier.weight(1f),
                            softWrap = true,
                            textAlign = TextAlign.Start
                        )
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${images.size} 张图片", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已标记 ${images.count { it.isMarked }} 张 · ${if (prefs.getBoolean("include_subfolders", true)) "包含子文件夹" else "仅当前文件夹"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = ::scanSelectedFolder,
                    enabled = selectedFolder != null && !scanProgress.running,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (images.isEmpty()) "开始扫描" else "重新扫描")
                }
            }

            if (scanProgress.running) ScreeningProgressCard(scanProgress)

            if (selectedFolder == null) {
                ScreeningEmptyState(
                    title = "请选择筛选目录",
                    description = "点击上方目录按钮，选择要筛选的图片文件夹。"
                )
            } else if (images.isEmpty() && !scanProgress.running) {
                ScreeningEmptyState(
                    title = "这个目录还没有索引",
                    description = "点击“开始扫描”，建立当前目录的图片列表。"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(images, key = { it.path }) { image ->
                        ScreeningThumbnail(
                            image = image,
                            onClick = { previewIndex = images.indexOfFirst { it.path == image.path } }
                        )
                    }
                }
            }
        }
    }

    if (showFolderBrowser) {
        FolderBrowserDialog(
            onDismiss = { showFolderBrowser = false },
            onSelect = ::selectFolderPath
        )
    }
}

private fun hasScreeningStorageAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        return true
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Manifest.permission.READ_EXTERNAL_STORAGE
    } else {
        return true
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun ColumnScope.ScreeningEmptyState(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 18.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(66.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScreeningProgressCard(progress: ScanProgress) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "${progress.stage.ifBlank { "正在扫描" }} · ${progress.currentFolderName}",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "已读取 ${progress.scanned} 个文件 · 候选 ${progress.candidates} 张 · 完成 ${progress.completed} 张",
                style = MaterialTheme.typography.bodySmall
            )
            if (progress.currentPath.isNotBlank()) {
                Text(
                    progress.currentPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ScreeningThumbnail(image: Wallpaper, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (image.isMarked) Modifier.border(
                    BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
    ) {
        AsyncImage(
            model = image.path,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (image.isMarked) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已标记",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(4.dp).size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScreeningPreview(
    items: List<Wallpaper>,
    initialIndex: Int,
    onBack: () -> Unit,
    initialImmersive: Boolean,
    onImmersiveChange: (Boolean) -> Unit,
    onMark: (Wallpaper) -> Unit
) {
    if (items.isEmpty()) {
        onBack()
        return
    }

    var index by remember(items) { mutableIntStateOf(initialIndex.coerceIn(0, items.lastIndex)) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var immersive by rememberSaveable { mutableStateOf(initialImmersive) }
    ScreeningSystemBars(immersive)
    BackHandler(onBack = onBack)

    fun next() {
        index = (index + 1).coerceAtMost(items.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(items.size) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        when {
                            dragDistance < -80f -> next()
                            dragDistance > 80f -> index = (index - 1).coerceAtLeast(0)
                        }
                        dragDistance = 0f
                    }
                )
            }
    ) {
        ScreeningDesktopMock(items[index], Modifier.fillMaxSize())

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
            color = Color.Black.copy(alpha = 0.48f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("${index + 1} / ${items.size}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        java.io.File(items[index].path.removePrefix("file://")).name,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilterChip(
                    selected = immersive,
                    onClick = {
                        immersive = !immersive
                        onImmersiveChange(immersive)
                    },
                    label = { Text(if (immersive) "沉浸式" else "普通") }
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            color = Color.Black.copy(alpha = 0.52f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onMark(items[index])
                        next()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("待删", color = Color.White)
                }
                Button(
                    onClick = ::next,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("保留")
                }
            }
        }
    }
}

@Composable
private fun ScreeningDesktopMock(image: Wallpaper, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val designHeight = 2296f
        val designScale = minOf(maxWidth / 1080f, maxHeight / designHeight)
            val canvasWidth = 1080f * designScale.value
            val canvasHeight = designHeight * designScale.value

            // 保留当前已显示的图片，下一张加载完成后再切换，避免 Coil 清理旧图时露出黑底。
            var displayedPath by remember { mutableStateOf(image.path) }
            var loadingPath by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(image.path) {
                if (image.path != displayedPath) {
                    loadingPath = image.path
                }
            }

            AsyncImage(
                model = displayedPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            loadingPath?.let { targetPath ->
                AsyncImage(
                    model = targetPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = {
                        if (loadingPath == targetPath) {
                            displayedPath = targetPath
                            loadingPath = null
                        }
                    },
                    onError = {
                        if (loadingPath == targetPath) {
                            loadingPath = null
                        }
                    }
                )
            }

            Box(Modifier.size(canvasWidth.dp, canvasHeight.dp)) {
                ScreeningDesktopOverlay(scale = designScale.value)
            }
    }
}

@Composable
private fun ScreeningDesktopOverlay(scale: Float) {
    fun px(value: Float): Dp = (value * scale).dp

    Box(Modifier.fillMaxSize()) {
        Text(
            "20:15",
            modifier = Modifier.offset(px(86f), px(145f)),
            color = Color.Black,
            fontSize = (130f * scale).sp,
            fontWeight = FontWeight.Light
        )
        Text("9月9日 星期二", modifier = Modifier.offset(px(104f), px(382f)), color = Color.Black, fontSize = (36f * scale).sp)
        Text("当前位置 25°C", modifier = Modifier.offset(px(690f), px(382f)), color = Color.Black, fontSize = (36f * scale).sp)
        Text("晴 30°/22°", modifier = Modifier.offset(px(104f), px(460f)), color = Color.Black, fontSize = (36f * scale).sp)
        Text("湿度 28°C", modifier = Modifier.offset(px(774f), px(460f)), color = Color.Black, fontSize = (36f * scale).sp)
        Text("空气质量 111（轻度污染）", modifier = Modifier.offset(px(104f), px(538f)), color = Color.Black, fontSize = (36f * scale).sp)
        Text("风 2.3 米/秒", modifier = Modifier.offset(px(756f), px(538f)), color = Color.Black, fontSize = (36f * scale).sp)

        ScreeningWeatherMoon(Modifier.offset(px(824f), px(168f)).size(px(158f)))

        DesktopRasterIcon(::px, 48f, 700f, "Authenticator", DesktopAsset.AUTHENTICATOR, imageOffsetY = 3f)
        DesktopRasterFolder(::px, 252f, 700f, "B站", DesktopAsset.B_STATION_FOLDER, imageOffsetY = 3f)
        DesktopRasterIcon(::px, 456f, 700f, "baby", DesktopAsset.BABY, imageOffsetY = 3f)
        DesktopRasterFolder(::px, 660f, 700f, "don't skip", DesktopAsset.DONT_SKIP_FOLDER, imageOffsetY = 3f)
        DesktopRasterFolder(::px, 864f, 700f, "小工具", DesktopAsset.SMALL_TOOLS_FOLDER, imageOffsetY = 3f)

        DesktopRasterIcon(::px, 48f, 1015f, "FlashWall", DesktopAsset.FLASHWALL)
        DesktopRasterIcon(::px, 252f, 1015f, "录音机", DesktopAsset.RECORDER)
        DesktopRasterIcon(::px, 456f, 1015f, "微信", DesktopAsset.WECHAT_TOP)
        DesktopRasterIcon(::px, 864f, 1015f, "Share", DesktopAsset.SHARE)

        DesktopRasterIcon(::px, 48f, 1328f, "微信", DesktopAsset.WECHAT_THIRD)
        DesktopRasterIcon(::px, 252f, 1328f, "TODO", DesktopAsset.TODO)
        DesktopRasterIcon(::px, 456f, 1328f, "设置", DesktopAsset.SETTINGS)
        DesktopRasterIcon(::px, 660f, 1328f, "BotFather", DesktopAsset.BOTFATHER)
        DesktopRasterIcon(::px, 864f, 1328f, "企业微信", DesktopAsset.WEWORK)

        DesktopRasterIcon(::px, 48f, 1640f, "文件", DesktopAsset.FILES)
        DesktopRasterIcon(::px, 456f, 1640f, "雪狐", DesktopAsset.CLEANER)
        DesktopRasterIcon(::px, 660f, 1640f, "Komi Store", DesktopAsset.KOMI)
        DesktopRasterIcon(::px, 864f, 1640f, "MT管理器", DesktopAsset.MT)

        DesktopRasterDockIcon(::px, 48f, 2032f, DesktopAsset.CAMERA)
        DesktopRasterDockIcon(::px, 252f, 2032f, DesktopAsset.PHONE)
        DesktopRasterDockIcon(::px, 456f, 2032f, DesktopAsset.GALLERY)
        DesktopRasterDockIcon(::px, 660f, 2032f, DesktopAsset.MARKET)
        DesktopRasterDockIcon(::px, 864f, 2032f, DesktopAsset.TELEGRAM_PORTRAIT)

        Row(
            modifier = Modifier.offset(px(405f), px(1915f)).width(px(290f)),
            horizontalArrangement = Arrangement.spacedBy(px(22f))
        ) {
            repeat(7) { index ->
                Box(
                    Modifier.size(px(if (index == 1) 16f else 12f))
                        .clip(CircleShape)
                        .background(if (index == 1) Color.White else Color.White.copy(alpha = 0.5f))
                )
            }
        }
        Box(
            modifier = Modifier.offset(px(337f), px(2255f)).size(px(400f), px(13f))
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.92f))
        )
    }
}

@Composable
private fun ScreeningWeatherMoon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val unit = size.minDimension
        val center = Offset(size.width * 0.52f, size.height * 0.50f)
        val moon = Path().apply {
            moveTo(center.x + unit * 0.18f, center.y - unit * 0.38f)
            cubicTo(center.x - unit * 0.08f, center.y - unit * 0.35f, center.x - unit * 0.29f, center.y - unit * 0.14f, center.x - unit * 0.27f, center.y + unit * 0.12f)
            cubicTo(center.x - unit * 0.25f, center.y + unit * 0.38f, center.x + unit * 0.03f, center.y + unit * 0.43f, center.x + unit * 0.24f, center.y + unit * 0.29f)
            cubicTo(center.x + unit * 0.02f, center.y + unit * 0.23f, center.x - unit * 0.01f, center.y + unit * 0.02f, center.x + unit * 0.03f, center.y - unit * 0.12f)
            cubicTo(center.x + unit * 0.06f, center.y - unit * 0.25f, center.x + unit * 0.12f, center.y - unit * 0.33f, center.x + unit * 0.18f, center.y - unit * 0.38f)
            close()
        }
        drawPath(moon, Color(0xFFB7CDFF))
    }
}

private enum class DesktopAsset(@androidx.annotation.DrawableRes val resourceId: Int) {
    AUTHENTICATOR(R.drawable.desktop_authenticator),
    B_STATION_FOLDER(R.drawable.desktop_b_station_folder),
    BABY(R.drawable.desktop_baby),
    DONT_SKIP_FOLDER(R.drawable.desktop_dont_skip_folder),
    SMALL_TOOLS_FOLDER(R.drawable.desktop_small_tools_folder),
    FLASHWALL(R.drawable.desktop_flashwall),
    RECORDER(R.drawable.desktop_recorder),
    WECHAT_TOP(R.drawable.desktop_wechat),
    SHARE(R.drawable.desktop_share),
    WECHAT_THIRD(R.drawable.desktop_wechat_2),
    TODO(R.drawable.desktop_todo),
    SETTINGS(R.drawable.desktop_settings),
    BOTFATHER(R.drawable.desktop_botfather),
    WEWORK(R.drawable.desktop_wework),
    FILES(R.drawable.desktop_files),
    CLEANER(R.drawable.desktop_cleaner),
    KOMI(R.drawable.desktop_komi),
    MT(R.drawable.desktop_mt),
    CAMERA(R.drawable.desktop_camera),
    PHONE(R.drawable.desktop_phone),
    GALLERY(R.drawable.desktop_gallery),
    MARKET(R.drawable.desktop_market),
    TELEGRAM_PORTRAIT(R.drawable.desktop_telegram_portrait)
}

@Composable
private fun DesktopRasterIcon(
    px: (Float) -> Dp,
    x: Float,
    y: Float,
    label: String,
    asset: DesktopAsset,
    imageOffsetY: Float = 0f
) {
    Column(
        modifier = Modifier.offset(px(x), px(y)).width(px(180f)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(asset.resourceId),
            contentDescription = label,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset(x = px(-4f), y = px(imageOffsetY)).size(px(144f))
                .clip(RoundedCornerShape(px(31f)))
        )
        Spacer(Modifier.height(px(12f)))
        Text(
            label,
            color = Color.White,
            fontSize = (30f * px(1f).value).sp,
            lineHeight = (35f * px(1f).value).sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DesktopRasterFolder(
    px: (Float) -> Dp,
    x: Float,
    y: Float,
    label: String,
    asset: DesktopAsset,
    imageOffsetY: Float = 0f
) {
    DesktopRasterIcon(px, x, y, label, asset, imageOffsetY)
}

@Composable
private fun DesktopRasterDockIcon(px: (Float) -> Dp, x: Float, y: Float, asset: DesktopAsset) {
    Image(
        painter = painterResource(asset.resourceId),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.offset(px(x - 4f), px(y)).size(px(144f))
            .clip(RoundedCornerShape(px(31f)))
    )
}

@Composable
private fun ScreeningSystemBars(immersive: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    DisposableEffect(immersive) {
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (immersive) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
}
