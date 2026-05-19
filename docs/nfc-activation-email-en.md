# NFC Digital Key Activation Request

> **Template**: Fill in the `[BRACKETS]` below with your details before sending.
> 
> **You will need:**
> - Your full name
> - Phone number (international format, e.g. +55 XX XXXXXXXXX)
> - Email address
> - VIN — found on your vehicle registration or door jamb sticker
> - Device ID — run `adb shell getprop persist.sys.cloud.last_vin` on the head unit
> - License plate number
> - Dealer name and city where you purchased the vehicle
> - CPF (Brazil only)
> - Firmware versions — check Settings > System > About on the head unit

**To**: db@byd.com
**CC**: shouhoufuwu@byd.com
**Subject**: NFC Digital Key Activation Request — BYD Dolphin GS (Brazil)

---

Dear BYD Technical Support Team,

I am writing to request the activation of the **NFC Digital Key** feature on my BYD Dolphin GS, purchased in Brazil.

## Owner Details

- **Name**: [YOUR_NAME]
- **Phone**: [YOUR_PHONE]
- **Email**: [YOUR_EMAIL]

## Vehicle Details

- **VIN**: [YOUR_VIN]
- **Device ID**: [YOUR_DEVICE_ID]
- **Model**: BYD Dolphin GS 25/26
- **License Plate**: [YOUR_LICENSE_PLATE]
- **Country**: Brazil
- **Dealer**: [YOUR_DEALER]
- **Purchase Date**: Q4 2025
- **SOC Firmware**: 13.1.32.2507250.1
- **MCU Firmware**: 13.5.2.2312260.1
- **IntelligentEntry App**: v1.0.0.250711.1

## Technical Evidence — NFC Hardware is Present and Ready

Through diagnostic analysis, I have confirmed that my vehicle has all the necessary NFC hardware and software components installed, but the feature is **disabled at the firmware level**:

1. **NFC chip is physically present** — NXP chip connected via I2C (`ro.nfc.port = I2C`)
2. **NFC HAL is running** — `vendor.nxp.hardware.nfc@1.2-service` is active
3. **Secure Element is present** — `secure_element` system service is running
4. **IntelligentEntry app supports NFC_abroad** — The app (v1.0.0.250711.1) includes complete NFC key management for overseas markets (Apple Wallet / NFC card key)
5. **MCU reports NFC hardware present** — CAN signal `0x43600028` returns value **3**, confirming the MCU recognizes the NFC hardware

## The Blocker

The IntelligentEntry app only activates NFC when signal `0x43600028` equals **1** (feature enabled). My MCU currently returns **3** (hardware present, feature locked). This is a server-side configuration that BYD can change remotely.

## Request

Please push the NFC Digital Key activation to my vehicle. Based on reports from other Brazilian Dolphin owners (on the dolphinbyd.com.br forum), this activation can be done remotely via a server-side configuration push, and the car restarts briefly to apply the change.

I understand this has been successfully activated for other Brazilian Dolphin owners by your team within 24 hours.

Thank you for your support.

Best regards,
**[YOUR_NAME]**
[YOUR_PHONE]
[YOUR_EMAIL]
