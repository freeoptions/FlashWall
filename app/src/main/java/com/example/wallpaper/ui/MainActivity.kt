package com.example.wallpaper.ui

import android.util.Log
import android.app.WallpaperManager
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.wallpaper.data.AppDatabase
import com.example.wallpaper.data.Folder
import com.example.wallpaper.service.MyWallpaperService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val PREF_HIDE_FROM_RECENTS = "hide_from_recents"
private const val PREF_MARKED_MOVE_TARGET_PATH = "marked_move_target_path"
private const val DEFAULT_INTERVAL_SECONDS = 5

class MainActivity : ComponentActivity() {
    private var isOpeningWallpaperSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlashWallTheme {
                MainApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isOpeningWallpaperSettings = false
    }

    override fun onStop() {
        super.onStop()
        val hideFromRecents = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(PREF_HIDE_FROM_RECENTS, false)
        if (hideFromRecents && !isOpeningWallpaperSettings && !isFinishing) {
            finishAndRemoveTask()
        }
    }

    fun markOpeningWallpaperSettings() {
        isOpeningWallpaperSettings = true
    }

    fun clearOpeningWallpaperSettings() {
        isOpeningWallpaperSettings = false
    }
}

private fun openLiveWallpaperSettings(context: Context) {
    (context as? MainActivity)?.markOpeningWallpaperSettings()
    val componentName = ComponentName(context, MyWallpaperService::class.java)
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, componentName)
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to open live wallpaper settings", e)
        Toast.makeText(context, "无法打开系统壁纸设置: ${e.message}", Toast.LENGTH_LONG).show()
        (context as? MainActivity)?.clearOpeningWallpaperSettings()
    }
}

