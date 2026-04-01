# WW7ATS - Amateur Television

WW7ATS is an open-source companion app for the [WW7ATS Amateur Television (ATV)](https://wwats.net) repeater system. Built for licensed ham radio operators, it lets you stream live video to the ATV repeater and watch incoming ATV feeds from your Android device.

## Features

- **RTMP Streaming** — Broadcast your camera feed to the WW7ATS repeater at 480p, 720p, or 1080p
- **HLS Playback** — Watch the repeater output with low-latency HLS
- **Slideshow + Camera PiP** — Cycle through photos with your camera composited as picture-in-picture (custom OpenGL shader)
- **Push-to-Talk (PTT)** — On-screen PTT button for transmitter control
- **Morse Code CW ID** — Automatic station identification compliant with FCC Part 97
- **Secure Settings** — Stream key stored in Android EncryptedSharedPreferences (AES-256-GCM)

## Screenshots

*(Coming soon)*

## Building

### Prerequisites

- Android Studio Ladybug (2024.2) or later
- JDK 17
- Android SDK 35

### Build

```bash
./gradlew assembleDebug
```

For a release build with your own signing key:

1. Create `keystore.properties` in the project root:
   ```properties
   storeFile=path/to/your.keystore
   storePassword=your_store_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```
2. Run:
   ```bash
   ./gradlew assembleRelease
   ```

## Privacy

WW7ATS does **not** collect, store, or transmit any personal data. No analytics, tracking, or advertising. Camera and microphone data is streamed only to the RTMP server you configure. See the full [Privacy Policy](https://cpicoto.github.io/ad7np/privacy-policy.html).

## License

```
Copyright 2026 Carlos Picoto (AD7NP)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

- [RootEncoder](https://github.com/pedroSG94/RootEncoder) — RTMP streaming engine (Apache 2.0)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Modern Android UI toolkit
- [ExoPlayer / Media3](https://developer.android.com/media/media3) — HLS playback

73 de AD7NP
