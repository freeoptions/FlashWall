package com.example.wallpaper.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wallpapers",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["folderId"]),
        Index(value = ["isMarked"])
    ]
)
data class Wallpaper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val folderId: Long,
    val isMarked: Boolean = false,
    val lastSeen: Long = 0
)
