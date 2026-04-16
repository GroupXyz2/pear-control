# Pear Control (Android)

# Disclaimer
## Created with AI because i don't have time to make everything myself, but it is pretty minimalistic and works so there should be no problems. Still i think it is only fair to add this disclaimer.

Android app to control Pear Desktop using the API from the included API Server Plugin.

## What is implemented

- Auth flow using `POST /auth/{id}` to obtain bearer token
- Playback controls:
  - previous, next, play, pause, toggle play/pause
- Seeking controls:
  - seek-to, go-back, go-forward
- Volume + mute:
  - set volume (0-100), toggle mute, volume state fetch
- Modes:
  - shuffle, repeat switch, fullscreen toggle
- Song/State display:
  - current song info, like state, repeat mode, shuffle/fullscreen state
- Periodic auto-refresh every 5 seconds

## Project structure

- `app/src/main/java/com/pearcontrol/app/MainActivity.kt` - Compose UI
- `app/src/main/java/com/pearcontrol/app/MainViewModel.kt` - UI/business state
- `app/src/main/java/com/pearcontrol/app/PearRepository.kt` - API operations and error handling
- `app/src/main/java/com/pearcontrol/app/PearApiService.kt` - Retrofit API models + endpoints

## Build

From workspace root:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK output:

- `app/build/outputs/apk/debug/app-debug.apk`

## How to use

1. Start your Pear Desktop API server.
2. Open this Android app.
3. Enter:
   - Server URL (example: `http://192.168.1.25:3000`)
   - Auth ID (the ID expected by `/auth/{id}`)
4. Tap **Connect**.
5. Use controls to manage playback.

## Notes

- The app allows cleartext HTTP (`usesCleartextTraffic=true`) for LAN control setups.
- If your API uses HTTPS and a valid cert, HTTPS works as well.
