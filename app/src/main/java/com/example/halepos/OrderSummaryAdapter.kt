package com.example.halepos

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.halepos.databinding.ItemOrderSummaryBinding

class OrderSummaryAdapter (private val orderItems: List<OrderItem>) :
    RecyclerView.Adapter<OrderSummaryAdapter.OrderSummaryViewHolder>() {

    inner class OrderSummaryViewHolder(val binding: ItemOrderSummaryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderSummaryViewHolder {
        val binding = ItemOrderSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderSummaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderSummaryViewHolder, position: Int) {
        val item = orderItems[position]
        with(holder.binding) {
            item.imageUri?.let { uriString ->
                val uri = Uri.parse(uriString)
                imageViewItem.setImageURI(uri)
            } ?: imageViewItem.setImageResource(R.drawable.fore)
            textItemName.text = item.name
            textItemQuantityAndPrice.text = "(${item.price}) x ${item.quantity}"
            textItemTotalPrice.text = "Rp.${item.getTotalPrice()}"
        }
    }

    override fun getItemCount() = orderItems.size
}