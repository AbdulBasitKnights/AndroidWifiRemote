# Android TV Remote - Kotlin

Smart WiFi remote for Android TV / Google TV using protocol v2.

## Modules

- `remotecontrol` - pairing + command library (Kotlin port of Swift package)
- `app` - demo remote UI

## Requirements

- Android phone/tablet on same WiFi as TV
- Android TV with remote pairing enabled (Settings → Remotes & accessories)
- TV IP address (from TV network settings)

## Usage

1. Open project in Android Studio
2. Build and run on device (emulator won't reach your TV)
3. Enter TV IP, tap **Connect**
4. If pairing needed, enter 6-char hex code shown on TV, tap **Send Code**
5. Use D-pad, volume, Home, Back, Netflix buttons

## Protocol

- Pairing: TLS port **6467**
- Commands: TLS port **6466**
- Self-signed client cert generated on first launch

## Reference

Based on [AndroidTVRemoteControl](https://github.com/odyshewroman/AndroidTVRemoteControl) by Roman Odyshew.
