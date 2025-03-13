package com.example.hackify

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.hackify.R
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    // Bluetooth-related
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val ESP32_MAC_ADDRESS = "FC:E8:C0:76:12:3E"
    private val UUID_BT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null

    // UI elements
    private lateinit var buttonReconnect: Button
    private lateinit var textViewStatus: TextView

    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa os elementos da UI
        buttonReconnect = findViewById(R.id.buttonReconnect)
        textViewStatus = findViewById(R.id.textViewStatus)

        // Configura o botão de reconectar
        buttonReconnect.setOnClickListener { reconnectToESP32() }

        // Tenta conectar automaticamente ao ESP32 ao abrir o app
        reconnectToESP32()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

    }

    private fun reconnectToESP32() {
        CoroutineScope(Dispatchers.IO).launch {
            connectToESP32()
        }
    }

    private fun connectToESP32() {
        CoroutineScope(Dispatchers.IO).launch {
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(ESP32_MAC_ADDRESS)
            if (device == null) {
                Log.e(TAG, "Dispositivo Bluetooth não encontrado!")
                updateUI("Dispositivo não encontrado!")
                return@launch
            }

            try {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        BLUETOOTH_PERMISSION_REQUEST_CODE
                    )
                    return@launch
                }


                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_BT)

                withTimeout(10000) {
                    Log.d(TAG, "Tentando conectar ao dispositivo: ${device.name}")
                    bluetoothSocket?.connect()
                    Log.d(TAG, "Conectado ao dispositivo: ${device.name}")
                    updateUI("Conectado ao ESP32!")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Tempo limite excedido ao conectar")
                updateUI("Tempo limite excedido!")
                bluetoothSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Erro ao conectar: ${e.message}")
                updateUI("Erro na conexão!")
                bluetoothSocket?.close()
            }
        }
    }


    fun sendMessageToESP32(message: String, callback: (String) -> Unit) {
        if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val outputStream: OutputStream = bluetoothSocket!!.outputStream
                    outputStream.write(message.toByteArray())

                    val response = receiveMessageFromESP32()
                    withContext(Dispatchers.Main) {
                        callback(response)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao enviar mensagem: ${e.message}")
                    withContext(Dispatchers.Main) {
                        callback("Erro ao enviar mensagem")
                    }
                }
            }
        } else {
            callback("Bluetooth não está conectado!")
        }
    }

    private suspend fun receiveMessageFromESP32(): String {
        val buffer = ByteArray(1024)
        val receivedData = StringBuilder()

        return withContext(Dispatchers.IO) {
            try {
                if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                    val inputStream: InputStream = bluetoothSocket!!.inputStream

                    while (true) {
                        val bytes = inputStream.read(buffer)
                        val message = String(buffer, 0, bytes)
                        receivedData.append(message)

                        if (message.contains("FIM")) break
                    }

                    receivedData.toString().replace("FIM", "").trim()
                } else {
                    "Bluetooth não conectado!"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao receber mensagem: ${e.message}")
                "Erro ao receber mensagem"
            }
        }
    }

    private fun updateUI(message: String) {
        runOnUiThread {
            textViewStatus.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar o socket: ${e.message}")
        }
    }
}
