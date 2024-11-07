package com.example.halepos

import com.example.halepos.R
import android.Manifest
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.halepos.databinding.ActivityOrderSummaryBinding
import com.example.halepos.databinding.DialogCustomBinding
import com.example.halepos.databinding.PrintBillLayoutBinding
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class OrderSummaryActivity : AppCompatActivity() {

    private val REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 1
    private lateinit var binding: ActivityOrderSummaryBinding
    private lateinit var orderSummaryAdapter: OrderSummaryAdapter
    private var orderItems = mutableListOf<OrderItem>()

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!hasWritePermission()) {
            requestWritePermission()
        }

        onBackPressedDispatcher.addCallback(this) {
            showCustomConfirmationDialog()
        }

        val cashierNames = arrayOf("Yusuf", "Beril", "Samuel")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cashierNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.textCashierName.adapter = adapter

        setCurrentDate()

        orderItems = intent.getParcelableArrayListExtra("orderItems") ?: mutableListOf()

        orderSummaryAdapter = OrderSummaryAdapter(orderItems)
        binding.recyclerViewOrderItems.adapter = orderSummaryAdapter
        binding.recyclerViewOrderItems.layoutManager = LinearLayoutManager(this)


        binding.textTotalItems.text = "Total Items (${orderItems.size})"

        calculatePaymentSummary()

        binding.cashNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                calculateChange()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.buttonPlaceOrder.setOnClickListener {
            val total = orderItems.sumOf { it.price * it.quantity }
            val cashInput = binding.cashNumber.text.toString().toIntOrNull() ?: 0

            if (cashInput < total) {
                // Tampilkan dialog konfirmasi
                showPaymentConfirmationDialog(total, cashInput)
            } else {
                prepareOrderCsvData(orderItems)
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
//            if (hasWritePermission()) {
//                saveOrderToCSV(orderItems)
//            } else {
//                requestWritePermission()
//            }
        }

        binding.buttonPrintOrder.setOnClickListener {
            showBillSummary()
        }

    }

    private fun showBillSummary(){
        val billBinding = PrintBillLayoutBinding.inflate(layoutInflater)

        billBinding.dateTime.text = SimpleDateFormat("dd MMM yy HH:mm", Locale.getDefault()).format(Date())
        billBinding.cashierName.text = binding.textCashierName.selectedItem.toString()

        val itemsText = orderItems.joinToString("\n") { "${it.quantity} ${it.name}" }
        val pricesText = orderItems.joinToString("\n") { "Rp.${it.price * it.quantity}" }
        billBinding.itemName.text = itemsText
        billBinding.itemPrice.text = pricesText

        val totalAmount = orderItems.sumOf { it.price * it.quantity }
        billBinding.subtotal.text = "Subtotal ${orderItems.size} Produk Rp.${totalAmount}"
        billBinding.total.text = "Total Tagihan Rp.${totalAmount}"
        billBinding.cash.text = "Tunai Rp.${binding.cashNumber.text.toString()}"
        billBinding.totalPaid.text = "Total Bayar Rp.${totalAmount}"

        billBinding.dateTimePaid.text = SimpleDateFormat("dd MMM yy HH:mm", Locale.getDefault()).format(Date())

        val dialog = AlertDialog.Builder(this)
            .setView(billBinding.root)
            .setPositiveButton("Print") { _, _ ->
                // Trigger print functionality here if needed
                printBill(billBinding.root)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun printBill(billView: View) {
        // Convert view to bitmap
        val bitmap = Bitmap.createBitmap(billView.width, billView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        billView.draw(canvas)

        // Send bitmap to printer
        // You need to implement your printer-specific logic here
        // Example:
        // printerManager.printBitmap(bitmap)

        Toast.makeText(this, "Bill printed successfully", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showPaymentConfirmationDialog(total: Int, cashInput: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom, null)
        val dialogBinding = DialogCustomBinding.bind(dialogView)

        // Mengatur isi dialog
        dialogBinding.dialogTitle.text = "Konfirmasi Pembayaran"
        dialogBinding.dialogMessage.text = "Jumlah uang tunai yang dimasukkan kurang dari total Rp.$total. Apakah Anda ingin melanjutkan pembayaran?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogBinding.buttonYes.setOnClickListener {
            prepareOrderCsvData(orderItems) // Melanjutkan ke penyimpanan order
            dialog.dismiss()
        }

        dialogBinding.buttonNo.setOnClickListener {
            dialog.dismiss() // Menutup dialog tanpa melanjutkan
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Menghilangkan background default
        dialog.show()
    }

    private fun showCustomConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Menggunakan binding untuk mendapatkan referensi ke elemen di dalam dialog
        val binding = DialogCustomBinding.bind(dialogView) // Ganti dengan nama binding sesuai dengan nama file layout XML

        binding.dialogTitle.text = "Konfirmasi"
        binding.dialogMessage.text = "Apakah Anda yakin ingin membatalkan pesanan ini?"

        binding.buttonYes.setOnClickListener {
            finish() // Menutup activity jika pengguna mengonfirmasi
            dialog.dismiss()
        }

        binding.buttonNo.setOnClickListener {
            dialog.dismiss() // Menutup dialog tanpa menutup activity
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Jika ingin menghilangkan background default
        dialog.show()
    }

    private fun hasWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestWritePermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE
        )
    }


    private fun prepareOrderCsvData(orderItems: List<OrderItem>) {
        val cashierName = binding.textCashierName.selectedItem.toString()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentDate = Date()
        val date = dateFormat.format(currentDate)
        val total = orderItems.sumOf { it.price * it.quantity }
        val itemNames = orderItems.joinToString(", ") { it.name }
        val prices = orderItems.joinToString(", ") { it.price.toString() }
        val itemCounts = orderItems.joinToString(", ") { it.quantity.toString() }
        val pricePerItem = orderItems.joinToString(", ") { (it.price * it.quantity).toString() }
        val csvData = "$date,$cashierName,\"$itemNames\",\"$prices\",\"$itemCounts\",\"$pricePerItem\",$total\n"

        val file = File(filesDir, "order_summary.csv")
        val fileExists = file.exists()

        try {
            FileWriter(file, true).use { writer ->
                if (!fileExists) {
                    writer.append("Date,Cashier Name,Item Names,Prices,Quantities,Price Per Item,Total\n")
                }
                writer.append(csvData)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving order data internally.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setCurrentDate() {
        val currentDate = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.textDate.text = dateFormat.format(currentDate)
    }

    private fun calculatePaymentSummary() {
        val price = orderItems.sumOf { it.price * it.quantity }
        val total = price
        binding.numTotal.text = "Rp.${total}"
    }

    private fun calculateChange() {
        val total = orderItems.sumOf { it.price * it.quantity }
        val cashInput = binding.cashNumber.text.toString().toIntOrNull() ?: 0
        val change = cashInput - total

        // Display the change in the changeNumber TextView
        binding.changeNumber.text = "Rp.${change}"
    }
}