private fun removeCurrentTaskFromRecents(context: Context) {
    val activity = context as? android.app.Activity
    if (activity == null) {
        Toast.makeText(context, "当前界面无法从多任务移除", Toast.LENGTH_SHORT).show()
        return
    }
    activity.finishAndRemoveTask()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "文件夹") },
                    label = { Text("文件夹") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Image, contentDescription = "筛选") },
                    label = { Text("筛选") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.FavoriteBorder, contentDescription = "标记中心") },
                    label = { Text("标记") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> FolderListScreen()
                1 -> ScreeningScreen()
                2 -> MarkedGalleryScreen()
                3 -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun FolderListScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val folders by db.wallpaperDao().getAllFolders().collectAsState(initial = emptyList())
    val totalMarked by db.wallpaperDao().getTotalMarkedFlow().collectAsState(initial = 0)

    var showFolderBrowser by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    val launcherIconUri by remember { mutableStateOf(prefs.getString("launcher_icon_uri", null)) }

    fun requestManageStorage() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    }

    val onFolderSelected: (String) -> Unit = { path ->
        scope.launch {
            val folderFile = File(path)
            db.wallpaperDao().insertFolder(Folder(name = folderFile.name, uri = path))
            showFolderBrowser = false
            prefs.edit().putBoolean("pending_refresh_on_visible", true).apply()
            Toast.makeText(context, "文件夹已添加", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            ScreenHeader(
                title = "文件夹",
                subtitle = "管理壁纸来源，决定轮播内容"
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("标记中心", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$totalMarked 张图片已标记",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "系统毫秒级索引 · 无需同步",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                            requestManageStorage()
                        } else {
                            showFolderBrowser = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("添加文件夹")
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "已选文件夹",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (folders.isNotEmpty()) {
                    val allSelected = folders.all { it.isSelected }
                    TextButton(onClick = {
                        scope.launch {
                            db.wallpaperDao().updateAllFoldersSelection(!allSelected)
                            prefs.edit().putBoolean("pending_refresh_on_visible", true).apply()
                        }
                    }) {
                        Text(if (allSelected) "全不选" else "全选")
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (folders.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        "还没有文件夹",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "添加一个目录，FlashWall 就能开始轮播",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    lazyItemsIndexed(folders) { index, folder ->
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = folder.isSelected,
                                    onCheckedChange = {
                                        scope.launch {
                                            db.wallpaperDao()
                                                .insertFolder(folder.copy(isSelected = it))
                                            prefs.edit()
                                                .putBoolean("pending_refresh_on_visible", true)
                                                .apply()
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(folder.name, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "轮播来源",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = {
                                    if (index > 0) {
                                        scope.launch {
                                            val list = folders.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index - 1, item)
                                            val updated =
                                                list.mapIndexed { i, f -> f.copy(position = i) }
                                            db.wallpaperDao().insertFolders(updated)
                                        }
                                    }
                                }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                                }

                                IconButton(onClick = {
                                    if (index < folders.size - 1) {
                                        scope.launch {
                                            val list = folders.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index + 1, item)
                                            val updated =
                                                list.mapIndexed { i, f -> f.copy(position = i) }
                                            db.wallpaperDao().insertFolders(updated)
                                        }
                                    }
                                }, enabled = index < folders.size - 1) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "下移"
                                    )
                                }

                                IconButton(onClick = { folderToDelete = folder }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                }

                Spacer(Modifier.height(88.dp))
            }

            if (folderToDelete != null) {
                AlertDialog(
                    onDismissRequest = { folderToDelete = null },
                    title = { Text("确认移除") },
                    text = { Text("确定要从库中移除文件夹 [${folderToDelete?.name}] 吗？\n(这不会删除您的物理文件)") },
                    confirmButton = {
                        TextButton(onClick = {
                            folderToDelete?.let { folder ->
                                scope.launch {
                                    db.wallpaperDao().deleteFolderFromRotation(
                                        folderId = folder.id,
                                        screeningFolderUri = prefs.getString(
                                            PREF_SCREENING_FOLDER_URI,
                                            null
                                        )
                                    )
                                    prefs.edit().putBoolean("pending_refresh_on_visible", true)
                                        .apply()
                                }
                            }
                            folderToDelete = null
                        }) {
                            Text("确定", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { folderToDelete = null }) {
                            Text("取消")
                        }
                    }
                )
            }

            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                ),
                onClick = {
                    openLiveWallpaperSettings(context)
                }
            ) {
                if (launcherIconUri != null) {
                    AsyncImage(
                        model = launcherIconUri,
                        contentDescription = "FlashWall 启动",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "FlashWall 启动",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (showFolderBrowser) {
                FolderBrowserDialog(
                    onDismiss = { showFolderBrowser = false },
                    onSelect = onFolderSelected
                )
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserDialog(
        onDismiss: () -> Unit,
        onSelect: (String) -> Unit
    ) {
        val context = LocalContext.current
        var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
        val currentFile = File(currentPath)
        val files = remember(currentPath) {
            currentFile.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text("选择图像目录", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            displayableDirectoryPath(context, currentPath) ?: currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (currentPath != Environment.getExternalStorageDirectory().absolutePath) {
                            item {
                                ListItem(
                                    headlineContent = { Text("../ (返回上级)") },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.KeyboardArrowLeft,
                                            null
                                        )
                                    },
                                    modifier = Modifier.combinedClickable(onClick = {
                                        currentFile.parent?.let { currentPath = it }
                                    })
                                )
                            }
                        }

                        items(files) { file ->
                            ListItem(
                                headlineContent = { Text("${file.name}/") },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Menu,
                                        null,
                                        tint = Color(0xFFFFC107)
                                    )
                                },
                                modifier = Modifier.combinedClickable(onClick = {
                                    currentPath = file.absolutePath
                                })
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = { onSelect(currentPath) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("选择这里")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

        var intervalSeconds by remember {
            mutableFloatStateOf(
                prefs.getInt("interval", DEFAULT_INTERVAL_SECONDS).toFloat()
            )
        }
        var includeSubfolders by remember {
            mutableStateOf(
                    prefs.getBoolean(
                    "include_subfolders",
                    true
                )
            )
        }
        var switchOnScreenOn by remember {
            mutableStateOf(
                prefs.getBoolean(
                    "switch_on_screen_on",
                    false
                )
            )
        }
        var hideFromRecents by remember {
            mutableStateOf(
                prefs.getBoolean(
                    PREF_HIDE_FROM_RECENTS,
                    false
                )
            )
        }
        var selectedBorderColorKey by remember {
            mutableStateOf(
                prefs.getString(
                    "selected_border_color",
                    "yellow"
                ) ?: "yellow"
            )
        }
        var launcherIconUri by remember {
            mutableStateOf(
                prefs.getString(
                    "launcher_icon_uri",
                    null
                )
            )
        }
        var markedMoveTargetPath by remember {
            mutableStateOf(
                prefs.getString(
                    PREF_MARKED_MOVE_TARGET_PATH,
                    null
                )
            )
        }
        var exportFolderPath by remember { mutableStateOf(prefs.getString("export_folder", null)) }
        val readableMarkedMoveTargetPath = remember(markedMoveTargetPath) {
            displayableDirectoryPath(context, markedMoveTargetPath)
        }
        val readableExportFolderPath = remember(exportFolderPath) {
            displayableDirectoryPath(context, exportFolderPath)
        }

        val exportFolderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.w("SettingsScreen", "无法持久化导出目录授权", e)
                }
                exportFolderPath = it.toString()
                prefs.edit().putString("export_folder", it.toString()).apply()
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    try {
                        val json = JSONObject().apply {
                            put("interval", prefs.getInt("interval", DEFAULT_INTERVAL_SECONDS))
                            put("include_subfolders", prefs.getBoolean("include_subfolders", true))
                            put(
                                "switch_on_screen_on",
                                prefs.getBoolean("switch_on_screen_on", false)
                            )
                            put(
                                PREF_HIDE_FROM_RECENTS,
                                prefs.getBoolean(PREF_HIDE_FROM_RECENTS, false)
                            )
                            put(
                                "selected_border_color",
                                prefs.getString("selected_border_color", "yellow")
                            )
                            put("launcher_icon_uri", prefs.getString("launcher_icon_uri", null))
                            put(
                                PREF_MARKED_MOVE_TARGET_PATH,
                                prefs.getString(PREF_MARKED_MOVE_TARGET_PATH, null)
                            )
                            put("export_folder", prefs.getString("export_folder", null))
                            put("export_time", System.currentTimeMillis())
                            put("device", android.os.Build.MODEL)
                        }
                        context.contentResolver.openOutputStream(it)?.use { out ->
                            out.write(json.toString(4).toByteArray())
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "FlashWall 设置导出成功", Toast.LENGTH_SHORT)
                                .show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            }
        }

        val iconPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                }
                launcherIconUri = it.toString()
                prefs.edit().putString("launcher_icon_uri", it.toString()).apply()
            }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    try {
                        val content = context.contentResolver.openInputStream(it)?.bufferedReader()
                            ?.use { r -> r.readText() }
                        if (content != null) {
                            val json = JSONObject(content)
                            prefs.edit().apply {
                                if (json.has("interval")) putInt(
                                    "interval",
                                    json.getInt("interval")
                                )
                                if (json.has("include_subfolders")) putBoolean(
                                    "include_subfolders",
                                    json.getBoolean("include_subfolders")
                                )
                                if (json.has("switch_on_screen_on")) putBoolean(
                                    "switch_on_screen_on",
                                    json.getBoolean("switch_on_screen_on")
                                )
                                if (json.has(PREF_HIDE_FROM_RECENTS)) putBoolean(
                                    PREF_HIDE_FROM_RECENTS,
                                    json.getBoolean(PREF_HIDE_FROM_RECENTS)
                                )
                                if (json.has("selected_border_color")) putString(
                                    "selected_border_color",
                                    json.getString("selected_border_color")
                                )
                                if (json.has("launcher_icon_uri")) putString(
                                    "launcher_icon_uri",
                                    json.optString("launcher_icon_uri").ifEmpty { null })
                                if (json.has(PREF_MARKED_MOVE_TARGET_PATH)) putString(
                                    PREF_MARKED_MOVE_TARGET_PATH,
                                    json.optString(PREF_MARKED_MOVE_TARGET_PATH).ifEmpty { null })
                                if (json.has("export_folder")) putString(
                                    "export_folder",
                                    json.optString("export_folder").ifEmpty { null })
                                apply()
                            }
                            withContext(Dispatchers.Main) {
                                intervalSeconds = prefs.getInt("interval", DEFAULT_INTERVAL_SECONDS).toFloat()
                                includeSubfolders = prefs.getBoolean("include_subfolders", true)
                                switchOnScreenOn = prefs.getBoolean("switch_on_screen_on", false)
                                hideFromRecents = prefs.getBoolean(PREF_HIDE_FROM_RECENTS, false)
                                selectedBorderColorKey =
                                    prefs.getString("selected_border_color", "yellow") ?: "yellow"
                                launcherIconUri = prefs.getString("launcher_icon_uri", null)
                                markedMoveTargetPath =
                                    prefs.getString(PREF_MARKED_MOVE_TARGET_PATH, null)
                                exportFolderPath = prefs.getString("export_folder", null)
                                Toast.makeText(
                                    context,
                                    "FlashWall 设置导入成功",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "导入失败，请检查文件格式", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            ScreenHeader(
                title = "设置",
                subtitle = "调整轮播节奏、显示方式与备份目录"
            )
            Text(
                "基础设置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "自动播放",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "每隔一段时间切换下一张壁纸",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        ) {
                            Text(
                                "${intervalSeconds.toInt()} 秒",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Slider(
                        value = intervalSeconds,
                        onValueChange = {
                            intervalSeconds = it
                            prefs.edit().putInt("interval", it.toInt()).apply()
                        },
                        valueRange = 4f..300f,
                        steps = 295,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "4 秒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.64f)
                        )
                        Text(
                            "300 秒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.64f)
                        )
                    }

                    Text(
                        "快速设置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(4, 5, 10).forEach { quickInterval ->
                            val isSelected = intervalSeconds.toInt() == quickInterval
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clickable {
                                        intervalSeconds = quickInterval.toFloat()
                                        prefs.edit().putInt("interval", quickInterval).apply()
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                                    }
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "$quickInterval 秒",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = {
                            intervalSeconds = (intervalSeconds - 1f).coerceAtLeast(4f)
                            prefs.edit().putInt("interval", intervalSeconds.toInt()).apply()
                        }) { Icon(Icons.Default.KeyboardArrowLeft, "减1秒") }

                        OutlinedTextField(
                            value = intervalSeconds.toInt().toString(),
                            onValueChange = {
                                val newValue = it.toIntOrNull()
                                if (newValue != null) {
                                    intervalSeconds = newValue.toFloat().coerceIn(4f, 300f)
                                    prefs.edit().putInt("interval", intervalSeconds.toInt()).apply()
                                }
                            },
                            modifier = Modifier.width(132.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            label = { Text("精确设置") },
                            trailingIcon = {
                                Text(
                                    "秒",
                                    modifier = Modifier.padding(end = 12.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )

                        IconButton(onClick = {
                            intervalSeconds = (intervalSeconds + 1f).coerceAtMost(300f)
                            prefs.edit().putInt("interval", intervalSeconds.toInt()).apply()
                        }) { Icon(Icons.Default.KeyboardArrowRight, "加1秒") }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "播放行为",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsSwitchRow(
                        icon = Icons.Default.List,
                        title = "包含子文件夹",
                        summary = "添加文件夹时是否扫描其子目录",
                        checked = includeSubfolders,
                        onCheckedChange = {
                            includeSubfolders = it
                            prefs.edit().putBoolean("include_subfolders", it).apply()
                        }
                    )
                    Divider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SettingsSwitchRow(
                        icon = Icons.Default.PlayArrow,
                        title = "亮屏/解锁时更换",
                        summary = "每次打开屏幕自动换下一张",
                        checked = switchOnScreenOn,
                        onCheckedChange = {
                            switchOnScreenOn = it
                            prefs.edit().putBoolean("switch_on_screen_on", it).apply()
                        }
                    )
                    Divider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SettingsSwitchRow(
                        icon = Icons.Default.Settings,
                        title = "多任务页面隐藏 App",
                        summary = "开启后自动把当前界面移出多任务",
                        checked = hideFromRecents,
                        onCheckedChange = {
                            hideFromRecents = it
                            prefs.edit().putBoolean(PREF_HIDE_FROM_RECENTS, it).apply()
                            if (it) {
                                removeCurrentTaskFromRecents(context)
                            }
                        }
                    )
                    Divider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                if (launcherIconUri != null) {
                                    AsyncImage(
                                        model = launcherIconUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(18.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("启动轮播图标", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (launcherIconUri != null) "已使用自定义图片" else "使用默认播放图标",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { iconPickerLauncher.launch(arrayOf("image/*")) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("更换", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (launcherIconUri != null) {
                            TextButton(
                                onClick = {
                                    launcherIconUri = null
                                    prefs.edit().remove("launcher_icon_uri").apply()
                                },
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    "恢复默认",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Divider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        var showMarkedMoveFolderBrowser by remember { mutableStateOf(false) }
                        Text("标记移动目录", style = MaterialTheme.typography.titleMedium)
                        Text(
                            readableMarkedMoveTargetPath ?: "未设置，点击移动时会提示选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (markedMoveTargetPath != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showMarkedMoveFolderBrowser = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("选择目录")
                            }
                            if (markedMoveTargetPath != null) {
                                OutlinedButton(
                                    onClick = {
                                        markedMoveTargetPath = null
                                        prefs.edit().remove(PREF_MARKED_MOVE_TARGET_PATH).apply()
                                    }
                                ) {
                                    Text("清除")
                                }
                            }
                        }

                        if (showMarkedMoveFolderBrowser) {
                            FolderBrowserDialog(
                                onDismiss = { showMarkedMoveFolderBrowser = false },
                                onSelect = { path ->
                                    markedMoveTargetPath = path
                                    prefs.edit().putString(PREF_MARKED_MOVE_TARGET_PATH, path)
                                        .apply()
                                    showMarkedMoveFolderBrowser = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text("选中边框颜色", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "长按标记图片时使用的选中提示色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "yellow" to Color(0xFFFFEB3B),
                            "red" to Color(0xFFFF5252),
                            "blue" to Color(0xFF2196F3),
                            "pink" to Color(0xFFFF4081)
                        ).forEach { (key, color) ->
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                    selectedBorderColorKey = key; prefs.edit()
                                    .putString("selected_border_color", key).apply()
                                },
                                shape = CircleShape,
                                color = color,
                                border = BorderStroke(
                                    if (selectedBorderColorKey == key) 3.dp else 1.dp,
                                    if (selectedBorderColorKey == key) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                            ) { }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "备份与迁移",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("导出路径", style = MaterialTheme.typography.titleMedium)
                    Text(
                        readableExportFolderPath ?: "未设置（每次询问）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exportFolderPath != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportFolderPickerLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("选择目录") }
                        if (exportFolderPath != null) {
                            OutlinedButton(onClick = {
                                exportFolderPath = null; prefs.edit().remove("export_folder")
                                .apply()
                            }) { Text("清除") }
                        }
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val timestamp = SimpleDateFormat(
                                "yyyyMMdd_HHmmss",
                                Locale.getDefault()
                            ).format(Date())
                            val fileName = "FlashWall_config_$timestamp.json"
                            val path = exportFolderPath
                            if (path != null) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val json = JSONObject().apply {
                                            put("interval", prefs.getInt("interval", DEFAULT_INTERVAL_SECONDS))
                                            put(
                                                "include_subfolders",
                                                prefs.getBoolean("include_subfolders", true)
                                            )
                                            put(
                                                "switch_on_screen_on",
                                                prefs.getBoolean("switch_on_screen_on", false)
                                            )
                                            put(
                                                PREF_HIDE_FROM_RECENTS,
                                                prefs.getBoolean(PREF_HIDE_FROM_RECENTS, false)
                                            )
                                            put(
                                                "selected_border_color",
                                                prefs.getString("selected_border_color", "yellow")
                                            )
                                            put(
                                                "launcher_icon_uri",
                                                prefs.getString("launcher_icon_uri", null)
                                            )
                                            put(
                                                "export_folder",
                                                prefs.getString("export_folder", null)
                                            )
                                            put("export_time", System.currentTimeMillis())
                                        }
                                        val savedName = writeTextToDirectory(
                                            context = context,
                                            rawDirectory = path,
                                            fileName = fileName,
                                            contents = json.toString(4)
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "设置已导出至: $savedName",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "静默导出失败: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            } else {
                                exportLauncher.launch(fileName)
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("导出设置")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("导入设置")
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun MarkedGalleryScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val db = AppDatabase.getDatabase(context)
        val markedWallpapers by db.wallpaperDao().getMarkedWallpapers()
            .collectAsState(initial = emptyList())

        val selectedPaths = remember { mutableStateListOf<String>() }
        var isMoving by remember { mutableStateOf(false) }
        var previewIndex by remember { mutableStateOf<Int?>(null) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var showFolderBrowserForMove by remember { mutableStateOf(false) }

        val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
        val borderColor = remember(prefs.getString("selected_border_color", "yellow")) {
            when (prefs.getString("selected_border_color", "yellow")) {
                "red" -> Color(0xFFFF5252)
                "blue" -> Color(0xFF2196F3)
                "pink" -> Color(0xFFFF4081)
                else -> Color(0xFFFFEB3B)
            }
        }

        var rememberedTargetPath by remember {
            mutableStateOf(
                prefs.getString(
                    PREF_MARKED_MOVE_TARGET_PATH,
                    null
                )
            )
        }
        val rememberedTargetName = remember(rememberedTargetPath) {
            displayableDirectoryPath(context, rememberedTargetPath)
        }

        fun performMove(targetPath: String) {
            scope.launch(Dispatchers.IO) {
                isMoving = true
                if (isDirectoryAvailable(context, targetPath)) {
                    val list =
                        if (selectedPaths.isEmpty()) markedWallpapers else markedWallpapers.filter { wp ->
                            selectedPaths.contains(wp.path)
                        }
                    list.forEach { wp ->
                        try {
                            if (moveWallpaperToDirectory(context, wp.path, targetPath)) {
                                db.wallpaperDao().deleteWallpaperByPath(wp.path)
                            }
                        } catch (e: Exception) {
                            Log.e("Move", "Error moving ${wp.path}: ${e.message}")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        selectedPaths.clear()
                        Toast.makeText(context, "批量移动完成", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "无法访问目标文件夹，请长按重置", Toast.LENGTH_LONG)
                            .show()
                    }
                }
                isMoving = false
            }
        }

        if (showFolderBrowserForMove) {
            FolderBrowserDialog(
                onDismiss = { showFolderBrowserForMove = false },
                onSelect = { path ->
                    prefs.edit().putString(PREF_MARKED_MOVE_TARGET_PATH, path).apply()
                    rememberedTargetPath = path
                    performMove(path)
                    showFolderBrowserForMove = false
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("确认取消标记") },
                text = { Text("确定要取消所选图片的标记吗？\n(这不会删除物理文件)") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            selectedPaths.forEach { db.wallpaperDao().markWallpaper(it, false) }
                            selectedPaths.clear()
                        }
                        showDeleteConfirm = false
                    }) {
                        Text("确定", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }

        BackHandler(enabled = selectedPaths.isNotEmpty() || previewIndex != null) {
            if (previewIndex != null) previewIndex = null else selectedPaths.clear()
        }

        Scaffold(
            bottomBar = {
                if (markedWallpapers.isNotEmpty()) {
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (selectedPaths.isNotEmpty()) showDeleteConfirm = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = selectedPaths.isNotEmpty(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("取消标记")
                                }

                                val isMoveEnabled = !isMoving
                                Button(
                                    onClick = {
                                        val path = rememberedTargetPath
                                        if (path != null) {
                                            performMove(path)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "请先在设置里配置标记移动目录",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            showFolderBrowserForMove = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f).combinedClickable(
                                        onClick = {
                                            val path = rememberedTargetPath
                                            if (path != null) {
                                                performMove(path)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "请先在设置里配置标记移动目录",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                showFolderBrowserForMove = true
                                            }
                                        },
                                        onLongClick = { showFolderBrowserForMove = true }
                                    ),
                                    enabled = isMoveEnabled,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (selectedPaths.isEmpty()) "全部移动" else "移动所选")
                                }

                                TextButton(
                                    onClick = {
                                        if (selectedPaths.size < markedWallpapers.size) {
                                            selectedPaths.clear()
                                            selectedPaths.addAll(markedWallpapers.map { it.path })
                                        } else {
                                            selectedPaths.clear()
                                        }
                                    }
                                ) {
                                    Text(if (selectedPaths.size < markedWallpapers.size) "全选" else "全不选")
                                }
                            }

                            if (rememberedTargetName != null) {
                                Text(
                                    text = "目标: $rememberedTargetName (长按可修改目录)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 4.dp)
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                ScreenHeader(
                    title = if (selectedPaths.isEmpty()) "标记" else "已选 ${selectedPaths.size} 项",
                    subtitle = if (markedWallpapers.isEmpty()) {
                        "还没有收藏壁纸"
                    } else {
                        "共 ${markedWallpapers.size} 张 · 点击预览，长按选择"
                    }
                )

                if (markedWallpapers.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(34.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "标记的壁纸会出现在这里",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "在壁纸轮播时使用快捷入口即可标记",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        gridItemsIndexed(markedWallpapers) { index, wp ->
                            val isSelected = selectedPaths.contains(wp.path)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (selectedPaths.isEmpty()) previewIndex = index
                                            else {
                                                if (isSelected) selectedPaths.remove(wp.path)
                                                else selectedPaths.add(wp.path)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelected) selectedPaths.add(wp.path)
                                        }
                                    ),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = if (isSelected) BorderStroke(4.dp, borderColor) else null
                            ) {
                                AsyncImage(
                                    model = wp.path,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }

        previewIndex?.let { initial ->
            val pagerState = rememberPagerState(initialPage = initial) { markedWallpapers.size }
            Dialog(
                onDismissRequest = { previewIndex = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Black
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp
                    ) { page ->
                        val wp = markedWallpapers.getOrNull(page)
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .combinedClickable(onClick = { previewIndex = null })
                        ) {
                            if (wp != null) AsyncImage(
                                model = wp.path,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
