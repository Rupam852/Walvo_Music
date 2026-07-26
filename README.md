<div align="center">

# 🎵 Walvo Music

### A modern, sleek, and feature-rich YouTube Music client for Android.

<br/>

[![Official Website](https://img.shields.io/badge/Website-walvo--music.vercel.app-000000?style=for-the-badge&logo=vercel&logoColor=white&labelColor=11131E)](https://walvo-music.vercel.app)
[![Latest Release](https://img.shields.io/github/v/release/Rupam852/Walvo_Music?style=for-the-badge&labelColor=11131E&color=365194)](https://github.com/Rupam852/Walvo_Music/releases)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge&labelColor=11131E&color=365194)](LICENSE)
[![GitHub Repository](https://img.shields.io/badge/GitHub-Rupam852%2FWalvo__Music-blue?style=for-the-badge&logo=github&labelColor=11131E)](https://github.com/Rupam852/Walvo_Music)
[![Android Min SDK](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-green?style=for-the-badge&logo=android&labelColor=11131E)](https://developer.android.com)

<br/>

[🌐 **Website**](https://walvo-music.vercel.app) · [**Download APK**](#-download) · [**Features**](#-features) · [**Build Variants**](#-build-variants) · [**Building**](#-building-from-source) · [**Tech Stack**](#-tech-stack) · [**License**](#-license)

</div>

---

## ✨ Features

<table>
  <tr>
    <td width="50%" valign="top">

#### 🎧 Playback & Downloads
- High-quality audio streaming directly from YouTube Music
- Download & cache songs for seamless offline listening
- Background playback with Android MediaSession controls
- Skip silence, sleep timer, & continuous playback

</td>
    <td width="50%" valign="top">

#### 🎛️ Audio & Customization
- Built-in Equalizer & Audio normalization (Loudness Enhancement)
- Fine-grained tempo & pitch adjustment controls
- Dynamic Material 3 dark theme with Monet color extraction
- Customizable accent colors & theme modes

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### 🎤 Synced Lyrics & Discovery
- Real-time synchronized lyrics integration (LrcLib, BetterLyrics)
- AI-powered lyrics translation support
- Instant Song Recognizer (identify music playing nearby)
- Personalized recommendations, quick picks, & mix generation

</td>
    <td width="50%" valign="top">

#### 👥 Social & Integrations
- **Listen Together**: Sync music live with friends in real-time
- **Last.fm**: Automatic scrobbling & listening statistics tracking
- **Discord**: Live Rich Presence status integration
- **Kugou & ShazamKit**: Enhanced metadata and audio recognition

</td>
  </tr>
</table>

---

## 📦 Build Variants

Walvo Music supports multiple build variants tailored for different user needs:

| Variant | Description | Google Cast | Auto-Updater | Target Audience |
| :--- | :--- | :---: | :---: | :--- |
| **FOSS** | Pure open-source build with in-app updater | ❌ | ✅ | General users / GitHub releases |
| **GMS** | Google Play Services build with Cast support | ✅ | ✅ | Users wanting Chromecast streaming |
| **Izzy** | F-Droid & IzzyOnDroid compliant variant | ❌ | ❌ | F-Droid / IzzyOnDroid repository |

---

## 📥 Download

Get the latest signed release APK directly from our official sources:

- 🌐 **[Official Website](https://walvo-music.vercel.app)**
- 🚀 **[Download Walvo Music Release APK](https://neo-files-transfer.pages.dev/download/826508fec442)**
- 📦 **[GitHub Releases Page](https://github.com/Rupam852/Walvo_Music/releases)**

> [!NOTE]
> All official release builds are signed with a custom **RSA 2048-bit Release Keystore** and support **APK Signature Schemes v1, v2, v3, and v4** for maximum security and integrity across all Android versions.

---

## 🛠️ Building from Source

To build Walvo Music locally from source:

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Rupam852/Walvo_Music.git
   cd Walvo_Music
   ```

2. **Build the desired APK variant**:
   ```bash
   # Build FOSS Release APK
   ./gradlew :app:assembleFossRelease

   # Build GMS Release APK (with Chromecast support)
   ./gradlew :app:assembleGmsRelease

   # Build FOSS Debug APK for testing
   ./gradlew :app:assembleFossDebug
   ```

3. **Output Location**:
   The compiled APK files will be located in:
   `app/build/outputs/apk/<variant>/<buildType>/`

---

## ⚡ Tech Stack & Architecture

- **Language**: [Kotlin](https://kotlinlang.org/) (JVM 21)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3
- **Dependency Injection**: [Hilt](https://dagger.dev/hilt/)
- **Network & API**: [Ktor Client](https://ktor.io/), OkHttp, Protobuf
- **Database & Storage**: [Room DB](https://developer.android.com/training/data-storage/room), Jetpack DataStore
- **Media Playback**: [AndroidX Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3)
- **Asynchronous Processing**: Kotlin Coroutines & Flow

---

## 🤝 Contributing

Contributions, bug reports, and feature suggestions are always welcome!
Feel free to open an [Issue](https://github.com/Rupam852/Walvo_Music/issues) or submit a [Pull Request](https://github.com/Rupam852/Walvo_Music/pulls).

---

## 📄 License

This project is licensed under the **MIT License** - Copyright (c) 2026 Rupam (Walvo Music). See the [LICENSE](LICENSE) file for details.
