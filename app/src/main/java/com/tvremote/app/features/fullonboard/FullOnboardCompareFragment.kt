package com.tvremote.app.features.fullonboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.tvremote.app.databinding.FragmentFullOnboardCompareBinding

class FullOnboardCompareFragment : Fragment() {

    private var _binding: FragmentFullOnboardCompareBinding? = null
    private val binding get() = _binding!!

    private val pageIndex: Int by lazy {
        requireArguments().getInt(ARG_PAGE_INDEX)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFullOnboardCompareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val page = FullOnboardContent.pages.getOrNull(pageIndex) as? FullOnboardPage.Compare ?: return
        binding.compareView.setImages(page.afterImageRes, page.beforeImageRes)
        binding.compareView.onCompareTouchChanged = { isTouching ->
            (activity as? FullOnboardHost)?.onCompareTouchChanged(isTouching)
        }
    }

    override fun onDestroyView() {
        binding.compareView.stopHintAnimation()
        binding.compareView.onCompareTouchChanged = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_PAGE_INDEX = "page_index"

        fun newInstance(pageIndex: Int): FullOnboardCompareFragment {
            return FullOnboardCompareFragment().apply {
                arguments = bundleOf(ARG_PAGE_INDEX to pageIndex)
            }
        }
    }
}
