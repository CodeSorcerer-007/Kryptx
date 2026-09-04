package com.kryptx.app.core.designsystem.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Kryptx Sovereign Motion Physics & Animation System.
 * Ultra-fluid, tactile spring curves and navigation choreography.
 */
object KryptxMotion {
    // ── Spring Tokens ──────────────────────────────────────────────────────────
    
    // Snappy tactile feedback for buttons and interactive controls
    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Bouncy playful spring for toggles, favorites, badges, unlock bursts
    val ExpressiveBouncy = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMediumLow
    )

    // Smooth weighted inertia for bottom sheets, dialogs, expanding cards
    val SpatialSmooth = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    // Subtle breathing glow / ambient pulse
    val AmbientPulse = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow
    )

    // ── Easings ───────────────────────────────────────────────────────────────
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val StandardEasing = FastOutSlowInEasing

    // ── Navigation Transitions ────────────────────────────────────────────────
    
    /**
     * Cinematic forward slide-and-fade for drill-downs (e.g. Dashboard -> Detail).
     */
    fun forwardTransition(): ContentTransform {
        return (slideInHorizontally(
            initialOffsetX = { (it * 0.15f).toInt() },
            animationSpec = tween(340, easing = EmphasizedDecelerate)
        ) + fadeIn(
            animationSpec = tween(280, easing = LinearOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(340, easing = EmphasizedDecelerate)
        )) togetherWith (slideOutHorizontally(
            targetOffsetX = { -(it * 0.12f).toInt() },
            animationSpec = tween(280, easing = EmphasizedAccelerate)
        ) + fadeOut(
            animationSpec = tween(220)
        ) + scaleOut(
            targetScale = 0.98f,
            animationSpec = tween(280)
        ))
    }

    /**
     * Reverse slide-and-fade for back navigation.
     */
    fun backwardTransition(): ContentTransform {
        return (slideInHorizontally(
            initialOffsetX = { -(it * 0.12f).toInt() },
            animationSpec = tween(320, easing = EmphasizedDecelerate)
        ) + fadeIn(
            animationSpec = tween(260, easing = LinearOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(320, easing = EmphasizedDecelerate)
        )) togetherWith (slideOutHorizontally(
            targetOffsetX = { (it * 0.15f).toInt() },
            animationSpec = tween(280, easing = EmphasizedAccelerate)
        ) + fadeOut(
            animationSpec = tween(220)
        ) + scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(280)
        ))
    }

    /**
     * Cross-fade with subtle scale for bottom tab switching.
     */
    fun tabCrossfade(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(220, easing = LinearOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.985f,
            animationSpec = tween(240, easing = EmphasizedDecelerate)
        )) togetherWith (fadeOut(
            animationSpec = tween(160)
        ))
    }

    /**
     * Vault Unlock explosive entrance transition: scale up from vault center.
     */
    fun vaultUnlockEntrance(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(380, easing = LinearOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMediumLow
            )
        )) togetherWith (fadeOut(
            animationSpec = tween(220)
        ) + scaleOut(
            targetScale = 1.06f,
            animationSpec = tween(260, easing = EmphasizedAccelerate)
        ))
    }
}
