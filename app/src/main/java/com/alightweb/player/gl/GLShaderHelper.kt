package com.alightweb.player.gl

import android.opengl.GLES20
import android.util.Log

object GLShaderHelper {

    private const val TAG = "GLShaderHelper"

    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Error compiling shader ($type): $error")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) return 0

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Error linking program: $error")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }
}

object Shaders {

    const val COLOR_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
        }
    """

    const val COLOR_FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec4 uColor;
        uniform float uAlpha;
        void main() {
            gl_FragColor = vec4(uColor.rgb, uColor.a * uAlpha);
        }
    """

    const val TEXTURE_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
        }
    """

    const val TEXTURE_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uAlpha;
        void main() {
            vec4 col = texture2D(uTexture, vTexCoord);
            gl_FragColor = vec4(col.rgb, col.a * uAlpha);
        }
    """
}
