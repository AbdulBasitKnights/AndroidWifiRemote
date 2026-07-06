package com.tvremote.app.ui.common

import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.view.isVisible
import com.tvremote.app.R
import com.tvremote.app.databinding.IncludeConnectionLoaderBinding

object ConnectionLoader {

    enum class Mode(
        val titleRes: Int,
        val subtitleRes: Int,
        val iconRes: Int,
    ) {
        REMOTE(
            titleRes = R.string.loader_remote_title,
            subtitleRes = R.string.loader_remote_subtitle,
            iconRes = R.drawable.ic_remote,
        ),
        CAST(
            titleRes = R.string.loader_cast_title,
            subtitleRes = R.string.loader_cast_subtitle,
            iconRes = R.drawable.ic_cast,
        ),
        MIRROR(
            titleRes = R.string.loader_mirror_title,
            subtitleRes = R.string.loader_mirror_subtitle,
            iconRes = R.drawable.ic_screen_mirror,
        ),
    }

    private val activeAnimations = mutableMapOf<View, List<Animation>>()

    fun show(root: View, mode: Mode, subtitle: String? = null) {
        val binding = IncludeConnectionLoaderBinding.bind(root)
        binding.loaderTitle.setText(mode.titleRes)
        binding.loaderSubtitle.text = subtitle?.takeIf { it.isNotBlank() }
            ?: root.context.getString(mode.subtitleRes)
        binding.loaderIcon.setImageResource(mode.iconRes)
        root.isVisible = true
        startDotAnimations(binding)
    }

    fun hide(root: View) {
        stopDotAnimations(root)
        root.isVisible = false
    }

    private fun startDotAnimations(binding: IncludeConnectionLoaderBinding) {
        stopDotAnimations(binding.root)
        val dots = listOf(binding.loaderDot1, binding.loaderDot2, binding.loaderDot3)
        val animations = dots.mapIndexed { index, dot ->
            AnimationUtils.loadAnimation(dot.context, R.anim.anim_loader_dot_pulse).apply {
                startOffset = index * 180L
                dot.startAnimation(this)
            }
        }
        activeAnimations[binding.root] = animations
    }

    private fun stopDotAnimations(root: View) {
        activeAnimations.remove(root)?.forEach { it.cancel() }
        IncludeConnectionLoaderBinding.bind(root).apply {
            loaderDot1.clearAnimation()
            loaderDot2.clearAnimation()
            loaderDot3.clearAnimation()
        }
    }
}
