package com.alightweb.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alightweb.player.R
import com.alightweb.player.model.PresetItem

class PresetsAdapter(
    private val presets: List<PresetItem>,
    private val onSelect: (PresetItem) -> Unit
) : RecyclerView.Adapter<PresetsAdapter.ViewHolder>() {

    var selectedIndex = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.presetTitle)
        val infoText: TextView = view.findViewById(R.id.presetInfo)
        val indicator: View = view.findViewById(R.id.selectedIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_preset, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = presets[position]
        holder.titleText.text = item.title
        holder.infoText.text = "${item.fileName} • ${item.durationText}"
        holder.indicator.visibility = if (position == selectedIndex) View.VISIBLE else View.INVISIBLE

        holder.itemView.setOnClickListener {
            val prev = selectedIndex
            selectedIndex = holder.bindingAdapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedIndex)
            onSelect(item)
        }
    }

    override fun getItemCount(): Int = presets.size
}
