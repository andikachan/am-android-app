package com.alightweb.player.gl

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.alightweb.player.animation.TimelineAnimator
import com.alightweb.player.model.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MotionGLRenderer : GLSurfaceView.Renderer {

    var project: Project = Project()
    var currentTimeMs: Long = 0L
    var qualityScale: Float = 0.5f // Draft/Potato mode (0.35f to 1.0f)

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var colorProgram = 0
    private var textureProgram = 0

    private lateinit var quadVertexBuffer: FloatBuffer

    private val quadVertices = floatArrayOf(
        -0.5f,  0.5f, 0.0f,
        -0.5f, -0.5f, 0.0f,
         0.5f,  0.5f, 0.0f,
         0.5f, -0.5f, 0.0f
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
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val projW = project.width.toFloat()
        val projH = project.height.toFloat()

        // Orthographic projection: top-left (0,0) to (projW, projH)
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

        val duration = project.duration

        // Render each active layer
        for (layer in project.layers) {
            if (!layer.visible) continue
            if (currentTimeMs < layer.startTime || currentTimeMs > layer.endTime) continue

            val layerTime = currentTimeMs - layer.startTime
            val layerDuration = layer.endTime - layer.startTime

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
                drawColorQuad(layer.fillColor, alpha)
            }
            is TextLayer -> {
                // Approximate Text drawing in GL
                val w = 400f * scl.x
                val h = (layer.fontSize * 1.5f) * scl.y
                Matrix.scaleM(modelMatrix, 0, w, h, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
                drawColorQuad(layer.textColor, alpha)
            }
            is MediaLayer -> {
                val w = layer.size.x * scl.x
                val h = layer.size.y * scl.y
                Matrix.scaleM(modelMatrix, 0, w, h, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
                drawColorQuad(0xFF555555.toInt(), alpha)
            }
            is AudioLayer -> {
                // Audio does not render visually
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
}
