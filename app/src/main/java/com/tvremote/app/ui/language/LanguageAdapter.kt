package com.tvremote.app.ui.language

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tvremote.app.R
import com.tvremote.app.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val onSelected: (AppLanguage) -> Unit,
) : ListAdapter<AppLanguage, LanguageAdapter.Holder>(Diff) {

    private var selectedCode: String = "en"

    fun setSelected(code: String) {
        selectedCode = code
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), selectedCode == getItem(position).code)
    }

    inner class Holder(
        private val binding: ItemLanguageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(language: AppLanguage, selected: Boolean) {
            val ctx = binding.root.context
            binding.flagLabel.text = language.flagEmoji
            binding.languageName.text = ctx.getString(language.nameRes)
            binding.languageNative.text = ctx.getString(language.nativeNameRes)
            binding.root.strokeWidth = if (selected) 2 else 0
            binding.root.strokeColor = ContextCompat.getColor(ctx, R.color.accent_purple)
            binding.root.setOnClickListener {
                selectedCode = language.code
                notifyDataSetChanged()
                onSelected(language)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AppLanguage>() {
        override fun areItemsTheSame(old: AppLanguage, new: AppLanguage) = old.code == new.code
        override fun areContentsTheSame(old: AppLanguage, new: AppLanguage) = old == new
    }
}
