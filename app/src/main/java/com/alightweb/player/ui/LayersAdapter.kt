package com.alightweb.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alightweb.player.R
import com.alightweb.player.model.*

class LayersAdapter(
    private var layers: List<Layer>,
    private val onVisibilityChanged: (Layer, Boolean) -> Unit
) : RecyclerView.Adapter<LayersAdapter.ViewHolder>() {

    fun updateLayers(newLayers: List<Layer>) {
        layers = newLayers
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeBadge: TextView = view.findViewById(R.id.layerTypeBadge)
        val nameText: TextView = view.findViewById(R.id.layerName)
        val timingText: TextView = view.findViewById(R.id.layerTiming)
        val eyeButton: ImageButton = view.findViewById(R.id.layerVisibilityBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_layer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val layer = layers[position]
        holder.nameText.text = layer.name
        holder.timingText.text = String.format("%.2fs - %.2fs", layer.startTime / 1000f, layer.endTime / 1000f)

        when (layer) {
            is ShapeLayer -> {
                holder.typeBadge.text = "SHAPE"
                holder.typeBadge.setBackgroundResource(R.drawable.badge_shape)
            }
            is TextLayer -> {
                holder.typeBadge.text = "TEXT"
                holder.typeBadge.setBackgroundResource(R.drawable.badge_text)
            }
            is MediaLayer -> {
                holder.typeBadge.text = if (layer.isVideo) "VIDEO" else "IMAGE"
                holder.typeBadge.setBackgroundResource(R.drawable.badge_media)
            }
            is AudioLayer -> {
                holder.typeBadge.text = "AUDIO"
                holder.typeBadge.setBackgroundResource(R.drawable.badge_audio)
            }
        }

        holder.eyeButton.setImageResource(
            if (layer.visible) android.R.drawable.ic_menu_view else android.R.drawable.ic_menu_close_clear_cancel
        )

        holder.eyeButton.setOnClickListener {
            layer.visible = !layer.visible
            notifyItemChanged(holder.adapterPosition)
            onVisibilityChanged(layer, layer.visible)
        }
    }

    override fun getItemCount(): Int = layers.size
}
