package com.example.wallpaper.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "uri") val uri: String,
    val isSelected: Boolean = true,
    val position: Int = 0,
    // 筛选页目录不参与壁纸轮播，也不应被文件夹页的移除操作删除。
    val isScreeningFolder: Boolean = false
)
