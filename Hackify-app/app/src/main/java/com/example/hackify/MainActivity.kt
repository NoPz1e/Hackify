package com.example.Hackify

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.hackify.HomeFragment
import com.example.hackify.ProfileFragment
import com.example.hackify.SearchFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    // Bluetooth-related
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val ESP32_MAC_ADDRESS = "FC:E8:C0:76:12:3E"
    private val UUID_BT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null

    // Permissions and request codes
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 101
    private val LOCATION_PERMISSION_REQUEST_CODE = 100

    // UI elements
    private lateinit var buttonSend: Button
    private lateinit var buttonReconnect: Button
    private lateinit var textViewResponse: TextView
    private lateinit var textViewTimer: TextView

    // Location-related
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // States
    private var isReconnecting = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        buttonSend = findViewById(R.id.buttonSend)
        buttonReconnect = findViewById(R.id.buttonReconnect)
        textViewResponse = findViewById(R.id.textViewResponse)
        textViewTimer = findViewById(R.id.textViewTimer)

        buttonReconnect.visibility = View.GONE
        textViewTimer.visibility = View.GONE

        checkPermissions()

        buttonSend.setOnClickListener { sendMessageToESP32() }
        buttonReconnect.setOnClickListener { reconnectToESP32() }

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_search -> {
                    replaceFragment(SearchFragment())
                    true
                }
                R.id.nav_profile -> {
                    replaceFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }

        // Define o fragmento inicial
        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_home
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkBluetoothPermissions()
        } else {
            initBluetooth()
        }
        checkLocationPermissions()
    }

    private fun checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        } else {
            initBluetooth()
        }
    }

    private fun checkLocationPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            } else {
                accessLocation()
            }
        } else {
            Log.i(TAG, "Permissões de localização não são necessárias no Android 12+")
        }
    }

    private fun initBluetooth() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth não está habilitado")
        } else {
            connectToESP32()
        }
    }

    private fun connectToESP32() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(ESP32_MAC_ADDRESS)
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_BT)
                bluetoothAdapter.cancelDiscovery()
                bluetoothSocket!!.connect()

                withContext(Dispatchers.Main) {
                    Log.i(TAG, "Conectado ao ESP32")
                    buttonReconnect.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar: ${e.message}")
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    buttonReconnect.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun sendMessageToESP32() {
        if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val outputStream: OutputStream = bluetoothSocket!!.outputStream
                    outputStream.write("Olá ESP32!".toByteArray())
                    Log.i(TAG, "Mensagem enviada: Olá ESP32!")

                    val response = receiveMessageFromESP32()
                    withContext(Dispatchers.Main) {
                        textViewResponse.text = response
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao enviar mensagem: ${e.message}")
                    withContext(Dispatchers.Main) {
                        textViewResponse.text = "Erro ao enviar mensagem"
                    }
                }
            }
        } else {
            textViewResponse.text = "Bluetooth não está conectado!"
            buttonReconnect.visibility = View.VISIBLE
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

    private fun reconnectToESP32() {
        if (!isReconnecting) {
            isReconnecting = true
            buttonReconnect.isEnabled = false

            val timeRemaining = 10
            textViewTimer.visibility = View.VISIBLE

            handler.post(object : Runnable {
                var secondsLeft = timeRemaining
                override fun run() {
                    if (secondsLeft > 0) {
                        textViewTimer.text = "Reconectando em: $secondsLeft segundos"
                        secondsLeft--
                        handler.postDelayed(this, 1000)
                    } else {
                        textViewTimer.visibility = View.GONE
                        isReconnecting = false
                        buttonReconnect.isEnabled = true
                        connectToESP32()
                    }
                }
            })
        }
    }

    private fun accessLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.i(TAG, "Localização: Latitude ${location.latitude}, Longitude ${location.longitude}")
            }
        }
    }
}
