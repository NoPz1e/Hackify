package com.example.hackify

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.hackify.R
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.UUID

class HomeFragment : Fragment() {
    private lateinit var buttonSend: Button
    private lateinit var textViewResponse: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializa os elementos do layout do fragmento
        buttonSend = view.findViewById(R.id.buttonSend)
        textViewResponse = view.findViewById(R.id.textViewResponse)

        // Adiciona um Log para verificar se o Fragment está carregando
        Log.d("HomeFragment", "Fragment carregado com sucesso!")

        // Configura o botão para enviar mensagem
        buttonSend.setOnClickListener { sendMessage() }
    }

    private fun sendMessage() {
        val activity = requireActivity() as MainActivity
        activity.sendMessageToESP32("Olá ESP32!") { response ->
            textViewResponse.text = response
        }
    }
}
