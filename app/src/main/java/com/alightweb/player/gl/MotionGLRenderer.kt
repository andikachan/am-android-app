package com.alightweb.player.gl

import android.content.Context
import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.alightweb.player.animation.TimelineAnimator
import com.alightweb.player.model.*
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class MotionGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    var project: Project = Project()
        set(value) {
            field = value
            textureCache.clear()
            textTextureCache.clear()
        }

    var currentTimeMs: Long = 0L
    var qualityScale: Float = 0.5f // 0.35f to 1.0f

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var colorProgram = 0
    private var textureProgram = 0

    private lateinit var quadVertexBuffer: FloatBuffer
    private lateinit var quadTexCoordBuffer: FloatBuffer

    private val textureCache = mutableMapOf<String, Int>()
    private val textTextureCache = mutableMapOf<String, Int>()

    private val quadVertices = floatArrayOf(
        -0.5f,  0.5f, 0.0f,
        -0.5f, -0.5f, 0.0f,
         0.5f,  0.5f, 0.0f,
         0.5f, -0.5f, 0.0f
    )

    private val quadTexCoords = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        colorProgram = GLShaderHelper.createProgram(Shaders.COLOR_VERTEX_SHADER, Shaders.COLOR_FRAGMENT_SHADER)
        textureProgram = GLShaderHelper.createProgram(Shaders.TEXTURE_VERTEX_SHADER, Shaders.TEXTURE_FRAGMENT_SHADER)

        quadVertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVertices)
                position(0)
            }

        quadTexCoordBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadTexCoords)
                position(0)
            }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val projW = project.width.toFloat()
        val projH = project.height.toFloat()

        // Orthographic 2D projection: top-left (0,0) to bottom-right (projW, projH)
        Matrix.orthoM(projectionMatrix, 0, 0f, projW, projH, 0f, -1000f, 1000f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val bg = project.backgroundColor
        val r = Color.red(bg) / 255.0f
        val g = Color.green(bg) / 255.0f
        val b = Color.blue(bg) / 255.0f
        val a = Color.alpha(bg) / 255.0f

        GLES20.glClearColor(r, g, b, a)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val layers = project.layers
        for (i in layers.indices) {
            val layer = layers[i]
            if (!layer.visible) continue
            if (currentTimeMs < layer.startTime || currentTimeMs > layer.endTime) continue

            val layerTime = currentTimeMs - layer.startTime
            val layerDuration = (layer.endTime - layer.startTime).coerceAtLeast(1L)

            renderLayer(layer, layerTime, layerDuration)
        }
    }

    private fun renderLayer(layer: Layer, layerTime: Long, layerDuration: Long) {
        val loc = TimelineAnimator.evaluateVec3(layer.transform.location, layerTime, layerDuration)
        val scl = TimelineAnimator.evaluateVec2(layer.transform.scale, layerTime, layerDuration)
        val rot = TimelineAnimator.evaluateFloat(layer.transform.rotation, layerTime, layerDuration)
        val alpha = TimelineAnimator.evaluateFloat(layer.transform.opacity, layerTime, layerDuration)

        if (alpha <= 0.001f) return

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, loc.x, loc.y, loc.z)
        Matrix.rotateM(modelMatrix, 0, rot, 0f, 0f, 1f)

        when (layer) {
            is ShapeLayer -> {
                val w = layer.size.x * scl.x
                val h = layer.size.y * scl.y
                Matrix.scaleM(modelMatrix, 0, w, h, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

                if (layer.mediaFillSrc != null) {
                    val texId = getOrLoadTexture(layer.mediaFillSrc!!)
                    if (texId != 0) {
                        drawTextureQuad(texId, alpha)
                        return
                    }
                }
                drawColorQuad(layer.fillColor, alpha)
            }
            is TextLayer -> {
                val texId = getOrCreateTextTexture(layer)
                val w = 512f * scl.x
                val h = 128f * scl.y
                Matrix.scaleM(modelMatrix, 0, w, h, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

                if (texId != 0) {
                    drawTextureQuad(texId, alpha)
                } else {
                    drawColorQuad(layer.textColor, alpha)
                }
            }
            is MediaLayer -> {
                val w = layer.size.x * scl.x
                val h = layer.size.y * scl.y
                Matrix.scaleM(modelMatrix, 0, w, h, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

                val texId = getOrLoadTexture(layer.src)
                if (texId != 0) {
                    drawTextureQuad(texId, alpha)
                } else {
                    drawColorQuad(0xFF336699.toInt(), alpha)
                }
            }
            is AudioLayer -> {
                // Audio is handled by AudioEngine
            }
        }
    }

    private fun drawColorQuad(colorInt: Int, alpha: Float) {
        GLES20.glUseProgram(colorProgram)

        val uMVPMatrixHandle = GLES20.glGetUniformLocation(colorProgram, "uMVPMatrix")
        val uColorHandle = GLES20.glGetUniformLocation(colorProgram, "uColor")
        val uAlphaHandle = GLES20.glGetUniformLocation(colorProgram, "uAlpha")
        val aPositionHandle = GLES20.glGetAttribLocation(colorProgram, "aPosition")

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)

        val cr = Color.red(colorInt) / 255f
        val cg = Color.green(colorInt) / 255f
        val cb = Color.blue(colorInt) / 255f
        val ca = Color.alpha(colorInt) / 255f
        GLES20.glUniform4f(uColorHandle, cr, cg, cb, ca)
        GLES20.glUniform1f(uAlphaHandle, alpha)

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
    }

    private fun drawTextureQuad(textureId: Int, alpha: Float) {
        GLES20.glUseProgram(textureProgram)

        val uMVPMatrixHandle = GLES20.glGetUniformLocation(textureProgram, "uMVPMatrix")
        val uTextureHandle = GLES20.glGetUniformLocation(textureProgram, "uTexture")
        val uAlphaHandle = GLES20.glGetUniformLocation(textureProgram, "uAlpha")
        val aPositionHandle = GLES20.glGetAttribLocation(textureProgram, "aPosition")
        val aTexCoordHandle = GLES20.glGetAttribLocation(textureProgram, "aTexCoord")

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(uAlphaHandle, alpha)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureHandle, 0)

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordHandle)
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, quadTexCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTexCoordHandle)
    }

    private fun getOrLoadTexture(src: String): Int {
        if (textureCache.containsKey(src)) {
            return textureCache[src] ?: 0
        }

        val bitmap = loadBitmapFromSrc(src) ?: return 0
        val textureId = uploadBitmapToGL(bitmap)
        bitmap.recycle()
        textureCache[src] = textureId
        return textureId
    }

    private fun loadBitmapFromSrc(src: String): Bitmap? {
        return try {
            val file = File(src)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                // Try from project mapped mediaFiles
                val mapped = project.mediaFiles[src]
                if (mapped != null && File(mapped).exists()) {
                    return BitmapFactory.decodeFile(mapped)
                }

                // Try from local assets
                val cleanSrc = src.substringAfterLast('/')
                val isStream: InputStream = try {
                    context.assets.open("preset/$cleanSrc")
                } catch (e: Exception) {
                    try {
                        context.assets.open("runtime/preset/$cleanSrc")
                    } catch (e2: Exception) {
                        context.assets.open("preset/12w4v1.jpg")
                    }
                }
                BitmapFactory.decodeStream(isStream)
            }
        } catch (e: Exception) {
            Log.e("GLRenderer", "Failed loading bitmap for $src: ${e.message}")
            null
        }
    }

    private fun getOrCreateTextTexture(layer: TextLayer): Int {
        val key = "${layer.text}_${layer.fontSize}_${layer.textColor}"
        if (textTextureCache.containsKey(key)) {
            return textTextureCache[key] ?: 0
        }

        val width = 512
        val height = 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.textColor
            textSize = layer.fontSize * 1.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(layer.fontName, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val yPos = (height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(layer.text, width / 2f, yPos, paint)

        val texId = uploadBitmapToGL(bitmap)
        bitmap.recycle()
        textTextureCache[key] = texId
        return texId
    }

    private fun uploadBitmapToGL(bitmap: Bitmap): Int {
        val textureHandles = IntArray(1)
        GLES20.glGenTextures(1, textureHandles, 0)
        val texId = textureHandles[0]
        if (texId == 0) return 0

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return texId
    }
}
