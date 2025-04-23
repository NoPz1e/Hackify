package com.example.hackify

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var macAddressInput: EditText
    private lateinit var saveButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        macAddressInput = view.findViewById(R.id.macAddressInput)
        saveButton = view.findViewById(R.id.saveButton)

        // Carrega o MAC salvo (ou valor padrão se ainda não existir)
        val sharedPref = requireActivity()
            .getSharedPreferences("HackifyPrefs", Context.MODE_PRIVATE)
        macAddressInput.setText(
            sharedPref.getString("esp_mac", "FC:E8:C0:76:12:3E")
        )

        saveButton.setOnClickListener {
            val mac = macAddressInput.text.toString().trim()

            // Validação simples de MAC (XX:XX:XX:XX:XX:XX)
            if (!mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) {
                Toast.makeText(
                    requireContext(),
                    "MAC inválido. Ex: FC:E8:C0:76:12:3E",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // Salva no SharedPreferences
            sharedPref.edit()
                .putString("esp_mac", mac)
                .apply()

            Toast.makeText(requireContext(), "MAC salvo!", Toast.LENGTH_SHORT).show()
        }
    }
}
