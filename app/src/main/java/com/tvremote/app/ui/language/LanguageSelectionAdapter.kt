package com.tvremote.app.ui.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.tvremote.app.R

class LanguageSelectionAdapter(
    private val list: List<LanguageModel>,
    private val listener: LanguageSelectionClickListener,
) : RecyclerView.Adapter<LanguageSelectionAdapter.LanguageSelectionViewHolder>() {

    interface LanguageSelectionClickListener {
        fun onLanguageClick(language: LanguageModel?)
    }

    inner class LanguageSelectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rootLayout: ConstraintLayout = itemView.findViewById(R.id.parent)
        val name: TextView = itemView.findViewById(R.id.tv_language)
        val flag: ImageView = itemView.findViewById(R.id.flag)
        val selector: ImageView = itemView.findViewById(R.id.selector)
        val anim: LottieAnimationView = itemView.findViewById(R.id.animationTap)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageSelectionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
        return LanguageSelectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: LanguageSelectionViewHolder, position: Int) {
        val item = list[position]
        holder.name.text = item.name
        holder.flag.setImageDrawable(item.icon)

        if (item.isSelected) {
            Glide.with(holder.selector.context).load(R.drawable.ic_radio_selected).into(holder.selector)
            holder.rootLayout.setBackgroundResource(R.drawable.language_bg_selected)
        } else {
            Glide.with(holder.selector.context).load(R.drawable.ic_radio_unselected).into(holder.selector)
            holder.rootLayout.setBackgroundResource(R.drawable.language_bg_stroke)
        }

        val noItemSelected = list.none { it.isSelected } && item.lang == "en"
        holder.anim.visibility = if (noItemSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            list.forEach { it.isSelected = false }
            item.isSelected = true
            listener.onLanguageClick(item)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = list.size
}
