package com.example.game.engine

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Mesh(private val vboId: Int, private val vertexCount: Int) {
    fun draw(mode: Int = GLES30.GL_TRIANGLES) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glDrawArrays(mode, 0, vertexCount)
    }
}

object MeshBuilder {

    fun createBox(): Mesh {
        // 36 vertices (pos xyz + normal xyz)
        val data = floatArrayOf(
            // Front (+Z)
            -0.5f,-0.5f, 0.5f, 0f,0f,1f,  0.5f,-0.5f, 0.5f, 0f,0f,1f,  0.5f, 0.5f, 0.5f, 0f,0f,1f,
            -0.5f,-0.5f, 0.5f, 0f,0f,1f,  0.5f, 0.5f, 0.5f, 0f,0f,1f, -0.5f, 0.5f, 0.5f, 0f,0f,1f,
            // Back (-Z)
             0.5f,-0.5f,-0.5f, 0f,0f,-1f,-0.5f,-0.5f,-0.5f, 0f,0f,-1f,-0.5f, 0.5f,-0.5f, 0f,0f,-1f,
             0.5f,-0.5f,-0.5f, 0f,0f,-1f,-0.5f, 0.5f,-0.5f, 0f,0f,-1f, 0.5f, 0.5f,-0.5f, 0f,0f,-1f,
            // Top (+Y)
            -0.5f, 0.5f, 0.5f, 0f,1f,0f,  0.5f, 0.5f, 0.5f, 0f,1f,0f,  0.5f, 0.5f,-0.5f, 0f,1f,0f,
            -0.5f, 0.5f, 0.5f, 0f,1f,0f,  0.5f, 0.5f,-0.5f, 0f,1f,0f, -0.5f, 0.5f,-0.5f, 0f,1f,0f,
            // Bottom (-Y)
            -0.5f,-0.5f,-0.5f, 0f,-1f,0f, 0.5f,-0.5f,-0.5f, 0f,-1f,0f, 0.5f,-0.5f, 0.5f, 0f,-1f,0f,
            -0.5f,-0.5f,-0.5f, 0f,-1f,0f, 0.5f,-0.5f, 0.5f, 0f,-1f,0f,-0.5f,-0.5f, 0.5f, 0f,-1f,0f,
            // Right (+X)
             0.5f,-0.5f, 0.5f, 1f,0f,0f,  0.5f,-0.5f,-0.5f, 1f,0f,0f,  0.5f, 0.5f,-0.5f, 1f,0f,0f,
             0.5f,-0.5f, 0.5f, 1f,0f,0f,  0.5f, 0.5f,-0.5f, 1f,0f,0f,  0.5f, 0.5f, 0.5f, 1f,0f,0f,
            // Left (-X)
            -0.5f,-0.5f,-0.5f,-1f,0f,0f, -0.5f,-0.5f, 0.5f,-1f,0f,0f, -0.5f, 0.5f, 0.5f,-1f,0f,0f,
            -0.5f,-0.5f,-0.5f,-1f,0f,0f, -0.5f, 0.5f, 0.5f,-1f,0f,0f, -0.5f, 0.5f,-0.5f,-1f,0f,0f
        )
        return upload(data)
    }

    fun createSphere(stacks: Int = 12, slices: Int = 16): Mesh {
        val verts = ArrayList<Float>(stacks * slices * 36)
        for (i in 0 until stacks) {
            val phi0 = PI * (-0.5 + i.toDouble() / stacks)
            val phi1 = PI * (-0.5 + (i + 1).toDouble() / stacks)
            for (j in 0 until slices) {
                val theta0 = 2.0 * PI * j.toDouble() / slices
                val theta1 = 2.0 * PI * (j + 1).toDouble() / slices

                val p00 = spherePoint(phi0, theta0)
                val p10 = spherePoint(phi1, theta0)
                val p11 = spherePoint(phi1, theta1)
                val p01 = spherePoint(phi0, theta1)

                addVert(verts, p00); addVert(verts, p10); addVert(verts, p11)
                addVert(verts, p00); addVert(verts, p11); addVert(verts, p01)
            }
        }
        return upload(verts.toFloatArray())
    }

    fun createCylinder(segments: Int = 14): Mesh {
        val verts = ArrayList<Float>(segments * 72)
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val x0 = cos(a0) * 0.5f; val z0 = sin(a0) * 0.5f
            val x1 = cos(a1) * 0.5f; val z1 = sin(a1) * 0.5f
            val nx0 = cos(a0); val nz0 = sin(a0)
            val nx1 = cos(a1); val nz1 = sin(a1)

            // Side quad
            addPn(verts, x0, -0.5f, z0, nx0, 0f, nz0)
            addPn(verts, x1, -0.5f, z1, nx1, 0f, nz1)
            addPn(verts, x1,  0.5f, z1, nx1, 0f, nz1)

            addPn(verts, x0, -0.5f, z0, nx0, 0f, nz0)
            addPn(verts, x1,  0.5f, z1, nx1, 0f, nz1)
            addPn(verts, x0,  0.5f, z0, nx0, 0f, nz0)

            // Top cap
            addPn(verts, 0f, 0.5f, 0f, 0f, 1f, 0f)
            addPn(verts, x0, 0.5f, z0, 0f, 1f, 0f)
            addPn(verts, x1, 0.5f, z1, 0f, 1f, 0f)
        }
        return upload(verts.toFloatArray())
    }

    fun createRing(segments: Int = 28, innerRatio: Float = 0.80f): Mesh {
        val verts = ArrayList<Float>(segments * 36)
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)

            addPn(verts, c0 * innerRatio, 0f, s0 * innerRatio, 0f, 1f, 0f)
            addPn(verts, c0, 0f, s0, 0f, 1f, 0f)
            addPn(verts, c1, 0f, s1, 0f, 1f, 0f)

            addPn(verts, c0 * innerRatio, 0f, s0 * innerRatio, 0f, 1f, 0f)
            addPn(verts, c1, 0f, s1, 0f, 1f, 0f)
            addPn(verts, c1 * innerRatio, 0f, s1 * innerRatio, 0f, 1f, 0f)
        }
        return upload(verts.toFloatArray())
    }

    private fun spherePoint(phi: Double, theta: Double): FloatArray {
        val x = (cos(phi) * cos(theta)).toFloat()
        val y = sin(phi).toFloat()
        val z = (cos(phi) * sin(theta)).toFloat()
        return floatArrayOf(x * 0.5f, y * 0.5f, z * 0.5f, x, y, z)
    }

    private fun addVert(list: MutableList<Float>, p: FloatArray) {
        for (f in p) list.add(f)
    }

    private fun addPn(list: MutableList<Float>, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
        list.add(x); list.add(y); list.add(z)
        list.add(nx); list.add(ny); list.add(nz)
    }

    private fun upload(data: FloatArray): Mesh {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        val buf = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
        buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf, GLES30.GL_STATIC_DRAW)
        return Mesh(ids[0], data.size / 6)
    }
}
