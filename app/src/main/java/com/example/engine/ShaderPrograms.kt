package com.example.engine

import android.opengl.GLES30
import android.util.Log

class LitShaderProgram {
    val programId: Int

    val uMvpMatrix: Int
    val uModelMatrix: Int
    val uCameraPos: Int
    val uTintColor: Int
    val uUseTexture: Int
    val uTexture: Int
    val uMetallic: Int
    val uRoughness: Int
    val uEmissive: Int
    val uHeadlightPos: Int
    val uHeadlightDir: Int
    val uHeadlightPower: Int
    val uFogColor: Int
    val uFogDensity: Int
    val uWetRoadSpec: Int
    val uSunDir: Int
    val uSunColor: Int
    val uSunIntensity: Int
    val uSkyAmb: Int
    val uGroundAmb: Int
    val uNightFactor: Int
    val uReflectionStrength: Int
    val uDamageFactor: Int

    init {
        programId = compileProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvpMatrix = GLES30.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrix = GLES30.glGetUniformLocation(programId, "uModelMatrix")
        uCameraPos = GLES30.glGetUniformLocation(programId, "uCameraPos")
        uTintColor = GLES30.glGetUniformLocation(programId, "uTintColor")
        uUseTexture = GLES30.glGetUniformLocation(programId, "uUseTexture")
        uTexture = GLES30.glGetUniformLocation(programId, "uTexture")
        uMetallic = GLES30.glGetUniformLocation(programId, "uMetallic")
        uRoughness = GLES30.glGetUniformLocation(programId, "uRoughness")
        uEmissive = GLES30.glGetUniformLocation(programId, "uEmissive")
        uHeadlightPos = GLES30.glGetUniformLocation(programId, "uHeadlightPos")
        uHeadlightDir = GLES30.glGetUniformLocation(programId, "uHeadlightDir")
        uHeadlightPower = GLES30.glGetUniformLocation(programId, "uHeadlightPower")
        uFogColor = GLES30.glGetUniformLocation(programId, "uFogColor")
        uFogDensity = GLES30.glGetUniformLocation(programId, "uFogDensity")
        uWetRoadSpec = GLES30.glGetUniformLocation(programId, "uWetRoadSpec")
        uSunDir = GLES30.glGetUniformLocation(programId, "uSunDir")
        uSunColor = GLES30.glGetUniformLocation(programId, "uSunColor")
        uSunIntensity = GLES30.glGetUniformLocation(programId, "uSunIntensity")
        uSkyAmb = GLES30.glGetUniformLocation(programId, "uSkyAmb")
        uGroundAmb = GLES30.glGetUniformLocation(programId, "uGroundAmb")
        uNightFactor = GLES30.glGetUniformLocation(programId, "uNightFactor")
        uReflectionStrength = GLES30.glGetUniformLocation(programId, "uReflectionStrength")
        uDamageFactor = GLES30.glGetUniformLocation(programId, "uDamageFactor")
    }

    fun use() {
        GLES30.glUseProgram(programId)
    }

    fun release() {
        GLES30.glDeleteProgram(programId)
    }

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aTexCoord;
            layout(location = 3) in vec3 aColor;

            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;

            out vec3 vWorldPos;
            out vec3 vWorldNormal;
            out vec2 vTexCoord;
            out vec3 vColor;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vWorldNormal = normalize(mat3(uModelMatrix) * aNormal);
                vTexCoord = aTexCoord;
                vColor = aColor;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec3 vWorldPos;
            in vec3 vWorldNormal;
            in vec2 vTexCoord;
            in vec3 vColor;

            uniform vec3 uCameraPos;
            uniform vec3 uTintColor;
            uniform int uUseTexture;
            uniform sampler2D uTexture;
            uniform float uMetallic;
            uniform float uRoughness;
            uniform float uEmissive;
            uniform vec3 uHeadlightPos;
            uniform vec3 uHeadlightDir;
            uniform float uHeadlightPower;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform float uWetRoadSpec;
            uniform vec3 uSunDir;
            uniform vec3 uSunColor;
            uniform float uSunIntensity;
            uniform vec3 uSkyAmb;
            uniform vec3 uGroundAmb;
            uniform float uNightFactor;
            uniform float uReflectionStrength;
            uniform float uDamageFactor;

            out vec4 fragColor;

