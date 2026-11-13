# Home Control

Your own Android app for Philips Hue, Tapo and Govee, with schedules like "morning lights".
No Alexa and no vendor cloud: the phone talks to each device directly on your home Wi-Fi,
and all settings stay on the phone.

## What it does
- **Devices tab:** every light/plug grouped by brand, with on/off, brightness, and "All off".
- **Automations tab:** time-based rules (time, days of the week, which devices, on/off, brightness).
  A "Morning lights" template is pre-filled (07:00 weekdays, 80%). "Run now" tests a rule instantly.
- **Setup tab:** connect each brand, and the two Android permissions that make schedules reliable.

## Build and install
Pick one:
1. **Android Studio (Windows/Mac):** File > Open > this folder, wait for sync, then Run with your
   phone plugged in (USB debugging on), or Build > Build APK(s).
2. **GitHub:** push this folder to a GitHub repo. The included workflow builds the APK;
   download it from the repo's Actions tab > latest run > "HomeControl-apk".

To install a downloaded APK, open it on the phone and allow "Install unknown apps" when asked.

## Connect your devices
- **Hue:** Setup > Find bridge > press the round button on the bridge > Pair. Lights import automatically.
- **Tapo:** in the Tapo app turn on *Me > Third-Party Services > Third-Party Compatibility*.
  In Home Control enter your Tapo email/password, Save, then add each device by its IP
  (Tapo app > device > settings > Device info). Tip: give devices a fixed IP in your router.
- **Govee:** in Govee Home open each device > settings > turn on *LAN Control*, then Scan.
  Only Govee models with LAN Control are supported.

## Making schedules reliable
Phone-only means automations run on the phone, so:
- Allow **Alarms & reminders** (Setup shows a button if it's missing).
- Set battery usage for Home Control to **Unrestricted**.
- The phone must be on and on your home Wi-Fi at the scheduled time.

## Code map
- `devices/` – HueClient (local API v1), TapoClient (KLAP v2), GoveeClient (LAN UDP), DeviceController.
- `automation/` – Scheduler (exact alarms), AlarmReceiver (runs a rule, books the next), Runner.
- `data/` – models and on-phone storage.
- `ui/` – Jetpack Compose screens.
