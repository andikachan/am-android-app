package com.alightweb.player.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.alightweb.player.animation.TimelineAnimator
import com.alightweb.player.model.AudioLayer
import com.alightweb.player.model.Project
import java.io.File

class AudioEngine(private val context: Context) {

    private val activePlayers = mutableMapOf<String, MediaPlayer>()
    private var isPlaying = false

    fun prepareProject(project: Project) {
        release()
        for (layer in project.layers) {
            if (layer is AudioLayer && layer.src.isNotBlank()) {
                try {
                    val player = MediaPlayer()
                    if (layer.src.startsWith("http://") || layer.src.startsWith("https://")) {
                        player.setDataSource(layer.src)
                    } else if (File(layer.src).exists()) {
                        player.setDataSource(layer.src)
                    } else {
                        // Check local assets
                        val afd = context.assets.openFd("preset/Beraksi.mp3")
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                    player.prepare()
                    activePlayers[layer.id] = player
                } catch (e: Exception) {
                    // Ignore audio loading errors gracefully
                }
            }
        }
    }

    fun sync(currentTimeMs: Long, project: Project) {
        for (layer in project.layers) {
            if (layer is AudioLayer) {
                val player = activePlayers[layer.id] ?: continue
                if (currentTimeMs in layer.startTime..layer.endTime) {
                    val layerTime = currentTimeMs - layer.startTime
                    val targetAudioMs = layer.inTime + layerTime
                    val gain = TimelineAnimator.evaluateFloat(layer.gainTimeline, layerTime, layer.endTime - layer.startTime)
                    val vol = gain.coerceIn(0f, 1f)
                    player.setVolume(vol, vol)

                    if (isPlaying && !player.isPlaying) {
                        player.seekTo(targetAudioMs.toInt())
                        player.start()
                    } else if (!isPlaying && player.isPlaying) {
                        player.pause()
                    }
                } else {
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            }
        }
    }

    fun play() {
        isPlaying = true
    }

    fun pause() {
        isPlaying = false
        activePlayers.values.forEach {
            if (it.isPlaying) it.pause()
        }
    }

    fun seek(timeMs: Long, project: Project) {
        for (layer in project.layers) {
            if (layer is AudioLayer) {
                val player = activePlayers[layer.id] ?: continue
                val layerTime = timeMs - layer.startTime
                val targetAudioMs = (layer.inTime + layerTime).coerceAtLeast(0)
                player.seekTo(targetAudioMs.toInt())
            }
        }
    }

    fun release() {
        activePlayers.values.forEach {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {}
        }
        activePlayers.clear()
    }
}
