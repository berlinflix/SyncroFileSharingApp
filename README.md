# Syncro

Fast, end-to-end encrypted file sharing between Android phones and PCs. Think Quick Share, but every transfer is encrypted end to end with keys only the two devices hold, and it works with Windows too.

- **Android app** (`app/`): Jetpack Compose, Material 3
- **Syncro for Windows** (`desktop/`): Compose for Desktop, also runs on macOS/Linux
- **Shared core** (`core/`): the protocol, crypto, discovery and transfer engine, written once in Kotlin and used by both apps

## Download

Get the latest build from [Releases](https://github.com/berlinflix/SyncroFileSharingApp/releases):

- **Windows:** `SyncroSetup-<version>.exe`. It installs for your user only, so no admin rights are needed.
- **Android:** `Syncro-<version>.apk`.

Every push is built and tested by GitHub Actions. Pushing a `v*` tag publishes a release with both files.

## Features

- Send any number of files, photos, videos and text or links, with no size limit. Files stream straight to disk and are never held in memory.
- Works on the same Wi-Fi, on a phone hotspot, over Ethernet, and **phone to phone with no shared network** using Google Nearby Connections (Bluetooth discovery, then an upgrade to Wi-Fi Direct or a hotspot).
- Accept or decline every request, with a 4-digit PIN to compare on both screens. Mark a device as trusted to skip the prompt next time.
- QR codes: scan a PC's or phone's code to connect to it directly with its identity pinned.
- Share from any app through the Android share sheet. Drag and drop onto the desktop app.
- Background receiving through a foreground service, with progress notifications and Accept/Decline actions.
- Transfer history, a list of known devices, and light and dark themes.
- Desktop: a frameless window with Syncro's own title bar, tray icon, notifications, a single-instance app, and a headless CLI.
- A branded, single-file Windows installer (`SyncroSetup.exe`) that needs no admin rights and uninstalls cleanly.

## How it works

### Finding devices

Devices announce themselves and answer queries over UDP port **47820**:

1. One socket per local network interface (Wi-Fi, hotspot, Ethernet). Beacons go to the subnet broadcast address, `255.255.255.255` and the multicast group `239.255.77.77`. The old app sent a single broadcast to `255.255.255.255`, which is why it only worked on some hotspots.
2. Scanning devices send queries, and visible devices reply by unicast from the socket the query arrived on, so replies get through NATs and stateful firewalls.
3. When a network filters broadcasts, the scanner probes every address in small subnets and the last-known addresses of devices it has seen before.
4. On Android, sockets are bound to the Wi-Fi network, so traffic still reaches local peers when Wi-Fi has no internet and mobile data is the default route.
5. Phones also advertise and discover through **Nearby Connections**. Peers found both ways are merged into one list, and Wi-Fi is tried first.

### Security

Every connection runs a mutually authenticated key exchange before any data moves (`core/.../secure/Handshake.kt`):

- Each device has a long-term P-256 identity key, and its device ID is derived from that key.
- **Commit, then reveal.** The client commits to its ephemeral key before seeing the server's, so a man-in-the-middle can't grind keys until both PINs match. A 4-digit PIN therefore gives a 1-in-10,000 chance per attempt.
- The ECDH shared secret and the transcript hash feed HKDF-SHA256, which derives a separate key for each direction plus the PIN. Both devices sign the transcript with their identity keys.
- Records are encrypted with **AES-256-GCM**, using a per-direction counter nonce and the record length as associated data. Any tampering, reordering, replay or truncation fails authentication and stops the transfer; there's a test for this.
- Ephemeral keys give forward secrecy.
- A QR code or a trusted device pins the peer's identity, so a spoofed device on the network is rejected.

### Transfer protocol

Offer (names and sizes) → accept or decline → for each file, a start marker, 1 MiB encrypted data records, then an end marker with the byte count → done → acknowledgement.

The receiver enforces the announced sizes, writes to a temporary file and publishes it only after it's complete. Either side can cancel. Watchdogs end stalled connections.

### Performance

The core does 1–2 GB/s of AES-GCM on desktop JVMs (after a 0.4 s JIT warm-up at startup). Android uses BoringSSL's hardware AES. Measured:

| Path | Result |
|---|---|
| PC → PC, local TCP, 300 MB to disk | 1.0 s (≈290 MB/s) |
| Android emulator ↔ PC | ≈1 MB/s, the same as raw TCP from the emulator; its virtual network is the limit |

On real hardware the Wi-Fi link is the bottleneck.

## Project layout

```
core/     Protocol, crypto, LAN transport, engine, stores (pure Kotlin/JVM, unit-tested)
app/      Android app: UI (ui/), Nearby transport (net/), MediaStore storage (data/), service/
desktop/  Windows/macOS/Linux app: UI (ui/), settings, tray, CLI
installer/  SyncroSetup.exe: WPF setup UI (Setup.xaml), install/uninstall logic, build script
```

## Building

Requirements: JDK 17+ (Android Studio's bundled JDK works) and the Android SDK (platform 36).

```bash
./gradlew :core:test                 # protocol, crypto and end-to-end transfer tests
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease       # minified, signed with the debug key; use your own key to publish
./gradlew :desktop:run               # run the desktop app
./gradlew :desktop:packageUberJarForCurrentOS   # single runnable jar
```

Windows installers need a full JDK that includes `jpackage`:

```bash
./gradlew :desktop:packageReleaseMsi :desktop:packageReleaseExe -Psyncro.packagingJdk="C:/path/to/jdk-21"
```

Output goes to `desktop/build/compose/binaries/main-release/{msi,exe}/`.

### Syncro Setup (branded installer)

```bash
./gradlew :desktop:packageSetup -Psyncro.packagingJdk="C:/path/to/jdk-21"
```

This builds `desktop/build/setup/SyncroSetup-2.0.0.exe`, one self-contained file with the whole app inside. It uses the .NET Framework 4.8 C# compiler and WPF that come with Windows 10 and 11, so there's nothing extra to install.

- Installs for the current user into `%LOCALAPPDATA%\Programs\Syncro`, with no admin prompt.
- Options: a desktop shortcut, starting with Windows (in the tray, ready to receive), and opening Syncro when done.
- Detects an existing install and offers to update it, keeping settings and trusted devices.
- Registers in Settings > Apps. Uninstalling can optionally remove settings and device keys too.
- Flags for scripted installs: `/S` (silent), `/D=<folder>`, `/uninstall`, and `/uninstall /S`.

### Desktop CLI

```bash
Syncro receive [--dir DIR] [--name NAME] [--port PORT]
Syncro send <ip[:port] | syncro://…> <file | text:message>...
```

## Credits

The Roboto typeface bundled with the desktop app and installer is licensed under the SIL Open Font License 1.1 (`desktop/src/main/resources/font/LICENSE-Roboto.txt`).

## Network requirements

- TCP **47821** (transfers) and UDP **47820** (discovery). When Windows asks, allow Syncro on private networks.
- Networks with client isolation (many public or guest Wi-Fi networks) block device-to-device traffic. Use a phone hotspot, or for phone to phone, Nearby.
- Android asks for Nearby devices and location permissions. Google Play services requires location for Bluetooth discovery; nothing else uses it.
