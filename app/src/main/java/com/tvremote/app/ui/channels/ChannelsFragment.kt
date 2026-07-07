package com.tvremote.app.ui.channels

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.tvremote.app.R
import com.tvremote.app.data.repository.TvRemoteRepository
import com.tvremote.app.databinding.FragmentChannelsBinding
import com.tvremote.app.databinding.ItemChannelShortcutBinding
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.ui.remote.RemoteViewModel
import kotlinx.coroutines.launch

class ChannelsFragment : Fragment(R.layout.fragment_channels) {
    private var _binding: FragmentChannelsBinding? = null
    private var adapter: ChannelShortcutAdapter? = null

    private val viewModel: RemoteViewModel by activityViewModels {
        (requireActivity() as MainActivity).viewModelFactory()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChannelsBinding.bind(view)
        val binding = _binding ?: return

        val shortcuts = buildShortcuts()
        adapter = ChannelShortcutAdapter(shortcuts) { shortcut ->
            viewModel.refreshConnectionState()
            shortcut.action()
        }
        binding.channelsGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.channelsGrid.adapter = adapter

        binding.searchInput.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty().trim()
            adapter?.filter(query)
        }

        observeEvents()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshConnectionState()
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is TvRemoteRepository.RepositoryEvent.ChannelLaunched ->
                            Toast.makeText(requireContext(), R.string.channel_launched, Toast.LENGTH_SHORT).show()
                        is TvRemoteRepository.RepositoryEvent.ChannelLaunching ->
                            Toast.makeText(requireContext(), R.string.channel_launching, Toast.LENGTH_SHORT).show()
                        is TvRemoteRepository.RepositoryEvent.ChannelNotConnected ->
                            Toast.makeText(requireContext(), R.string.connect_tv_first_channel, Toast.LENGTH_SHORT).show()
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun buildShortcuts(): List<ChannelShortcut> = listOf(
        ChannelShortcut(R.string.shortcut_youtube, R.color.channel_youtube) { viewModel.runYouTube() },
        ChannelShortcut(R.string.shortcut_netflix, R.color.channel_netflix) { viewModel.runNetflix() },
        ChannelShortcut(R.string.shortcut_prime, R.color.channel_prime) { viewModel.runPrime() },
        ChannelShortcut(R.string.shortcut_apple_tv, R.color.channel_apple) { viewModel.runAppleTv() },
        ChannelShortcut(R.string.shortcut_disney, R.color.channel_disney) { viewModel.runDisney() },
        ChannelShortcut(R.string.shortcut_hotstar, R.color.channel_hotstar) { viewModel.runHotstar() },
    )

    override fun onDestroyView() {
        adapter = null
        _binding = null
        super.onDestroyView()
    }

    data class ChannelShortcut(
        val labelRes: Int,
        val colorRes: Int,
        val action: () -> Unit,
    ) {
        fun label(context: android.content.Context): String = context.getString(labelRes)
    }

    private class ChannelShortcutAdapter(
        private val allItems: List<ChannelShortcut>,
        private val onClick: (ChannelShortcut) -> Unit,
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ChannelShortcutAdapter.VH>() {

        private var visible = allItems

        fun filter(query: String) {
            visible = if (query.isBlank()) {
                allItems
            } else {
                val q = query.lowercase()
                allItems.filter { it.label(bindingContext).lowercase().contains(q) }
            }
            notifyDataSetChanged()
        }

        private lateinit var bindingContext: android.content.Context

        override fun onAttachedToRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            bindingContext = recyclerView.context
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemChannelShortcutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(visible[position], onClick)
        }

        override fun getItemCount(): Int = visible.size

        class VH(private val binding: ItemChannelShortcutBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

            fun bind(item: ChannelShortcut, onClick: (ChannelShortcut) -> Unit) {
                val ctx = binding.root.context
                binding.channelLabel.text = ctx.getString(item.labelRes)
                binding.channelLabel.setTextColor(
                    ContextCompat.getColor(ctx, R.color.text_on_accent),
                )
                binding.root.setCardBackgroundColor(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, item.colorRes)),
                )
                binding.root.setOnClickListener { onClick(item) }
            }
        }
    }
}
