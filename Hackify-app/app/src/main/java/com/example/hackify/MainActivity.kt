package com.example.Hackify

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : Activity() {

    private val TAG = "MainActivity"
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val ESP32_MAC_ADDRESS = "FC:E8:C0:76:12:3E"
    private val UUID_BT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var bluetoothSocket: BluetoothSocket? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 101
    private val LOCATION_PERMISSION_REQUEST_CODE = 100

    private lateinit var buttonSend: Button
    private lateinit var buttonReconnect: Button
    private lateinit var textViewResponse: TextView
    private lateinit var textViewTimer: TextView

    private var isReconnecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        buttonSend = findViewById(R.id.buttonSend)
        buttonReconnect = findViewById(R.id.buttonReconnect)
        textViewResponse = findViewById(R.id.textViewResponse)
        textViewTimer = findViewById(R.id.textViewTimer)

        buttonReconnect.visibility = View.GONE // Botão inicialmente invisível
        textViewTimer.visibility = View.GONE

        checkBluetoothPermissions()
        checkLocationPermissions()

        buttonSend.setOnClickListener {
            if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                sendMessage("Olá ESP32!")

                CoroutineScope(Dispatchers.Main).launch {
                    val response = withContext(Dispatchers.IO) { receiveMessage() }
                    textViewResponse.text = response
                }
            } else {
                textViewResponse.text = "Bluetooth não está conectado!"
                buttonReconnect.visibility = View.VISIBLE // Mostra o botão em caso de erro
            }
        }

        buttonReconnect.setOnClickListener {
            if (!isReconnecting) {
                startReconnectionProcess()
            }
        }
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    BLUETOOTH_PERMISSION_REQUEST_CODE
                )
            } else {
                initBluetooth()
            }
        } else {
            initBluetooth()
        }
    }

    private fun initBluetooth() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth não está habilitado")
            return
        }
        connectToESP32()
    }

    private fun connectToESP32() {
        try {
            val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(ESP32_MAC_ADDRESS)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_BT)
            bluetoothAdapter.cancelDiscovery()
            bluetoothSocket!!.connect()
            Log.i(TAG, "Conectado ao ESP32")
            buttonReconnect.visibility = View.GONE // Esconde o botão ao conectar
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao conectar: ${e.message}")
            e.printStackTrace()
            buttonReconnect.visibility = View.VISIBLE // Mostra o botão ao detectar erro
        }
    }

    private fun sendMessage(message: String) {
        try {
            if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                val outputStream: OutputStream = bluetoothSocket!!.outputStream
                outputStream.write(message.toByteArray())
                Log.i(TAG, "Mensagem enviada: $message")
            } else {
                throw Exception("Bluetooth não conectado!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar mensagem: ${e.message}")
            e.printStackTrace()
            buttonReconnect.visibility = View.VISIBLE // Mostra o botão em caso de erro
        }
    }

    private suspend fun receiveMessage(): String {
        val buffer = ByteArray(1024)
        val receivedData = StringBuilder()

        try {
            if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                val inputStream: InputStream = bluetoothSocket!!.inputStream

                while (true) {
                    val bytes = inputStream.read(buffer)
                    val message = String(buffer, 0, bytes)
                    receivedData.append(message)

                    if (message.contains("FIM")) {
                        break
                    }
                }

                return receivedData.toString().replace("FIM", "").trim()
            } else {
                return "Bluetooth não conectado!"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao receber mensagem: ${e.message}")
            return "Erro ao receber mensagem"
        }
    }

    private fun startReconnectionProcess() {
        isReconnecting = true
        buttonReconnect.isEnabled = false
        var timeRemaining = 10

        val handler = Handler()
        val runnable = object : Runnable {
            override fun run() {
                if (timeRemaining > 0) {
                    textViewTimer.text = "A reconexão será feita em: $timeRemaining segundos"
                    timeRemaining--
                    handler.postDelayed(this, 1000)
                } else {
                    textViewTimer.text = "Pronto para enviar!"
                    isReconnecting = false
                    connectToESP32()
                    buttonReconnect.isEnabled = true

                    if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                        buttonReconnect.visibility = View.GONE // Esconde o botão se a conexão foi bem-sucedida
                    }
                }
            }
        }
        handler.post(runnable)
    }

    private fun checkLocationPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            } else {
                accessLocation()
            }
        } else {
            Log.i(TAG, "Permissões de localização não são necessárias no Android 12+")
        }
    }

    private fun accessLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    // Log.i(TAG, "Localização: Latitude $latitude, Longitude $longitude")
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    initBluetooth()
                } else {
                    Log.e(TAG, "Permissões de Bluetooth negadas")
                }
            }
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    accessLocation()
                } else {
                    Log.e(TAG, "Permissão de localização negada")
                }
            }
        }
    }
}
