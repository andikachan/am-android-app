package com.alightweb.player.model

data class PresetItem(
    val title: String,
    val fileName: String,
    val isAsset: Boolean = true,
    val fullPath: String = "",
    val sizeBytes: Long = 0L,
    val durationText: String = "5.0s",
    val layerCount: Int = 0
)
