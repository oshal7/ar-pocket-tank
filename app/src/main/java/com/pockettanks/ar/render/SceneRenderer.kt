package com.pockettanks.ar.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.pockettanks.ar.game.GameState
import com.pockettanks.ar.game.Phase
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Drives ARCore frame updates and renders the camera background plus the
 * destructible neon battlefield. Session lifecycle (resume/pause/close) is
 * owned by the hosting Activity; this class only consumes it.
 */
class SceneRenderer(context: Context, private val gameState: GameState) : GLSurfaceView.Renderer {

    @Volatile var session: Session? = null
    private val displayRotationHelper = DisplayRotationHelper(context)

    private val backgroundRenderer = BackgroundRenderer()
    private val colorProgram = ColorProgram()

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val anchorModelMatrix = FloatArray(16)
    private val tmpModel = FloatArray(16)
    private val tmpMv = FloatArray(16)
    private val tmpMvp = FloatArray(16)

    private val colorCyan = floatArrayOf(0f, 0.95f, 1f, 1f)
    private val colorMagenta = floatArrayOf(1f, 0f, 0.33f, 1f)
    private val colorCharcoal = floatArrayOf(0.10f, 0.10f, 0.14f, 1f)
    private val colorGreen = floatArrayOf(0.22f, 1f, 0.08f, 1f)
    private val colorWhite = floatArrayOf(1f, 1f, 1f, 1f)
    private val colorPlanePoint = floatArrayOf(0f, 0.95f, 1f, 0.6f)

    fun onSurfaceChangedExternal(width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    fun onResume() = displayRotationHelper.onResume()
    fun onPause() = displayRotationHelper.onPause()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.02f, 0.03f, 1f)
        backgroundRenderer.createOnGlThread()
        colorProgram.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val activeSession = session ?: return
        displayRotationHelper.updateSessionIfNeeded(activeSession)

        val frame = try {
            activeSession.update()
        } catch (e: Exception) {
            return
        }
        val camera = frame.camera
        backgroundRenderer.draw(frame)
        if (camera.trackingState != TrackingState.TRACKING) return

        camera.getProjectionMatrix(projMatrix, 0, 0.05f, 20f)
        camera.getViewMatrix(viewMatrix, 0)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        if (gameState.phase == Phase.SCANNING) {
            drawDetectedPlanes(activeSession)
            maybeAutoPlace(activeSession)
            return
        }

        val anchor = gameState.anchor ?: return
        if (anchor.trackingState != TrackingState.TRACKING) return
        anchor.pose.toMatrix(anchorModelMatrix, 0)

        drawTerrain()
        drawTank(gameState.player.x, gameState.player.y, gameState.player.angleDeg, gameState.player.facingRight, colorCyan)
        drawTank(gameState.ai.x, gameState.ai.y, gameState.ai.angleDeg, gameState.ai.facingRight, colorMagenta)

