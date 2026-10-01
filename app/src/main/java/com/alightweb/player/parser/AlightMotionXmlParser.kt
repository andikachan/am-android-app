package com.alightweb.player.parser

import android.graphics.Color
import android.util.Xml
import com.alightweb.player.model.*
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class AlightMotionXmlParser {

    fun parse(xmlString: String): Project {
        val cleanXml = cleanXmlString(xmlString)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(cleanXml))

        var project = Project()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName?.lowercase()) {
                        "scene", "project", "alightmotion" -> {
                            project = parseScene(parser)
                        }
                        "shape" -> {
                            val shape = parseShape(parser, project.duration)
                            if (shape != null) project.layers.add(shape)
                        }
                        "text" -> {
                            val text = parseText(parser, project.duration)
                            if (text != null) project.layers.add(text)
                        }
                        "media", "image", "video" -> {
                            val media = parseMedia(parser, project.duration)
                            if (media != null) project.layers.add(media)
                        }
                        "audio" -> {
                            val audio = parseAudio(parser, project.duration)
                            if (audio != null) project.layers.add(audio)
                        }
                        "bookmark" -> {
                            val t = parser.getAttributeValue(null, "t")?.toLongOrNull()
                            if (t != null) project.bookmarks.add(t)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return project
    }

    private fun cleanXmlString(raw: String): String {
        var str = raw.trim().removePrefix("\uFEFF").removePrefix("\uFFFE")
        val firstLt = str.indexOf('<')
        val lastGt = str.lastIndexOf('>')
        if (firstLt != -1 && lastGt != -1 && lastGt > firstLt) {
            str = str.substring(firstLt, lastGt + 1)
        }
        return str
    }

    private fun parseScene(parser: XmlPullParser): Project {
        val title = parser.getAttributeValue(null, "title") ?: "Alight Project"
        val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 1080
        val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 1920
        val fps = parser.getAttributeValue(null, "fps")?.toIntOrNull() ?: 30
        val totalTime = parser.getAttributeValue(null, "totalTime")?.toLongOrNull()
            ?: parser.getAttributeValue(null, "duration")?.toLongOrNull() ?: 5000L
        val bgStr = parser.getAttributeValue(null, "bgcolor") ?: "#090a0e"
        val bgColor = parseColor(bgStr, 0xFF090A0E.toInt())

        return Project(
            title = title,
            width = width,
            height = height,
            fps = fps,
            duration = totalTime,
            backgroundColor = bgColor
        )
    }

    private fun parseShape(parser: XmlPullParser, sceneDur: Long): ShapeLayer? {
        val id = parser.getAttributeValue(null, "id") ?: "shape_${System.currentTimeMillis()}"
        val name = parser.getAttributeValue(null, "name") ?: "Shape $id"
        val startTime = parser.getAttributeValue(null, "startTime")?.toLongOrNull() ?: 0L
        val endTime = parser.getAttributeValue(null, "endTime")?.toLongOrNull() ?: sceneDur
        val colorStr = parser.getAttributeValue(null, "fillColor") ?: parser.getAttributeValue(null, "color")
        val color = parseColor(colorStr, 0xFF00F0FF.toInt())
        val shapeType = parser.getAttributeValue(null, "type") ?: parser.getAttributeValue(null, "s") ?: "rect"
        val mediaSrc = parser.getAttributeValue(null, "src") ?: parser.getAttributeValue(null, "fillImage")

        val shape = ShapeLayer(
            id = id,
            name = name,
            startTime = startTime,
            endTime = endTime,
            shapeType = shapeType.removePrefix("."),
            fillColor = color,
            mediaFillSrc = mediaSrc
        )

        parseLayerChildren(parser, shape)
        return shape
    }

    private fun parseText(parser: XmlPullParser, sceneDur: Long): TextLayer? {
        val id = parser.getAttributeValue(null, "id") ?: "text_${System.currentTimeMillis()}"
        val name = parser.getAttributeValue(null, "name") ?: "Text"
        val startTime = parser.getAttributeValue(null, "startTime")?.toLongOrNull() ?: 0L
        val endTime = parser.getAttributeValue(null, "endTime")?.toLongOrNull() ?: sceneDur
        val textVal = parser.getAttributeValue(null, "text") ?: "ALIGHT MOTION"
        val fontSize = parser.getAttributeValue(null, "size")?.toFloatOrNull()
            ?: parser.getAttributeValue(null, "fontSize")?.toFloatOrNull() ?: 48f
        val colorStr = parser.getAttributeValue(null, "color")
        val color = parseColor(colorStr, 0xFFFFFFFF.toInt())

        val textLayer = TextLayer(
            id = id,
            name = name,
            startTime = startTime,
            endTime = endTime,
            text = textVal,
            fontSize = fontSize,
            textColor = color
        )

        parseLayerChildren(parser, textLayer)
        return textLayer
    }

    private fun parseMedia(parser: XmlPullParser, sceneDur: Long): MediaLayer? {
        val id = parser.getAttributeValue(null, "id") ?: "media_${System.currentTimeMillis()}"
        val name = parser.getAttributeValue(null, "name") ?: "Media"
        val startTime = parser.getAttributeValue(null, "startTime")?.toLongOrNull() ?: 0L
        val endTime = parser.getAttributeValue(null, "endTime")?.toLongOrNull() ?: sceneDur
        val src = parser.getAttributeValue(null, "src") ?: parser.getAttributeValue(null, "uri") ?: ""
        val type = parser.getAttributeValue(null, "type") ?: ""
        val isVideo = type.contains("video") || src.endsWith(".mp4")

        val mediaLayer = MediaLayer(
            id = id,
            name = name,
            startTime = startTime,
            endTime = endTime,
            src = src,
            isVideo = isVideo
        )

        parseLayerChildren(parser, mediaLayer)
        return mediaLayer
    }

    private fun parseAudio(parser: XmlPullParser, sceneDur: Long): AudioLayer? {
        val id = parser.getAttributeValue(null, "id") ?: "audio_${System.currentTimeMillis()}"
        val name = parser.getAttributeValue(null, "name") ?: "Audio"
        val startTime = parser.getAttributeValue(null, "startTime")?.toLongOrNull() ?: 0L
        val endTime = parser.getAttributeValue(null, "endTime")?.toLongOrNull() ?: sceneDur
        val src = parser.getAttributeValue(null, "src") ?: parser.getAttributeValue(null, "uri") ?: ""
        val inTime = parser.getAttributeValue(null, "inTime")?.toLongOrNull() ?: 0L
        val outTime = parser.getAttributeValue(null, "outTime")?.toLongOrNull() ?: sceneDur

        val audioLayer = AudioLayer(
            id = id,
            name = name,
            startTime = startTime,
            endTime = endTime,
            src = src,
            inTime = inTime,
            outTime = outTime
        )

        parseLayerChildren(parser, audioLayer)
        return audioLayer
    }

    private fun parseLayerChildren(parser: XmlPullParser, layer: Layer) {
        val initialDepth = parser.depth
        var eventType = parser.next()

        while (eventType != XmlPullParser.END_DOCUMENT && (parser.depth > initialDepth || eventType != XmlPullParser.END_TAG)) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name?.lowercase()
                when (name) {
                    "transform" -> parseTransformTag(parser, layer.transform)
                    "location" -> parseLocationKeyframes(parser, layer.transform.location, layer.endTime - layer.startTime)
                    "scale" -> parseScaleKeyframes(parser, layer.transform.scale, layer.endTime - layer.startTime)
                    "rotation" -> parseFloatKeyframes(parser, layer.transform.rotation, layer.endTime - layer.startTime)
                    "opacity" -> parseFloatKeyframes(parser, layer.transform.opacity, layer.endTime - layer.startTime)
                    "gain" -> if (layer is AudioLayer) parseFloatKeyframes(parser, layer.gainTimeline, layer.endTime - layer.startTime)
                    "effect" -> parseEffectTag(parser, layer.effects)
                    "property" -> parsePropertyTag(parser, layer)
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseTransformTag(parser: XmlPullParser, transform: LayerTransform) {
        val x = parser.getAttributeValue(null, "x")?.toFloatOrNull()
        val y = parser.getAttributeValue(null, "y")?.toFloatOrNull()
        if (x != null && y != null) {
            transform.location.defaultValue = Vec3(x, y, 0f)
        }
        val sx = parser.getAttributeValue(null, "scaleX")?.toFloatOrNull() ?: parser.getAttributeValue(null, "scale")?.toFloatOrNull()
        val sy = parser.getAttributeValue(null, "scaleY")?.toFloatOrNull() ?: parser.getAttributeValue(null, "scale")?.toFloatOrNull()
        if (sx != null) {
            transform.scale.defaultValue = Vec2(sx, sy ?: sx)
        }
        val rot = parser.getAttributeValue(null, "rotation")?.toFloatOrNull()
        if (rot != null) {
            transform.rotation.defaultValue = rot
        }
    }

    private fun parseLocationKeyframes(parser: XmlPullParser, timeline: PropertyTimeline<Vec3>, layerDur: Long) {
        val directVal = parser.getAttributeValue(null, "value")
        if (directVal != null) {
            val parts = directVal.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size >= 2) {
                timeline.defaultValue = Vec3(parts[0], parts[1], if (parts.size > 2) parts[2] else 0f)
            }
        }
        parseKfNodes(parser, layerDur) { tMs, tNorm, valStr, curve ->
            val parts = valStr.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size >= 2) {
                timeline.keyframes.add(Keyframe(tMs, tNorm, Vec3(parts[0], parts[1], if (parts.size > 2) parts[2] else 0f), curve))
            }
        }
    }

    private fun parseScaleKeyframes(parser: XmlPullParser, timeline: PropertyTimeline<Vec2>, layerDur: Long) {
        val directVal = parser.getAttributeValue(null, "value")
        if (directVal != null) {
            val parts = directVal.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.isNotEmpty()) {
                timeline.defaultValue = Vec2(parts[0], if (parts.size > 1) parts[1] else parts[0])
            }
        }
        parseKfNodes(parser, layerDur) { tMs, tNorm, valStr, curve ->
            val parts = valStr.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.isNotEmpty()) {
                timeline.keyframes.add(Keyframe(tMs, tNorm, Vec2(parts[0], if (parts.size > 1) parts[1] else parts[0]), curve))
            }
        }
    }

    private fun parseFloatKeyframes(parser: XmlPullParser, timeline: PropertyTimeline<Float>, layerDur: Long) {
        val directVal = parser.getAttributeValue(null, "value")?.toFloatOrNull()
        if (directVal != null) timeline.defaultValue = directVal

        parseKfNodes(parser, layerDur) { tMs, tNorm, valStr, curve ->
            val v = valStr.toFloatOrNull()
            if (v != null) {
                timeline.keyframes.add(Keyframe(tMs, tNorm, v, curve))
            }
        }
    }

    private inline fun parseKfNodes(
        parser: XmlPullParser,
        layerDur: Long,
        onKf: (timeMs: Long, normT: Float, valStr: String, curve: String) -> Unit
    ) {
        val initialDepth = parser.depth
        var eventType = parser.next()

        while (eventType != XmlPullParser.END_DOCUMENT && (parser.depth > initialDepth || eventType != XmlPullParser.END_TAG)) {
            if (eventType == XmlPullParser.START_TAG && (parser.name == "kf" || parser.name == "keyframe")) {
                val tVal = parser.getAttributeValue(null, "time")?.toLongOrNull() ?: 0L
                val tNorm = parser.getAttributeValue(null, "t")?.toFloatOrNull() ?: 0f
                val vStr = parser.getAttributeValue(null, "value") ?: parser.getAttributeValue(null, "v") ?: "0"
                val curve = parser.getAttributeValue(null, "curve") ?: parser.getAttributeValue(null, "e") ?: "linear"
                onKf(tVal, tNorm, vStr, curve)
            }
            eventType = parser.next()
        }
    }

    private fun parseEffectTag(parser: XmlPullParser, effectsList: MutableList<LayerEffect>) {
        val fxId = parser.getAttributeValue(null, "id") ?: "glow"
        val enabled = parser.getAttributeValue(null, "enabled") != "false"
        val fx = LayerEffect(id = fxId, enabled = enabled)
        effectsList.add(fx)
    }

    private fun parsePropertyTag(parser: XmlPullParser, layer: Layer) {
        val name = parser.getAttributeValue(null, "name")?.lowercase()
        val type = parser.getAttributeValue(null, "type")
        val value = parser.getAttributeValue(null, "value")
        if (name == "size" && value != null && layer is ShapeLayer) {
            val parts = value.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size >= 2) layer.size = Vec2(parts[0], parts[1])
        }
    }

    private fun parseColor(colorStr: String?, defaultColor: Int): Int {
        if (colorStr.isNullOrBlank()) return defaultColor
        return try {
            if (colorStr.startsWith("#")) {
                Color.parseColor(colorStr)
            } else {
                defaultColor
            }
        } catch (e: Exception) {
            defaultColor
        }
    }
}
