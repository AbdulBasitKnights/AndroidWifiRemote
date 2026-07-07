package com.tvremote.app.features.fullonboard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class FullOnboardPagerAdapter(
    activity: FragmentActivity,
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = FullOnboardContent.PAGE_COUNT

    override fun createFragment(position: Int): Fragment {
        val page = FullOnboardContent.pages[position]
        return when (page) {
            is FullOnboardPage.Compare -> FullOnboardCompareFragment.newInstance(position)
            is FullOnboardPage.HairSlideshow -> FullOnboardHairFragment.newInstance(position)
        }
    }
}
