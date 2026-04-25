# Surf Shield: Android Java Reference Client

A lightweight, fully functional reference VPN client for Android, written entirely in **Java and XML**. This repository demonstrates how to integrate the [Surf Shield Android SDK](https://surfshield.org/docs/android/) into legacy or Java-first codebases without needing Kotlin or Jetpack Compose.

## Features
- Connect and disconnect using the Surf Shield `LeafVPNService`.
- Simple input flow for dynamically fetching remote `clientId` subscriptions.
- Background asset synchronization (GeoIP/Geosite data).
- Integrity checking and safe `VpnService` permission handling.
- Lightweight, relying on standard Android View binding (`activity_main.xml`).

## Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/shiroedev2024/sample-android-java.git
   ```
2. **Open in Android Studio** and sync the Gradle project.
3. **Run the App:**
   ```bash
   ./gradlew :app:assembleDebug
   ```

## SDK Integration Highlight
This project relies on the `ServiceManagement` singleton provided by the Surfshield SDK:
```java
// Fetch configuration from the Surfshield Panel
ServiceManagement.getInstance().updateSubscription(clientId, callback);

// Start the VPN
ServiceManagement.getInstance().startLeaf();
```

## Resources
* [Android SDK Documentation](https://surfshield.org/docs/android/)
* [REST API Reference](https://surfshield.org/docs/api/)
* [Support & Contact](https://surfshield.org/docs/support/)

## License
Open-sourced under the [Apache 2.0 License](LICENSE).