            void main() {
                vec3 N = normalize(vWorldNormal);
                vec3 V = normalize(uCameraPos - vWorldPos);
                vec3 R = reflect(-V, N);

                vec3 albedo = vColor * uTintColor;
                float texLum = 0.0;
                if (uUseTexture == 1) {
                    vec4 texel = texture(uTexture, vTexCoord);
                    albedo *= texel.rgb;
                    texLum = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
                }

                // Procedural crash wear: metallic scrapes, soot char & clearcoat dulling
                float dmg = clamp(uDamageFactor, 0.0, 1.0);
                float effRoughness = clamp(uRoughness + dmg * 0.48, 0.05, 0.96);
                float effMetallic = clamp(uMetallic * (1.0 - dmg * 0.42), 0.05, 1.0);
                if (dmg > 0.02) {
                    float scratchWave = sin(vWorldPos.x * 23.0 + vWorldPos.z * 17.0) * cos(vWorldPos.y * 31.0 - vWorldPos.x * 11.0);
                    float scuffMask = smoothstep(0.55 - dmg * 0.45, 0.92, abs(scratchWave));
                    float charBand = smoothstep(0.20, 0.85, dmg) * (0.45 + 0.55 * sin(vWorldPos.x * 9.0 + vWorldPos.z * 7.0));
                    vec3 bareMetal = vec3(0.46, 0.49, 0.54);
                    vec3 sootChar = vec3(0.08, 0.07, 0.07);
                    albedo = mix(albedo, bareMetal, scuffMask * dmg * 0.58);
                    albedo = mix(albedo, sootChar, clamp(charBand * 0.42, 0.0, 0.52));
                }

                // Dynamic day/night hemisphere ambient
                float hemi = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
                vec3 ambient = albedo * mix(uGroundAmb, uSkyAmb, hemi) * 1.25;

                // Dynamic directional Sun / Moon lighting + dual-lobe metallic clearcoat specular
                vec3 L = normalize(uSunDir);
                float NdotL = max(dot(N, L), 0.0);
                vec3 H = normalize(L + V);
                float specExp = mix(140.0, 14.0, clamp(effRoughness, 0.05, 1.0));
                float spec = pow(max(dot(N, H), 0.0), specExp);
                float clearcoatSpec = pow(max(dot(N, H), 0.0), 180.0) * smoothstep(0.35, 0.95, effMetallic) * (1.0 - dmg * 0.7);
                vec3 diffuse = albedo * uSunColor * NdotL * uSunIntensity;

                // Secondary warm-red city neon bounce (stronger at dusk/night)
                vec3 L2 = normalize(vec3(0.65, 0.35, -0.55));
                float NdotL2 = max(dot(N, L2), 0.0);
                vec3 cityBounce = albedo * vec3(0.46, 0.14, 0.24) * NdotL2 * (0.16 + 0.42 * uNightFactor);

                // Procedural sky + horizon neon skyline reflection dome for metallic car paint & glass
                float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0);
                float horizonBand = exp(-abs(R.y) * 5.2);
                float skyMask = smoothstep(-0.08, 0.45, R.y);
                float neonStripe = 0.5 + 0.5 * sin(R.x * 7.0 + R.z * 5.0 + vWorldPos.x * 0.03);
                vec3 skylineGlow = mix(vec3(0.08, 0.85, 1.0), vec3(0.96, 0.14, 0.26), neonStripe) * (0.35 + 0.65 * uNightFactor);
                vec3 envDome = mix(uGroundAmb * 0.8, uSkyAmb * 2.1, skyMask) + skylineGlow * horizonBand * 0.72;
                float glossFactor = (1.0 - clamp(effRoughness, 0.05, 0.95)) * clamp(uReflectionStrength, 0.2, 1.2);
                vec3 specular = (mix(vec3(0.38), albedo * 0.82 + vec3(0.35), effMetallic) * spec * (0.68 + effMetallic * 1.15) + vec3(clearcoatSpec * 0.85)) * uSunColor;
                vec3 clearcoat = envDome * (0.12 + fresnel * 0.88) * (0.14 + effMetallic * 0.78) * glossFactor;

                // Dynamic car headlight cone on road and nearby objects
                vec3 headlightContrib = vec3(0.0);
                if (uHeadlightPower > 0.01) {
                    vec3 toFrag = vWorldPos - uHeadlightPos;
                    float distH = length(toFrag);
                    if (distH < 78.0 && distH > 0.5) {
                        vec3 fragDir = toFrag / distH;
                        float cone = dot(fragDir, normalize(uHeadlightDir));
                        float spot = smoothstep(0.75, 0.96, cone);
                        float atten = 1.0 / (1.0 + 0.0020 * distH * distH);
                        float facing = max(dot(N, -fragDir), 0.28);
                        headlightContrib = albedo * vec3(1.25, 1.32, 1.48) * spot * atten * facing * uHeadlightPower * 1.95;
                    }
                }

