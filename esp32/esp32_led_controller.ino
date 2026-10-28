#include <WiFi.h>
#include <Wire.h>
#include <Adafruit_MLX90614.h>

// Wi-Fi credentials
const char* ssid = "Mego Wifi";
const char* password = "kyth119904";

// Pin definitions
#define MQ2_AO_PIN     34
#define FLAME_DO_PIN   19
#define LED_PIN        23

// MLX90614
Adafruit_MLX90614 mlx = Adafruit_MLX90614();

void setup() {
  Serial.begin(115200);
  delay(1000);

  // Pin setup
  pinMode(FLAME_DO_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT);

  Serial.println();
  Serial.println("StoveAide - Module Check");
  Serial.println("------------------------");

  // --- WiFi ---
  WiFi.begin(ssid, password);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.println("✔ Wi-Fi Connected: " + String(WiFi.SSID()));
  Serial.println("  IP: " + WiFi.localIP().toString());
  Serial.println("  Signal: " + String(WiFi.RSSI()) + " dBm");
  Serial.println();

  // --- MLX90614 ---
  Serial.print("Checking MLX90614 (IR Temp)... ");
  if (mlx.begin()) {
    Serial.println("✔ Connected");
    Serial.println("  Ambient Temp: " + String(mlx.readAmbientTempC()) + " °C");
    Serial.println("  Object Temp:  " + String(mlx.readObjectTempC()) + " °C");
  } else {
    Serial.println("✘ NOT FOUND — Check SDA(D21) SCL(D22) wiring");
  }
  Serial.println();

  // --- MQ-2 ---
  Serial.print("Checking MQ-2 (Gas Sensor)... ");
  int gasValue = analogRead(MQ2_AO_PIN);
  if (gasValue > 0) {
    Serial.println("✔ Reading signal");
    Serial.println("  Raw value: " + String(gasValue) + " / 4095");
  } else {
    Serial.println("✘ No signal — Check VIN and D34 wiring");
  }
  Serial.println();

  // --- HW-072 Flame Sensor ---
  Serial.print("Checking HW-072 (Flame Sensor)... ");
  int flameValue = digitalRead(FLAME_DO_PIN);
  Serial.println("✔ Reading signal");
  Serial.println("  Flame detected: " + String(flameValue == LOW ? "YES" : "NO"));
  Serial.println();

  // --- LED ---
  Serial.print("Checking LED... ");
  digitalWrite(LED_PIN, HIGH);
  delay(1000);
  digitalWrite(LED_PIN, LOW);
  Serial.println("✔ Blink test done (D23)");
  Serial.println();

  Serial.println("------------------------");
  Serial.println("Module check complete.");
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("Wi-Fi OK | Gas: " + String(analogRead(MQ2_AO_PIN)) +
                   " | Flame: " + String(digitalRead(FLAME_DO_PIN) == LOW ? "YES" : "NO"));
  } else {
    Serial.println("Wi-Fi connection lost.");
  }
  delay(5000);
}
