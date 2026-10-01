package com.alightweb.player.model

data class Vec2(var x: Float = 0f, var y: Float = 0f)
data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f)

data class Keyframe<T>(
    val time: Long, // in milliseconds
    val normalizedTime: Float = 0f, // 0.0 to 1.0 (if defined in 't' attribute)
    val value: T,
    val curve: String = "linear" // e.g. "cubicBezier 0.42 0.0 0.58 1.0" or "0.25,0.1,0.25,1"
)

data class PropertyTimeline<T>(
    var defaultValue: T,
    val keyframes: MutableList<Keyframe<T>> = mutableListOf()
) {
    fun isAnimated(): Boolean = keyframes.isNotEmpty()
}

data class LayerTransform(
    val location: PropertyTimeline<Vec3> = PropertyTimeline(Vec3(540f, 960f, 0f)),
    val scale: PropertyTimeline<Vec2> = PropertyTimeline(Vec2(1f, 1f)),
    val rotation: PropertyTimeline<Float> = PropertyTimeline(0f),
    val opacity: PropertyTimeline<Float> = PropertyTimeline(1f)
)

data class LayerEffect(
    val id: String,
    val name: String = "",
    var enabled: Boolean = true,
    val properties: MutableMap<String, Any> = mutableMapOf()
)

sealed class Layer(
    open val id: String,
    open var name: String,
    open var startTime: Long, // ms
    open var endTime: Long,   // ms
    open var visible: Boolean = true,
    open val transform: LayerTransform = LayerTransform(),
    open val effects: MutableList<LayerEffect> = mutableListOf()
)

data class ShapeLayer(
    override val id: String,
    override var name: String = "Shape",
    override var startTime: Long = 0L,
    override var endTime: Long = 5000L,
    override var visible: Boolean = true,
    override val transform: LayerTransform = LayerTransform(),
    override val effects: MutableList<LayerEffect> = mutableListOf(),
    var shapeType: String = "rect", // rect, circle, star, triangle
    var size: Vec2 = Vec2(400f, 400f),
    var fillColor: Int = 0xFF00F0FF.toInt(),
    var strokeColor: Int? = null,
    var strokeWidth: Float = 0f,
    var cornerRadius: Float = 0f,
    var mediaFillSrc: String? = null
) : Layer(id, name, startTime, endTime, visible, transform, effects)

data class TextLayer(
    override val id: String,
    override var name: String = "Text",
    override var startTime: Long = 0L,
    override var endTime: Long = 5000L,
    override var visible: Boolean = true,
    override val transform: LayerTransform = LayerTransform(),
    override val effects: MutableList<LayerEffect> = mutableListOf(),
    var text: String = "ALIGHT MOTION",
    var fontSize: Float = 48f,
    var fontName: String = "sans-serif",
    var textColor: Int = 0xFFFFFFFF.toInt(),
    var textAlign: String = "center"
) : Layer(id, name, startTime, endTime, visible, transform, effects)

data class MediaLayer(
    override val id: String,
    override var name: String = "Media",
    override var startTime: Long = 0L,
    override var endTime: Long = 5000L,
    override var visible: Boolean = true,
    override val transform: LayerTransform = LayerTransform(),
    override val effects: MutableList<LayerEffect> = mutableListOf(),
    var src: String = "",
    var isVideo: Boolean = false,
    var size: Vec2 = Vec2(1080f, 1920f)
) : Layer(id, name, startTime, endTime, visible, transform, effects)

data class AudioLayer(
    override val id: String,
    override var name: String = "Audio",
    override var startTime: Long = 0L,
    override var endTime: Long = 5000L,
    override var visible: Boolean = true,
    override val transform: LayerTransform = LayerTransform(),
    override val effects: MutableList<LayerEffect> = mutableListOf(),
    var src: String = "",
    var inTime: Long = 0L,
    var outTime: Long = 5000L,
    val gainTimeline: PropertyTimeline<Float> = PropertyTimeline(1.0f)
) : Layer(id, name, startTime, endTime, visible, transform, effects)

data class Project(
    var title: String = "Alight Project",
    var width: Int = 1080,
    var height: Int = 1920,
    var fps: Int = 30,
    var duration: Long = 5000L, // in milliseconds
    var backgroundColor: Int = 0xFF090A0E.toInt(),
    val layers: MutableList<Layer> = mutableListOf(),
    val bookmarks: MutableList<Long> = mutableListOf(),
    val mediaFiles: MutableMap<String, String> = mutableMapOf() // uri -> localPath/url
)
