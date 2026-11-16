package com.example.game.engine

import android.opengl.Matrix
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun set(nx: Float, ny: Float, nz: Float) {
        x = nx; y = ny; z = nz
    }

    fun set(other: Vec3) {
        x = other.x; y = other.y; z = other.z
    }

    fun lengthXZ(): Float = sqrt(x * x + z * z)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun distanceXZ(other: Vec3): Float {
        val dx = x - other.x
        val dz = z - other.z
        return sqrt(dx * dx + dz * dz)
    }

    fun angleXZ(): Float = atan2(z, x)

    companion object {
        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        fun lerpAngle(a: Float, b: Float, t: Float): Float {
            var diff = (b - a) % (2f * Math.PI.toFloat())
            if (diff > Math.PI.toFloat()) diff -= 2f * Math.PI.toFloat()
            if (diff < -Math.PI.toFloat()) diff += 2f * Math.PI.toFloat()
            return a + diff * t
        }
    }
}

class Mat4 {
    val data = FloatArray(16)

    fun setIdentity(): Mat4 {
        Matrix.setIdentityM(data, 0)
        return this
    }

    fun setPerspective(fovDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
        Matrix.perspectiveM(data, 0, fovDeg, aspect, near, far)
        return this
    }

    fun setLookAt(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float
    ): Mat4 {
        Matrix.setLookAtM(data, 0, eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ)
        return this
    }

    fun translate(x: Float, y: Float, z: Float): Mat4 {
        Matrix.translateM(data, 0, x, y, z)
        return this
    }

    fun rotateY(deg: Float): Mat4 {
        Matrix.rotateM(data, 0, deg, 0f, 1f, 0f)
        return this
    }

    fun rotateX(deg: Float): Mat4 {
        Matrix.rotateM(data, 0, deg, 1f, 0f, 0f)
        return this
    }

    fun rotateZ(deg: Float): Mat4 {
        Matrix.rotateM(data, 0, deg, 0f, 0f, 1f)
        return this
    }

    fun scale(sx: Float, sy: Float, sz: Float): Mat4 {
        Matrix.scaleM(data, 0, sx, sy, sz)
        return this
    }

    fun multiply(lhs: Mat4, rhs: Mat4): Mat4 {
        Matrix.multiplyMM(data, 0, lhs.data, 0, rhs.data, 0)
        return this
    }

    fun copyFrom(other: Mat4): Mat4 {
        System.arraycopy(other.data, 0, data, 0, 16)
        return this
    }
}
