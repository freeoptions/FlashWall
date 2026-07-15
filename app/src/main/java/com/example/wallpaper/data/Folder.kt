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
    val position: Int = 0
)
