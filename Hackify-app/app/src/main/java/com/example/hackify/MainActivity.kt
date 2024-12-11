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

    private lateinit var buttonSend: Button
    private lateinit var buttonReconnect: Button
    private lateinit var textViewResponse: TextView
    private lateinit var textViewTimer: TextView

    private var isReconnecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar o provedor de localização
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicializando os botões e o temporizador
        buttonSend = findViewById(R.id.buttonSend)
        buttonReconnect = findViewById(R.id.buttonReconnect)
        textViewResponse = findViewById(R.id.textViewResponse)
        textViewTimer = findViewById(R.id.textViewTimer)

        // Verificar permissões de Bluetooth e localização
        checkBluetoothPermissions()
        checkLocationPermissions()

        // Quando o botão "Enviar" é pressionado, envia a mensagem
        buttonSend.setOnClickListener {
            if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                sendMessage("Olá ESP32!")

                // Inicia a tarefa de receber a resposta
                CoroutineScope(Dispatchers.Main).launch {
                    val response = withContext(Dispatchers.IO) {
                        // Chama a função de receber a mensagem
                        receiveMessage()
                    }
                    // Atualiza o TextView com a resposta
                    textViewResponse.text = response
                }
            } else {
                // Caso o Bluetooth não esteja conectado, exibe uma mensagem de erro
                textViewResponse.text = "Bluetooth não está conectado!"
            }
        }

        // Quando o botão "Reconectar" é pressionado, tenta reconectar com o ESP32
        buttonReconnect.setOnClickListener {
            if (!isReconnecting) {
                startReconnectionProcess()
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

    // Função que envia a mensagem para o ESP32
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

    // Função para receber a mensagem do ESP32
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

    // Função para iniciar o processo de reconexão com temporizador
    private fun startReconnectionProcess() {
        isReconnecting = true
        buttonReconnect.isEnabled = false // Desabilita o botão enquanto reconecta
        var timeRemaining = 10 // Tempo de espera em segundos (10 segundos, por exemplo)

        val handler = Handler()
        val runnable = object : Runnable {
            override fun run() {
                if (timeRemaining > 0) {
                    textViewTimer.text = "A reconexão será feita em: $timeRemaining segundos"
                    timeRemaining--
                    handler.postDelayed(this, 1000)
                } else {
                    textViewTimer.text = "Pronto para enviar!"
                    buttonReconnect.isEnabled = true // Habilita o botão novamente
                    isReconnecting = false
                    connectToESP32() // Tenta reconectar com o ESP32 após o temporizador
                }
            }
        }
        handler.post(runnable)
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

    // Função para acessar a localização (caso necessário)
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
