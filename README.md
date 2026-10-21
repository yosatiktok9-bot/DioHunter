# DioHunter

Local **endpoint hunter** for Android. Pick any installed app → start capture → open that app → see HTTP endpoints (and HTTPS hosts via TLS SNI).

Built for reverse-engineering Flutter / Dio / OkHttp traffic without a PC proxy.

## Flow

1. Install **DioHunter**
2. Open it → choose **target app** from the spinner
3. Tap **Start hunt** → accept **VPN** permission
4. Switch to the target app and use the screens you care about
5. Return to DioHunter → list of endpoints
6. Tap a row → headers + body preview
7. **Export JSON** for offline analysis

## Capture

| Traffic | Result |
|---------|--------|
| HTTP cleartext | Full request/response preview |
| HTTPS | Host via TLS SNI (method `TLS`) |

## Build

```bash
git clone https://github.com/yosatiktok9-bot/DioHunter.git
cd DioHunter
# Android Studio Open, or:
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions builds debug APK on every push (Actions → artifact `diohunter-debug`).

## Package

`com.paneer.diohunter`
