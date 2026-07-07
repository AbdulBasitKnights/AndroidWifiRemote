package com.tvremote.app.ui.cast

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tvremote.app.R
import com.tvremote.app.databinding.BottomSheetCastPickerBinding
import com.tvremote.app.ui.cast.adapter.PhotoGridAdapter
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.util.OperationResult
import com.tvremote.app.util.SafeRun

class CastMediaPickerSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCastPickerBinding? = null
    private val mediaUris = mutableListOf<Uri>()
    private lateinit var mediaType: MediaType

    private val viewModel: CastViewModel by activityViewModels {
        (requireActivity() as MainActivity).viewModelFactory()
    }

    private val adapter = PhotoGridAdapter { uri -> castMedia(uri) }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTO_PICK),
    ) { uris ->
        if (uris.isNotEmpty()) {
            addMedia(uris)
        }
    }

    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { addMedia(listOf(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaType = MediaType.valueOf(
            requireArguments().getString(ARG_MEDIA_TYPE, MediaType.PHOTO.name),
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetCastPickerBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = _binding ?: return

        when (mediaType) {
            MediaType.PHOTO -> {
                binding.pickerTitle.setText(R.string.cast_photos_title)
                binding.addMediaButton.setText(R.string.cast_add_photos)
            }
            MediaType.VIDEO -> {
                binding.pickerTitle.setText(R.string.cast_videos_title)
                binding.addMediaButton.setText(R.string.cast_add_videos)
            }
        }

        binding.mediaGrid.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.mediaGrid.adapter = adapter

        binding.addMediaButton.setOnClickListener {
            SafeRun.run(TAG) {
                when (mediaType) {
                    MediaType.PHOTO -> photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    MediaType.VIDEO -> videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                }
            }
        }

        openSystemPicker()
    }

    private fun openSystemPicker() {
        when (mediaType) {
            MediaType.PHOTO -> photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            MediaType.VIDEO -> videoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        }
    }

    private fun addMedia(uris: List<Uri>) {
        uris.forEach { uri ->
            if (mediaUris.none { it == uri }) {
                mediaUris.add(uri)
            }
        }
        adapter.submit(mediaUris.toList())
    }

    private fun castMedia(uri: Uri) {
        SafeRun.run(TAG) {
            if (!viewModel.isConnected()) {
                Toast.makeText(requireContext(), R.string.cast_select_device, Toast.LENGTH_SHORT).show()
                return@run
            }
            val result = when (mediaType) {
                MediaType.PHOTO -> viewModel.castPhoto(uri, getString(R.string.cast_photos_title))
                MediaType.VIDEO -> viewModel.castMedia(uri, isVideo = true)
            }
            when (result) {
                is OperationResult.Success -> {
                    val message = when (mediaType) {
                        MediaType.PHOTO -> R.string.cast_photo_sent
                        MediaType.VIDEO -> R.string.cast_video_sent
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
                is OperationResult.Failure -> Toast.makeText(
                    requireContext(),
                    getString(R.string.cast_failed, result.message),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    enum class MediaType { PHOTO, VIDEO }

    companion object {
        private const val TAG = "CastMediaPickerSheet"
        private const val ARG_MEDIA_TYPE = "media_type"
        private const val MAX_PHOTO_PICK = 20

        fun newInstance(type: MediaType): CastMediaPickerSheet {
            return CastMediaPickerSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEDIA_TYPE, type.name)
                }
            }
        }
    }
}
