# NFC Digital Key Activation Request — Email to BYD

## Email Details

| Field | Value |
|-------|-------|
| **To** | db@byd.com |
| **CC** | shouhoufuwu@byd.com |
| **Subject** | NFC Digital Key Activation Request — BYD Dolphin GS (Brazil) |

## Vehicle Information

| Property | Value |
|----------|-------|
| VIN | **[YOUR 17-DIGIT VIN HERE]** |
| Device ID | [YOUR_DEVICE_ID] |
| Model | BYD Dolphin GS 25/26 |
| Country | Brazil (code 55) |
| Car Type ID | 127 |
| SOC Software | 13.1.32.2507250.1 |
| MCU Software | 13.5.2.2312260.1 |
| DSP Software | 13.5.5.2505300.2 |
| Internal Build | 6125f_USER_SIGN_SW264_202507251150_Q2700 |
| Platform | DiLink 3.0, QCM6125, Android 10 |
| IntelligentEntry Version | 1.0.0.250711.1 |
| IMEI | [YOUR_IMEI] |

---

## English Version

Dear BYD Technical Support Team,

I am writing to request the activation of the **NFC Digital Key** feature on my BYD Dolphin GS, purchased in Brazil.

### Vehicle Details

- **VIN**: [YOUR 17-DIGIT VIN HERE]
- **Device ID**: [YOUR_DEVICE_ID]
- **Model**: BYD Dolphin GS 25/26
- **Country**: Brazil
- **SOC Firmware**: 13.1.32.2507250.1
- **MCU Firmware**: 13.5.2.2312260.1
- **IntelligentEntry App**: v1.0.0.250711.1

### Technical Evidence — NFC Hardware is Present and Ready

Through diagnostic analysis, I have confirmed that my vehicle has all the necessary NFC hardware and software components installed, but the feature is **disabled at the firmware level**:

1. **NFC chip is physically present** — NXP chip connected via I2C (`ro.nfc.port = I2C`)
2. **NFC HAL is running** — `vendor.nxp.hardware.nfc@1.2-service` is active
3. **Secure Element is present** — `secure_element` system service is running
4. **IntelligentEntry app supports NFC_abroad** — The app (v1.0.0.250711.1) includes complete NFC key management for overseas markets (Apple Wallet / NFC card key)
5. **MCU reports NFC hardware present** — CAN signal `0x43600028` returns value **3**, confirming the MCU recognizes the NFC hardware

### The Blocker

The IntelligentEntry app only activates NFC when signal `0x43600028` equals **1** (feature enabled). My MCU currently returns **3** (hardware present, feature locked). This is a server-side configuration that BYD can change remotely.

### Request

Please push the NFC Digital Key activation to my vehicle. Based on reports from other Brazilian Dolphin owners (on the dolphinbyd.com.br forum), this activation can be done remotely via a server-side configuration push, and the car restarts briefly to apply the change.

I understand this has been successfully activated for other Brazilian Dolphin owners by your team within 24 hours.

Thank you for your support.

Best regards,
**[YOUR NAME]**
**[YOUR PHONE NUMBER]**
**[YOUR EMAIL]**

---

## 中文版本 (Chinese Version)

尊敬的比亚迪技术支持团队：

您好！我是一名巴西的比亚迪海豚GS车主，写信请求开通我车辆的**NFC数字钥匙**功能。

### 车辆信息

- **车架号（VIN）**：[YOUR 17-DIGIT VIN HERE]
- **设备ID**：[YOUR_DEVICE_ID]
- **车型**：比亚迪海豚GS 25/26
- **所在国家**：巴西
- **SOC固件版本**：13.1.32.2507250.1
- **MCU固件版本**：13.5.2.2312260.1
- **IntelligentEntry应用版本**：1.0.0.250711.1

### 技术证据 — NFC硬件已存在且准备就绪

通过诊断分析，我确认我的车辆已具备所有必要的NFC硬件和软件组件，但该功能在**固件层面被禁用**：

1. **NFC芯片已安装** — NXP芯片通过I2C连接（`ro.nfc.port = I2C`）
2. **NFC HAL服务正在运行** — `vendor.nxp.hardware.nfc@1.2-service` 处于活动状态
3. **安全元件已存在** — `secure_element` 系统服务正在运行
4. **IntelligentEntry应用支持海外NFC** — 该应用（v1.0.0.250711.1）包含完整的海外市场NFC钥匙管理功能（Apple Wallet / NFC卡片钥匙）
5. **MCU确认NFC硬件存在** — CAN信号 `0x43600028` 返回值为**3**，确认MCU识别到NFC硬件

### 问题所在

IntelligentEntry应用仅在信号 `0x43600028` 等于**1**（功能已启用）时才激活NFC功能。我的MCU目前返回**3**（硬件存在，功能锁定）。这是一个可以由比亚迪远程更改的服务器端配置。

### 请求

请为我的车辆推送NFC数字钥匙激活配置。据巴西海豚车主论坛（dolphinbyd.com.br）的其他车主反馈，此激活可通过服务器端配置远程完成，车辆会短暂重启以应用更改。

据了解，贵团队已成功为其他巴西海豚车主在24小时内完成了此项激活。

感谢您的支持！

此致
敬礼

**[您的姓名]**
**[您的电话号码]**
**[您的电子邮件]**

---

## Notes

- Replace `[YOUR 17-DIGIT VIN HERE]` with your actual VIN (found on windshield sticker, driver door jamb, or vehicle registration)
- Replace contact details with your own
- The Chinese version is recommended as the primary email body — BYD China support responds faster to Mandarin
- Forum reference: Brazilian owners on dolphinbyd.com.br report activation within 24 hours after emailing db@byd.com
- The activation is a server-side config push, NOT a full OTA update — the car restarts briefly (~5 seconds) to apply
