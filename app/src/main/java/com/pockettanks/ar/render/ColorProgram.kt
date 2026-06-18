package com.pockettanks.ar.render

import android.opengl.GLES20
import java.nio.FloatBuffer

/** Minimal flat-color shader used for every shape in the scene (unlit, emissive look). */
class ColorProgram {
    private var program = 0
    private var positionAttrib = 0
    private var mvpUniform = 0
    private var colorUniform = 0
    private var pointSizeUniform = 0

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            uniform mat4 u_MVP;
            uniform float u_PointSize;
            void main() {
              gl_Position = u_MVP * a_Position;
              gl_PointSize = u_PointSize;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
              gl_FragColor = u_Color;
            }
        """
    }

    fun createOnGlThread() {
        program = ShaderUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        mvpUniform = GLES20.glGetUniformLocation(program, "u_MVP")
        colorUniform = GLES20.glGetUniformLocation(program, "u_Color")
        pointSizeUniform = GLES20.glGetUniformLocation(program, "u_PointSize")
    }

    fun draw(vertices: FloatBuffer, vertexCount: Int, mode: Int, mvp: FloatArray, color: FloatArray, pointSize: Float = 6f) {
        if (vertexCount <= 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvp, 0)
        GLES20.glUniform4fv(colorUniform, 1, color, 0)
        GLES20.glUniform1f(pointSizeUniform, pointSize)

        vertices.position(0)
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glDrawArrays(mode, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(positionAttrib)
    }
}
