
#include <WiFi.h>
#include <FirebaseESP32.h>
#include <esp_system.h>
#include <time.h>          // NTP real-time clock

#define WIFI_SSID "Mego Wifi"
#define WIFI_PASSWORD "kyth119904"

#define FIREBASE_HOST "stoveaide-default-rtdb.asia-southeast1.firebasedatabase.app"
#define FIREBASE_AUTH "DclUeNzyDz230Y1H651f1TNQfydO81gdYFDoaFjc"

// Device Unique Identifier
#define DEVICE_PATH "/devices/STOVE-PH-8842"

const int LED_PIN = 23; // Safe Output-Capable GPIO Pin (GPIO 23)

// Firebase Objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// State Variables
bool currentActualState = false;
bool lastKnownTargetState = false;

// Heartbeat & Polling Timers
unsigned long lastHeartbeatTime = 0;
const unsigned long HEARTBEAT_INTERVAL_MS = 5000; // Heartbeat every 5 seconds

unsigned long lastPollTime = 0;
const unsigned long POLL_INTERVAL_MS = 500; // Check command every 500ms

bool ntpSynced = false; // Whether NTP has successfully provided real time

void runHardwareBlinkTest() {
  Serial.println("[Hardware Test] Starting external LED blinking self-test...");
  for (int i = 0; i < 3; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(250);
    digitalWrite(LED_PIN, LOW);
    delay(250);
  }
  Serial.println("[Hardware Test] Self-test complete! LED is working properly.");
}

void connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;

  Serial.print("[Wi-Fi] Connecting to: ");
  Serial.println(WIFI_SSID);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[Wi-Fi] Connected successfully!");
    Serial.print("[Wi-Fi] IP Address: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\n[Wi-Fi] Connection failed. Main loop will auto-retry.");
  }
}

void sendDeviceStatus(bool actualState) {
  if (!Firebase.ready()) return;

  String statusPath = String(DEVICE_PATH) + "/status";

  // Send actual hardware state
  Firebase.setBool(fbdo, statusPath + "/actualState", actualState);
  
  // Send device status and metadata
  Firebase.setString(fbdo, statusPath + "/deviceStatus", "ONLINE");
  Firebase.setInt(fbdo, statusPath + "/gpioPin", LED_PIN);
  Firebase.setString(fbdo, statusPath + "/ipAddress", WiFi.localIP().toString());
  
  // Update heartbeat timestamp using real Unix epoch seconds (from NTP)
  // Android compares (currentTimeSeconds - lastHeartbeat) to detect Online/Offline
  long epochSeconds = ntpSynced ? (long)time(nullptr) : (long)(millis() / 1000);
  Firebase.setInt(fbdo, statusPath + "/lastHeartbeat", (int)epochSeconds);

  Serial.print("[Firebase Feedback] actualState: ");
  Serial.print(actualState ? "ON (HIGH)" : "OFF (LOW)");
  Serial.print(" | IP: ");
  Serial.println(WiFi.localIP().toString());
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  // Configure LED Pin
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  Serial.println("\n==================================================");
  Serial.println("  ELDROID: Android + Firebase + ESP32 LED System  ");
  Serial.println("==================================================");

  // Step 1: Run local LED blink test
  runHardwareBlinkTest();

  // Step 2: Connect to Wi-Fi
  connectWiFi();

  // Step 3a: Sync real-world clock via NTP (required for correct heartbeat timestamps)
  if (WiFi.status() == WL_CONNECTED) {
    configTime(0, 0, "pool.ntp.org", "time.nist.gov");
    Serial.print("[NTP] Syncing time");
    struct tm timeinfo;
    int ntpAttempts = 0;
    while (!getLocalTime(&timeinfo) && ntpAttempts < 20) {
      delay(500);
      Serial.print(".");
      ntpAttempts++;
    }
    if (getLocalTime(&timeinfo)) {
      ntpSynced = true;
      Serial.println("\n[NTP] Time synced: " + String(time(nullptr)));
    } else {
      Serial.println("\n[NTP] Sync failed. Heartbeat will use millis() fallback.");
    }
  }

  // Step 3: Configure Firebase Realtime Database
  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  config.timeout.serverResponse = 10 * 1000;

  Firebase.reconnectNetwork(true);

  fbdo.setBSSLBufferSize(4096, 1024);
  fbdo.setResponseSize(2048);
  fbdo.keepAlive(5, 5, 1);

  Firebase.begin(&config, &auth);
  Firebase.setReadTimeout(fbdo, 1000 * 30);
  Firebase.setwriteSizeLimit(fbdo, "tiny");

  Serial.println("[Firebase] Initialized Realtime Database connection.");

  // Send initial offline/online state + initialize command path so it always exists
  if (WiFi.status() == WL_CONNECTED) {
    sendDeviceStatus(false);

    // Initialize the command path with default OFF so Android reads a valid value on first launch
    String commandInitPath = String(DEVICE_PATH) + "/command";
    FirebaseData initFbdo;
    Firebase.setBool(initFbdo, commandInitPath + "/targetState", false);
    Firebase.setInt(initFbdo, commandInitPath + "/lastCommandTimestamp", 0);
    Firebase.setString(initFbdo, commandInitPath + "/requestedBy", "esp32_init");
    Serial.println("[Firebase] Command path initialized with default OFF state.");
  }
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
    return;
  }

  if (Firebase.ready()) {
    unsigned long currentMillis = millis();

    if (currentMillis - lastPollTime >= POLL_INTERVAL_MS) {
      lastPollTime = currentMillis;

      String commandPath = String(DEVICE_PATH) + "/command/targetState";

      if (Firebase.getBool(fbdo, commandPath)) {
        bool targetState = fbdo.boolData();

        
        if (targetState != currentActualState) {
          
          digitalWrite(LED_PIN, targetState ? HIGH : LOW);

          
          currentActualState = (digitalRead(LED_PIN) == HIGH);

          Serial.print("[Action] Received Command: Turn ");
          Serial.print(targetState ? "ON" : "OFF");
          Serial.print(" --> Physical LED Pin set to: ");
          Serial.println(currentActualState ? "HIGH (3.3V)" : "LOW (0V)");

          
          sendDeviceStatus(currentActualState);
        }
      } else {
        // Suppress "path not exist" — it's normal before the Android app sends its first command.
        // Only log genuine errors (not -1 = no error, not "path not exist").
        String reason = fbdo.errorReason();
        if (fbdo.httpCode() != 200 && fbdo.httpCode() != -1 && reason != "path not exist") {
          Serial.println("[Firebase] Read Warning: " + reason);
        }
      }
    }
    if (currentMillis - lastHeartbeatTime >= HEARTBEAT_INTERVAL_MS) {
      lastHeartbeatTime = currentMillis;
      currentActualState = (digitalRead(LED_PIN) == HIGH);
      sendDeviceStatus(currentActualState);
    }

  } else {
    Serial.println("[Firebase] Waiting for connection...");
    delay(1000);
  }
}
