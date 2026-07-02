package com.tvremote.app.ui.settings.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tvremote.app.R
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.databinding.ItemDiscoveredTvBinding

class DiscoveredTvAdapter(
    private val onTvSelected: (DiscoveredTv) -> Unit,
) : RecyclerView.Adapter<DiscoveredTvAdapter.TvViewHolder>() {
    private val items = mutableListOf<DiscoveredTv>()
    private var selectedHost: String? = null
    private var pairingHost: String? = null
    private var pairedHost: String? = null

    fun submit(devices: List<DiscoveredTv>) {
        items.clear()
        items.addAll(devices)
        notifyDataSetChanged()
    }

    fun setSelectedHost(host: String?) {
        selectedHost = host
        notifyDataSetChanged()
    }

    fun setPairingHost(host: String?) {
        pairingHost = host
        notifyDataSetChanged()
    }

    fun setPairedHost(host: String?) {
        pairedHost = host
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TvViewHolder {
        val binding = ItemDiscoveredTvBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TvViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TvViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TvViewHolder(
        private val binding: ItemDiscoveredTvBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tv: DiscoveredTv) {
            binding.tvName.text = tv.name.ifBlank { "Android TV" }
            binding.tvHost.text = tv.host
            val isPairing = pairingHost == tv.host
            val isPairedTv = pairedHost == tv.host
            binding.pairActionLabel.text = when {
                isPairing -> binding.root.context.getString(R.string.pairing_in_progress)
                isPairedTv -> binding.root.context.getString(R.string.tap_to_reconnect)
                else -> binding.root.context.getString(R.string.tap_to_pair)
            }
            binding.root.setOnClickListener { onTvSelected(tv) }
            binding.root.alpha = if (isPairing) 0.85f else 1f
        }
    }
}
