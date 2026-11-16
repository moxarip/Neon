package com.example.game.engine

import android.opengl.GLES30

class ShaderProgram {
    var programId: Int = 0
        private set

    var uMvpLoc: Int = -1
    var uModelLoc: Int = -1
    var uColorLoc: Int = -1
    var uEmissiveLoc: Int = -1
    var uAlphaLoc: Int = -1
    var uLightDirLoc: Int = -1
    var uCamPosLoc: Int = -1
    var uSpecularLoc: Int = -1

    fun compile() {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vs)
        GLES30.glAttachShader(programId, fs)
        GLES30.glLinkProgram(programId)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        uMvpLoc = GLES30.glGetUniformLocation(programId, "uMvp")
        uModelLoc = GLES30.glGetUniformLocation(programId, "uModel")
        uColorLoc = GLES30.glGetUniformLocation(programId, "uColor")
        uEmissiveLoc = GLES30.glGetUniformLocation(programId, "uEmissive")
        uAlphaLoc = GLES30.glGetUniformLocation(programId, "uAlpha")
        uLightDirLoc = GLES30.glGetUniformLocation(programId, "uLightDir")
        uCamPosLoc = GLES30.glGetUniformLocation(programId, "uCamPos")
        uSpecularLoc = GLES30.glGetUniformLocation(programId, "uSpecular")
    }

    fun use() {
        GLES30.glUseProgram(programId)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            out vec3 vWorldPos;
            out vec3 vNormal;
            void main() {
                vec4 wp = uModel * vec4(aPos, 1.0);
                vWorldPos = wp.xyz;
                vNormal = mat3(uModel) * aNormal;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec3 vWorldPos;
            in vec3 vNormal;
            uniform vec3 uColor;
            uniform float uEmissive;
            uniform float uAlpha;
            uniform vec3 uLightDir;
            uniform vec3 uCamPos;
            uniform float uSpecular;
            out vec4 fragColor;
            void main() {
                vec3 N = normalize(vNormal);
                vec3 L = normalize(uLightDir);
                vec3 V = normalize(uCamPos - vWorldPos);
                float diff = max(dot(N, L), 0.0);
                vec3 H = normalize(L + V);
                float spec = pow(max(dot(N, H), 0.0), 28.0) * uSpecular;
                float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.28;
                vec3 lit = uColor * (0.28 + 0.72 * diff) + vec3(spec) + uColor * (uEmissive + rim);
                float dist = length(uCamPos - vWorldPos);
                float fog = clamp((dist - 36.0) / 55.0, 0.0, 0.72);
                vec3 finalCol = mix(lit, vec3(0.025, 0.032, 0.08), fog);
                fragColor = vec4(finalCol, uAlpha);
            }
        """
    }
}
