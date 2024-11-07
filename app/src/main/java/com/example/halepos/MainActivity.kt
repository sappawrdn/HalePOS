package com.example.halepos

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.halepos.databinding.ActivityMainBinding
import com.example.halepos.databinding.DialogAddMenuBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private val sharedPreferences by lazy {
        getSharedPreferences("menu_preferences", MODE_PRIVATE)
    }

    private lateinit var binding: ActivityMainBinding
    private val STORAGE_PERMISSION_CODE = 101
    private val IMAGE_PICK_CODE = 102
    private lateinit var menuAdapter: MenuAdapter
    private val menuList = mutableListOf<MenuItem>()
    private var selectedImageUri: Uri? = null
    private var dialogBinding: DialogAddMenuBinding? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        menuAdapter = MenuAdapter(menuList, { position ->
            showDeleteConfirmationDialog(position)
        }, { hasOrder ->
            if (hasOrder) {
                binding.btnPlaceOrder.visibility = View.VISIBLE
            } else {
                binding.btnPlaceOrder.visibility = View.GONE
            }
        })

        val layoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = menuAdapter

        loadMenuItems()

        binding.fabAddMenu.setOnClickListener{
            showAddMenuDialog()
        }

        binding.editTextSearch.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                menuAdapter.filter(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })


        binding.btnPlaceOrder.setOnClickListener {
            val orderItems = menuList.filter { it.quantity > 0 }.map {
                OrderItem(it.name, it.price, it.quantity, it.imageUri?.url.toString())
            }
            val intent = Intent(this, OrderSummaryActivity::class.java).apply {
                putParcelableArrayListExtra("orderItems", ArrayList(orderItems))
            }
            startActivity(intent)
        }

        binding.btnDownload.setOnClickListener {
            saveCsvToMediaStore()
        }
    }

    private fun saveCsvToMediaStore() {
        val file = File(filesDir, "order_summary.csv")

        if (!file.exists()) {
            Toast.makeText(this, "No data available to download.", Toast.LENGTH_SHORT).show()
            return
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "order_summary.csv")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/POS_Orders/")
        }

        val contentResolver = applicationContext.contentResolver
        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        uri?.let {
            try {
                contentResolver.openOutputStream(it).use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream!!)
                    }
                }
                Toast.makeText(this, "File downloaded to device!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error saving file to device.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Failed to access storage.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun loadMenuItems() {
        val json = sharedPreferences.getString("menu_list", "[]")
        val type = object : TypeToken<List<MenuItem>>() {}.type
        val loadedMenuItems: List<MenuItem> = Gson().fromJson(json, type)
        menuList.addAll(loadedMenuItems)
        menuAdapter.notifyDataSetChanged()
    }

    private fun saveMenuItems() {
        val json = Gson().toJson(menuList)
        sharedPreferences.edit().putString("menu_list", json).apply()
    }

    private fun showAddMenuDialog() {
        // Inflate custom layout untuk dialog
        dialogBinding = DialogAddMenuBinding.inflate(layoutInflater)
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("Tambah Menu")
            .setView(dialogBinding?.root)
            .setPositiveButton("Tambah") { _, _ ->
                // Ambil data dari inputan
                val menuName = dialogBinding?.etMenuName?.text.toString()
                val menuPrice = dialogBinding?.etMenuPrice?.text.toString().toIntOrNull() ?: 0

                if (menuName.isEmpty() || menuPrice == null || menuPrice <= 0) {
                    // Tampilkan dialog warning jika input kosong
                    AlertDialog.Builder(this)
                        .setTitle("Input Tidak Lengkap")
                        .setMessage("Nama menu dan harga harus diisi dengan benar!")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val imageUri = selectedImageUri?.let { ImageUri(it.toString()) }
                    // Tambahkan item baru ke RecyclerView
                    val newItem = MenuItem(menuName, menuPrice, imageUri)
                    menuList.add(newItem)
                    menuAdapter.notifyItemInserted(menuList.size - 1)

                    selectedImageUri = null
                    saveMenuItems()
                }
            }
            .setNegativeButton("Batal", null)

        val dialog = dialogBuilder.create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Ganti warna tulisan pada tombol
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.black))
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.black))

            dialogBinding?.ivMenuImagePreview?.visibility = View.GONE // Pastikan awalnya disembunyikan
        }

        dialogBinding?.btnSelectImage?.setOnClickListener {
            requestStoragePermission()
        }

        dialog.show()

    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), STORAGE_PERMISSION_CODE)
        } else {
            openGallery()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            Log.d("PermissionsResult", "Request code matched")
            if (grantResults.isNotEmpty()) {
                Log.d("PermissionsResult", "Permission result: ${grantResults[0]}")
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("PermissionsResult", "Permission granted")
                    openGallery()
                } else {
                    Log.d("PermissionsResult", "Permission denied")
                    Toast.makeText(this, "Storage permission required to select images.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("PermissionsResult", "grantResults is empty")
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        galleryLauncher.launch(intent)
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                    } else {
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    dialogBinding?.ivMenuImagePreview?.setImageBitmap(bitmap)
                    dialogBinding?.ivMenuImagePreview?.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading image: ${e.message}")
                    Toast.makeText(this, "Error loading image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(position: Int) {
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("Hapus Menu")
            .setMessage("Apakah Anda yakin ingin menghapus item ini?")
            .setPositiveButton("Hapus") { _, _ ->
                // Menghapus item dari daftar
                if (position >= 0 && position < menuList.size) {
                    menuList.removeAt(position)
                    menuAdapter.notifyItemRemoved(position)
                    saveMenuItems()
                } else {
                    Log.e("MainActivity", "Index out of bounds: $position")
                }
            }
            .setNegativeButton("Batal", null)

        dialogBuilder.create().show()
    }


}