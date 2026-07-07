package com.tvremote.app.features.fullonboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tvremote.app.databinding.FragmentFullOnboardHairBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FullOnboardHairFragment : Fragment() {

    private var _binding: FragmentFullOnboardHairBinding? = null
    private val binding get() = _binding!!

    private var slideJob: Job? = null
    private var currentIndex = 0

    private val pageIndex: Int by lazy {
        requireArguments().getInt(ARG_PAGE_INDEX)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFullOnboardHairBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val page = FullOnboardContent.pages.getOrNull(pageIndex) as? FullOnboardPage.HairSlideshow ?: return
        val images = page.imageResIds
        if (images.isEmpty()) return

        currentIndex = 0
        binding.ivHairSlide.setImageResource(images[currentIndex])
        startAutoSlide(images)
    }

    private fun startAutoSlide(images: List<Int>) {
        slideJob?.cancel()
        slideJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(FullOnboardContent.HAIR_SLIDE_INTERVAL_MS)
                currentIndex = (currentIndex + 1) % images.size
                binding.ivHairSlide.setImageResource(images[currentIndex])
            }
        }
    }

    override fun onDestroyView() {
        slideJob?.cancel()
        slideJob = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_PAGE_INDEX = "page_index"

        fun newInstance(pageIndex: Int): FullOnboardHairFragment {
            return FullOnboardHairFragment().apply {
                arguments = bundleOf(ARG_PAGE_INDEX to pageIndex)
            }
        }
    }
}