        gameState.shell?.let { shell ->
            drawPoint(shell.x, shell.y, colorWhite, 14f)
            if (shell.trail.size > 1) {
                drawPolyline(shell.trail, colorCyan)
            }
        }
    }

    private fun drawDetectedPlanes(session: Session) {
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            val polygon = plane.polygon
            polygon.rewind()
            val count = polygon.limit() / 2
            if (count == 0) continue
            val verts = FloatArray(count * 3)
            val poseMatrix = FloatArray(16)
            plane.centerPose.toMatrix(poseMatrix, 0)
            for (i in 0 until count) {
                verts[i * 3] = polygon.get(i * 2)
                verts[i * 3 + 1] = 0f
                verts[i * 3 + 2] = polygon.get(i * 2 + 1)
            }
            Matrix.multiplyMM(tmpMv, 0, viewMatrix, 0, poseMatrix, 0)
            Matrix.multiplyMM(tmpMvp, 0, projMatrix, 0, tmpMv, 0)
            colorProgram.draw(Geometry.toBuffer(verts), count, GLES20.GL_LINE_LOOP, tmpMvp, colorPlanePoint, 10f)
            colorProgram.draw(Geometry.toBuffer(verts), count, GLES20.GL_POINTS, tmpMvp, colorPlanePoint, 8f)
        }
    }

    private fun maybeAutoPlace(session: Session) {
        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
            if (plane.subsumedBy != null) continue
            val area = plane.extentX * plane.extentZ
            if (area >= 0.06f) {
                val anchor = plane.createAnchor(plane.centerPose)
                gameState.anchor = anchor
                gameState.onAnchorPlaced()
                return
            }
        }
    }

    private fun drawTerrain() {
        val mesh = Geometry.buildTerrain(gameState.terrain)
        computeMvp(0f, 0f, 0f, 0f)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        colorProgram.draw(Geometry.toBuffer(mesh.wallTriStrip), mesh.wallTriStrip.size / 3, GLES20.GL_TRIANGLE_STRIP, tmpMvp, colorCharcoal)
        colorProgram.draw(Geometry.toBuffer(mesh.ridgeLineStrip), mesh.ridgeLineStrip.size / 3, GLES20.GL_LINE_STRIP, tmpMvp, colorGreen)
        colorProgram.draw(Geometry.toBuffer(mesh.gridLines), mesh.gridLines.size / 3, GLES20.GL_LINES, tmpMvp, colorGreen)
    }

    private fun drawTank(x: Float, y: Float, angleDeg: Float, facingRight: Boolean, color: FloatArray) {
        // Body
        computeMvpScaled(x, y + 0.012f, 0f, 0f, 0.045f, 0.024f, 0.03f)
        colorProgram.draw(Geometry.toBuffer(Geometry.unitCube), Geometry.unitCube.size / 3, GLES20.GL_TRIANGLES, tmpMvp, color)

        // Turret, rotated toward the current aim angle
        val dir = if (facingRight) 1f else -1f
        val turretAngle = if (facingRight) angleDeg else 180f - angleDeg
        val pivotX = x + dir * 0.015f
        val pivotY = y + 0.022f
        computeMvpRotatedScaled(pivotX, pivotY, 0f, turretAngle, 0.03f, 0.01f, 0.01f, offsetLocalX = dir * 0.012f)
        colorProgram.draw(Geometry.toBuffer(Geometry.unitCube), Geometry.unitCube.size / 3, GLES20.GL_TRIANGLES, tmpMvp, color)
    }

    private fun drawPoint(x: Float, y: Float, color: FloatArray, size: Float) {
        computeMvp(x, y, 0f, 0f)
        colorProgram.draw(Geometry.toBuffer(floatArrayOf(0f, 0f, 0f)), 1, GLES20.GL_POINTS, tmpMvp, color, size)
    }

    private fun drawPolyline(trail: List<FloatArray>, color: FloatArray) {
        val verts = Geometry.buildPolyline(trail)
        computeMvp(0f, 0f, 0f, 0f)
        colorProgram.draw(Geometry.toBuffer(verts), verts.size / 3, GLES20.GL_LINE_STRIP, tmpMvp, color)
    }

    private fun computeMvp(x: Float, y: Float, z: Float, rotDeg: Float) {
        Matrix.setIdentityM(tmpModel, 0)
        Matrix.translateM(tmpModel, 0, x, y, z)
        if (rotDeg != 0f) Matrix.rotateM(tmpModel, 0, rotDeg, 0f, 0f, 1f)
        finishMvp()
    }

    private fun computeMvpScaled(x: Float, y: Float, z: Float, rotDeg: Float, sx: Float, sy: Float, sz: Float) {
        Matrix.setIdentityM(tmpModel, 0)
        Matrix.translateM(tmpModel, 0, x, y, z)
        if (rotDeg != 0f) Matrix.rotateM(tmpModel, 0, rotDeg, 0f, 0f, 1f)
        Matrix.scaleM(tmpModel, 0, sx, sy, sz)
        finishMvp()
    }

    private fun computeMvpRotatedScaled(
        x: Float, y: Float, z: Float, rotDeg: Float,
        sx: Float, sy: Float, sz: Float, offsetLocalX: Float = 0f
    ) {
        Matrix.setIdentityM(tmpModel, 0)
        Matrix.translateM(tmpModel, 0, x, y, z)
        Matrix.rotateM(tmpModel, 0, rotDeg, 0f, 0f, 1f)
        Matrix.translateM(tmpModel, 0, offsetLocalX, 0f, 0f)
        Matrix.scaleM(tmpModel, 0, sx, sy, sz)
        finishMvp()
    }

    private fun finishMvp() {
        val world = FloatArray(16)
        Matrix.multiplyMM(world, 0, anchorModelMatrix, 0, tmpModel, 0)
        Matrix.multiplyMM(tmpMv, 0, viewMatrix, 0, world, 0)
        Matrix.multiplyMM(tmpMvp, 0, projMatrix, 0, tmpMv, 0)
    }
}
