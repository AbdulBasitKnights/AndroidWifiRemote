package com.tvremote.app.features.fullonboard

import com.tvremote.app.R

object FullOnboardContent {

    const val SEGMENT_DURATION_MS = 2_500L
    const val HAIR_SLIDE_INTERVAL_MS = 1_000L
    const val PAGE_COUNT = 4

    val pages: List<FullOnboardPage> = listOf(
        FullOnboardPage.Compare(
            titleRes = R.string.full_onboard_aging_title,
            descriptionRes = R.string.full_onboard_aging_desc,
            beforeImageRes = R.drawable.young_ob,
            afterImageRes = R.drawable.boy_ob,
        ),
        FullOnboardPage.Compare(
            titleRes = R.string.full_onboard_enhancer_title,
            descriptionRes = R.string.full_onboard_enhancer_desc,
            beforeImageRes = R.drawable.blur_ob,
            afterImageRes = R.drawable.enhance_ob,
        ),
        FullOnboardPage.Compare(
            titleRes = R.string.full_onboard_makeup_title,
            descriptionRes = R.string.full_onboard_makeup_desc,
            beforeImageRes = R.drawable.wo_makeup_ob,
            afterImageRes = R.drawable.makeup_ob,
        ),
        FullOnboardPage.HairSlideshow(
            titleRes = R.string.full_onboard_hair_title,
            descriptionRes = R.string.full_onboard_hair_desc,
            imageResIds = listOf(
                R.drawable.hair_ob1,
                R.drawable.hair_ob2,
                R.drawable.hair_ob3,
                R.drawable.hair_ob4,
            ),
        ),
    )
}
