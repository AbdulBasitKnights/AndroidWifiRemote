package com.tvremote.app.features.fullonboard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed class FullOnboardPage {
    abstract val titleRes: Int
    abstract val descriptionRes: Int

    data class Compare(
        @StringRes override val titleRes: Int,
        @StringRes override val descriptionRes: Int,
        @DrawableRes val beforeImageRes: Int,
        @DrawableRes val afterImageRes: Int,
    ) : FullOnboardPage()

    data class HairSlideshow(
        @StringRes override val titleRes: Int,
        @StringRes override val descriptionRes: Int,
        @DrawableRes val imageResIds: List<Int>,
    ) : FullOnboardPage()
}
