package com.example.halepos

import android.net.Uri

data class ImageUri(
    val url: String
)

data class MenuItem(
    val name: String,
    val price: Int,
    val imageUri: ImageUri?,
    var quantity: Int = 0
){
    fun getImageUri(): Uri? {
        return imageUri?.url?.let { Uri.parse(it) }
    }
}
