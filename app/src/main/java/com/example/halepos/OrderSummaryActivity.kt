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
import android.util.DisplayMetrics
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
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
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

    companion object {
        const val PERMISSION_BLUETOOTH = 1001
        const val PERMISSION_BLUETOOTH_ADMIN = 1002
        const val PERMISSION_BLUETOOTH_CONNECT = 1003
        const val PERMISSION_BLUETOOTH_SCAN = 1004
    }

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
                printBill()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun printBill() {
        // Cek izin Bluetooth
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH),
                PERMISSION_BLUETOOTH
            )
            return
        } else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_ADMIN),
                PERMISSION_BLUETOOTH_ADMIN
            )
            return
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                PERMISSION_BLUETOOTH_CONNECT
            )
            return
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                PERMISSION_BLUETOOTH_SCAN
            )
            return
        }

        // Jika izin sudah diberikan, lanjutkan dengan proses cetak
        val bluetoothConnection: BluetoothConnection? = BluetoothPrintersConnections.selectFirstPaired()
        if (bluetoothConnection == null) {
            Toast.makeText(this, "Printer tidak ditemukan atau belum terhubung", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val printer = EscPosPrinter(bluetoothConnection, 203, 58f, 32)
            val cashierName = binding.textCashierName.selectedItem.toString()
            val dateTime = SimpleDateFormat("dd MMM yy HH:mm", Locale.getDefault()).format(Date())
            val itemsText = orderItems.joinToString("\n") { "${it.quantity} ${it.name}  Rp.${it.price * it.quantity}" }
            val totalAmount = orderItems.sumOf { it.price * it.quantity }
            val cash = binding.cashNumber.text.toString()

            val printText = "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, this.getApplicationContext().getResources().getDrawableForDensity(R.drawable.logo, DisplayMetrics.DENSITY_MEDIUM))+"</img>\n" +
                    "[L]\n" +
                    "[C]<u><font size='big'>Hale Coffee</font></u>\n" +
                    "[C]================================\n"
                    "[L]Date/Time: $dateTime\n" +
                    "[L]Cashier: $cashierName\n" +
                    "[L]-----------------------------\n" +
                    "[L]<b>Items</b>\n" +
                    itemsText + "\n" +
                    "[L]-----------------------------\n" +
                    "[L]Subtotal: Rp.${totalAmount}\n" +
                    "[L]Cash: Rp.${cash}\n" +
                    "[L]-----------------------------\n" +
                    "[L]<b>Total: Rp.${totalAmount}</b>\n" +
                    "[C]================================\n" +
                    "[C]IG : @h.co_medan\n" +
                    "[C]Thank you for your purchase!\n"

            printer.printFormattedText(printText)
            Toast.makeText(this, "Bill printed successfully", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to print bill", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Cek ulang permintaan izin dan lanjutkan proses cetak jika izin sudah diberikan
            printBill()
        } else {
            Toast.makeText(this, "Izin Bluetooth diperlukan untuk mencetak", Toast.LENGTH_SHORT).show()
        }
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