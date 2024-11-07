package com.example.halepos

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.halepos.databinding.ItemMenuBinding

class MenuAdapter(private val menuList: List<MenuItem>,
                  private val onItemLongPress: (Int) -> Unit,
                  private val onQuantityChanged: (Boolean) -> Unit) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    private var filteredList: List<MenuItem> = menuList

    inner class MenuViewHolder(val binding: ItemMenuBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = ItemMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = menuList[position]
        with(holder.binding) {
            item.imageUri?.let {
                imageView.setImageURI(item.getImageUri())
            } ?: imageView.setImageResource(R.drawable.fore)
            textMenuName.text = item.name
            textPrice.text = "Rp ${item.price}"
            tvQuantity.text = item.quantity.toString()

            if (item.quantity > 0) {
                btnAddItem.visibility = View.GONE
                layoutQuantity.visibility = View.VISIBLE
            } else {
                btnAddItem.visibility = View.VISIBLE
                layoutQuantity.visibility = View.GONE
            }


            btnAddItem.setOnClickListener {
                btnAddItem.visibility = View.GONE
                layoutQuantity.visibility = View.VISIBLE
                btnAddItem2.visibility = View.VISIBLE
                item.quantity = 1
                tvQuantity.text = item.quantity.toString()
                onQuantityChanged(hasOrderedItem())
            }

            btnAddItem2.setOnClickListener {
                btnAddItem2.visibility = View.GONE
                btnAddItem.visibility = View.VISIBLE
                layoutQuantity.visibility = View.GONE
                item.quantity = 0
                onQuantityChanged(hasOrderedItem())
            }

            btnIncrease.setOnClickListener {
                item.quantity++
                tvQuantity.text = item.quantity.toString()
                onQuantityChanged(hasOrderedItem())
            }

            btnDecrease.setOnClickListener {
                if (item.quantity > 1) {
                    item.quantity--
                    tvQuantity.text = item.quantity.toString()
                } else {
                    // Hide layoutQuantity and show btnAddItem if quantity reaches 0
                    item.quantity = 0
                    tvQuantity.text = item.quantity.toString()
                    layoutQuantity.visibility = View.GONE
                    btnAddItem2.visibility = View.GONE
                    btnAddItem.visibility = View.VISIBLE
                }
                onQuantityChanged(hasOrderedItem())
            }
        }

        // Menangani penekanan lama pada item
        holder.itemView.setOnLongClickListener {
            onItemLongPress(position)
            true // Mengembalikan true untuk menunjukkan bahwa event telah ditangani
        }
    }

    override fun getItemCount() = filteredList.size

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            menuList
        } else {
            menuList.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    private fun hasOrderedItem(): Boolean {
        return filteredList.any { it.quantity > 0 }
    }
}