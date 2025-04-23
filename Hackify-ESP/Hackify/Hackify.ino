#include "BluetoothSerial.h"
#include <WiFi.h>

BluetoothSerial SerialBT;

void setup() {
  pinMode(16, OUTPUT);
  Serial.begin(115200);
  SerialBT.begin("ESP32_BT");

  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  delay(100);

  Serial.println("ESP32 pronto para receber mensagens via Bluetooth");
}

void loop() {
  if (SerialBT.available()) {
    String message = "";
    while (SerialBT.available()) {
      char c = SerialBT.read();
      message += c;
      if (c == '\n') break;
    }
    message.trim();

    if (message == "Olá ESP32!") {
      digitalWrite(16, HIGH);
      redesDisponiveis();
      digitalWrite(16, LOW);
    }

    Serial.print("Mensagem recebida: ");
    Serial.println(message);
  }
}

void redesDisponiveis() {
  String resultado = "";
  Serial.println("Iniciando a varredura de redes Wi-Fi...");
  int numRedes = WiFi.scanNetworks();

  resultado = "Número de redes encontradas: " + String(numRedes) + "\n";
  Serial.println("Número de redes encontradas: "+ String(numRedes));
  for (int i = 0; i < numRedes; ++i) {
    resultado += "Rede #" + String(i + 1) + ": " + WiFi.SSID(i) + "\n";
    resultado += "RSSI: " + String(WiFi.RSSI(i)) + " dBm\n";
    resultado += "Tipo: ";
    switch (WiFi.encryptionType(i)) {
      case WIFI_AUTH_OPEN: resultado += "Aberta"; break;
      case WIFI_AUTH_WEP: resultado += "WEP"; break;
      case WIFI_AUTH_WPA_PSK: resultado += "WPA/PSK"; break;
      case WIFI_AUTH_WPA2_PSK: resultado += "WPA2/PSK"; break;
      case WIFI_AUTH_WPA_WPA2_PSK: resultado += "WPA/WPA2/PSK"; break;
      case WIFI_AUTH_WPA3_PSK: resultado += "WPA3/PSK"; break;
      default: resultado += "Desconhecida"; break;
    }
    resultado += "\n-----------------------\n";
  }

  const int CHUNK_SIZE = 20;
  for (int i = 0; i < resultado.length(); i += CHUNK_SIZE) {
   String chunk = resultado.substring(i, min(i + CHUNK_SIZE, (int)resultado.length()));

    SerialBT.print(chunk);
    delay(50);
  }
  SerialBT.println("FIM");
}