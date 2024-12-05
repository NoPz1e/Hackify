package com.example.Hackify

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.Hackify.R
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

    private val TAG = "MainActivity" // Para logs
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val ESP32_MAC_ADDRESS = "FC:E8:C0:76:12:3E" // Endereço MAC do ESP32
    private val UUID_BT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID padrão SPP

    private var bluetoothSocket: BluetoothSocket? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 101
    private val LOCATION_PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar o provedor de localização
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Botão para enviar mensagem ao ESP32
        val buttonSend = findViewById<Button>(R.id.buttonSend)
        val textViewResponse = findViewById<TextView>(R.id.textViewResponse)

        // Verificar permissões de Bluetooth e localização
        checkBluetoothPermissions()
        checkLocationPermissions()

        buttonSend.setOnClickListener {
            // Envia a mensagem ao ESP32
            sendMessage("Olá ESP32!")

            // Inicia a tarefa de receber a resposta na thread de fundo
            CoroutineScope(Dispatchers.Main).launch {
                val response = withContext(Dispatchers.IO) {
                    // Chama a função de receber a mensagem, que ocorre em uma thread de fundo
                    receiveMessage()
                }
                // Atualiza o TextView com a resposta recebida
                textViewResponse.text = response
            }
        }
    }


    // Verificação de permissões de Bluetooth
    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
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

    // Verificação de permissões de localização
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

    // Inicializar Bluetooth e conectar ao ESP32
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
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao conectar: ${e.message}")
            e.printStackTrace()
        }
    }

    // Enviar mensagem ao ESP32
    private fun sendMessage(message: String) {
        try {
            if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                val outputStream: OutputStream = bluetoothSocket!!.outputStream
                outputStream.write(message.toByteArray())
                Log.i(TAG, "Mensagem enviada: $message")
            } else {
                Log.e(TAG, "Bluetooth não conectado!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar mensagem: ${e.message}")
            e.printStackTrace()
        }
    }

    // Receber mensagem do ESP32
// Essa função agora retorna um Job (coroutine) e não mais um valor diretamente
    private suspend fun receiveMessage(): String {
        return try {
            if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                val inputStream: InputStream = bluetoothSocket!!.inputStream
                val buffer = ByteArray(1024)
                val bytes = inputStream.read(buffer)
                val message = String(buffer, 0, bytes)
                Log.i(TAG, "Mensagem recebida: $message")
                message
            } else {
                "Bluetooth não conectado!"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao receber mensagem: ${e.message}")
            "Erro ao receber mensagem"
        }
    }


    // Acessar a última localização conhecida
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
                    Log.i(TAG, "Localização: Latitude $latitude, Longitude $longitude")
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