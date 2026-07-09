package com.tvremote.app.ui.cast.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tvremote.app.databinding.ItemCastMediaBinding
import com.tvremote.app.ui.cast.CastMediaItem
import java.util.Locale
import java.util.concurrent.TimeUnit

class CastMediaGridAdapter(
    private val onItemClick: (CastMediaItem) -> Unit,
) : ListAdapter<CastMediaItem, CastMediaGridAdapter.MediaViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemCastMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaViewHolder(
        private val binding: ItemCastMediaBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CastMediaItem) {
            Glide.with(binding.mediaThumbnail)
                .load(item.uri)
                .centerCrop()
                .into(binding.mediaThumbnail)

            binding.videoOverlay.isVisible = item.isVideo
            binding.playIcon.isVisible = item.isVideo
            if (item.isVideo && item.durationMs > 0L) {
                binding.durationLabel.isVisible = true
                binding.durationLabel.text = formatDuration(item.durationMs)
            } else {
                binding.durationLabel.isVisible = false
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }

        private fun formatDuration(durationMs: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<CastMediaItem>() {
        override fun areItemsTheSame(oldItem: CastMediaItem, newItem: CastMediaItem): Boolean =
            oldItem.uri == newItem.uri

        override fun areContentsTheSame(oldItem: CastMediaItem, newItem: CastMediaItem): Boolean =
            oldItem == newItem
    }
}
