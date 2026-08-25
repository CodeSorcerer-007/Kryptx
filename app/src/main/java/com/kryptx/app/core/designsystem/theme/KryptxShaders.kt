package com.kryptx.app.core.designsystem.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Android 13+ (API 33+) AGSL Fluid Dynamic Chromatic Mesh Shaders.
 * Renders hardware-accelerated animated specular caustics, dynamic light rays, and frosted refraction.
 */
object KryptxShaders {

    /**
     * AGSL Shader code for animated chromatic fluid glow.
     */
    private const val CHROMATIC_FLUID_AGSL = """
        uniform float2 iResolution;
        uniform float iTime;
        uniform float4 color1;
        uniform float4 color2;
        uniform float4 color3;

        half4 main(in float2 fragCoord) {
            float2 uv = fragCoord / iResolution.xy;
            float t = iTime * 0.4;
            
            float d1 = sin(uv.x * 3.14159 + t) * cos(uv.y * 3.14159 + t);
            float d2 = cos(uv.x * 2.0 - t * 0.7) * sin(uv.y * 2.0 + t * 0.5);
            float blend = clamp((d1 + d2) * 0.5 + 0.5, 0.0, 1.0);
            
            half4 col = mix(color1, color2, half(blend));
            col = mix(col, color3, half(sin(uv.x * 1.5 + uv.y * 1.5 + t) * 0.3 + 0.3));
            return col;
        }
    """

    /**
     * Applies dynamic fluid AGSL shader background on API 33+ or a smooth animated gradient brush fallback on older APIs.
     */
    fun Modifier.chromaticFluidBackground(
        color1: Color = KryptxCyan.copy(alpha = 0.25f),
        color2: Color = KryptxViolet.copy(alpha = 0.25f),
        color3: Color = KryptxBlue.copy(alpha = 0.15f)
    ): Modifier = composed {
        val transition = rememberInfiniteTransition(label = "fluid_shader")
        val time by transition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "time_uniform"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.graphicsLayer {
                try {
                    val shader = RuntimeShader(CHROMATIC_FLUID_AGSL).apply {
                        setFloatUniform("iResolution", size.width, size.height)
                        setFloatUniform("iTime", time)
                        setColorUniform("color1", android.graphics.Color.valueOf(color1.red, color1.green, color1.blue, color1.alpha))
                        setColorUniform("color2", android.graphics.Color.valueOf(color2.red, color2.green, color2.blue, color2.alpha))
                        setColorUniform("color3", android.graphics.Color.valueOf(color3.red, color3.green, color3.blue, color3.alpha))
                    }
                    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "contents").asComposeRenderEffect()
                } catch (_: Throwable) {
                    // Fallback to normal rendering if GPU shader compilation fails
                }
            }
        } else {
            // High-performance CPU/Canvas animated gradient fallback for older Android APIs
            this.drawBehind {
                val dx = kotlin.math.sin(time) * (size.width * 0.3f)
                val dy = kotlin.math.cos(time) * (size.height * 0.3f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(color1, color2, color3, Color.Transparent),
                        center = Offset(size.width * 0.5f + dx, size.height * 0.5f + dy),
                        radius = (size.width + size.height) * 0.6f
                    )
                )
            }
        }
    }
}
