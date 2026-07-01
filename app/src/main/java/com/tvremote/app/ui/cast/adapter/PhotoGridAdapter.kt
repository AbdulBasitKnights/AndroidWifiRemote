package com.tvremote.app.ui.cast.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tvremote.app.databinding.ItemCastPhotoBinding

class PhotoGridAdapter(
    private val onPhotoClick: (Uri) -> Unit,
) : RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder>() {
    private val items = mutableListOf<Uri>()

    fun submit(photos: List<Uri>) {
        items.clear()
        items.addAll(photos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemCastPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PhotoViewHolder(
        private val binding: ItemCastPhotoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uri: Uri) {
            Glide.with(binding.photoImage).load(uri).centerCrop().into(binding.photoImage)
            binding.root.setOnClickListener { onPhotoClick(uri) }
        }
    }
}