                // Wet asphalt specular reflections & periodic night streetlamp pools along avenues
                vec3 wetNeon = vec3(0.0);
                if (N.y > 0.55 && (uWetRoadSpec > 0.08 || uNightFactor > 0.25)) {
                    float lampWaveX = pow(max(0.0, cos(vWorldPos.x * 0.13)), 8.0);
                    float lampWaveZ = pow(max(0.0, cos(vWorldPos.z * 0.13)), 8.0);
                    float pool = max(lampWaveX, lampWaveZ) * smoothstep(0.06, 0.92, fresnel + 0.36);
                    vec3 nightNeon = mix(vec3(0.08, 0.76, 0.98), vec3(0.96, 0.16, 0.28), 0.5 + 0.5 * sin(vWorldPos.x * 0.02 + vWorldPos.z * 0.02));
                    vec3 rainSheen = mix(uSkyAmb * 1.7, nightNeon, clamp(uNightFactor + 0.28, 0.0, 1.0));
                    float reflScale = clamp(uReflectionStrength, 0.25, 1.2);
                    wetNeon = rainSheen * pool * (0.28 * uWetRoadSpec + 0.12 * uNightFactor) * reflScale;
                }

                // Self-illumination for neon strips, traffic lights, car lights & night building windows
                vec3 emissiveColor = albedo * uEmissive;
                if (uUseTexture == 1 && uEmissive < 0.01 && N.y < 0.35 && texLum > 0.42 && uNightFactor > 0.15) {
                    emissiveColor += albedo * (texLum - 0.35) * 1.55 * uNightFactor;
                }

                vec3 lit = ambient + diffuse + cityBounce + specular + clearcoat + headlightContrib + wetNeon + emissiveColor;

                // Dynamic atmospheric fog (clear / dense fog / rain haze)
                float dist = length(uCameraPos - vWorldPos);
                float fogFactor = clamp(exp(-pow(dist * uFogDensity, 1.75)), 0.0, 1.0);
                vec3 finalRgb = mix(uFogColor, lit, fogFactor);

                fragColor = vec4(finalRgb, 1.0);
            }
        """
    }
}

class UnlitOverlayShader {
    val programId: Int
    val uMvpMatrix: Int
    val uColor: Int

    init {
        programId = compileProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvpMatrix = GLES30.glGetUniformLocation(programId, "uMvpMatrix")
        uColor = GLES30.glGetUniformLocation(programId, "uColor")
    }

    fun use() {
        GLES30.glUseProgram(programId)
    }

    fun release() {
        GLES30.glDeleteProgram(programId)
    }

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aPosition;
            layout(location = 3) in vec3 aColor;

            uniform mat4 uMvpMatrix;
            out vec3 vColor;

            void main() {
                vColor = aColor;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec3 vColor;
            uniform vec4 uColor;
            out vec4 fragColor;

            void main() {
                fragColor = vec4(vColor * uColor.rgb, uColor.a);
            }
        """
    }
}

internal fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
    val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
    val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
    val program = GLES30.glCreateProgram()
    GLES30.glAttachShader(program, vs)
    GLES30.glAttachShader(program, fs)
    GLES30.glLinkProgram(program)
    val linkStatus = IntArray(1)
    GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
        val log = GLES30.glGetProgramInfoLog(program)
        GLES30.glDeleteProgram(program)
        Log.e("GtaRaceXShader", "Program link failed: $log")
        throw RuntimeException("Shader link failed: $log")
    }
    GLES30.glDeleteShader(vs)
    GLES30.glDeleteShader(fs)
    return program
}

private fun compileShader(type: Int, src: String): Int {
    val shader = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shader, src)
    GLES30.glCompileShader(shader)
    val compiled = IntArray(1)
    GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
    if (compiled[0] == 0) {
        val log = GLES30.glGetShaderInfoLog(shader)
        GLES30.glDeleteShader(shader)
        Log.e("GtaRaceXShader", "Shader compile failed: $log")
        throw RuntimeException("Shader compile failed: $log")
    }
    return shader
}
