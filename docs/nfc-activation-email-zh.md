# NFC数字钥匙激活请求

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

**收件人**: db@byd.com
**抄送**: shouhoufuwu@byd.com
**主题**: NFC Digital Key Activation Request — BYD Dolphin GS (Brazil) / NFC数字钥匙激活请求

---

尊敬的比亚迪技术支持团队：

您好！我是一名巴西的比亚迪海豚GS车主，写信请求开通我车辆的**NFC数字钥匙**功能。

## 车主信息

- **姓名**：[YOUR_NAME]
- **电话**：[YOUR_PHONE]
- **邮箱**：[YOUR_EMAIL]

## 车辆信息

- **车架号（VIN）**：[YOUR_VIN]
- **设备ID**：[YOUR_DEVICE_ID]
- **车型**：比亚迪海豚GS 25/26
- **车牌**：[YOUR_LICENSE_PLATE]
- **所在国家**：巴西
- **购车经销商**：[YOUR_DEALER]
- **购车日期**：2025年第四季度
- **SOC固件版本**：13.1.32.2507250.1
- **MCU固件版本**：13.5.2.2312260.1
- **IntelligentEntry应用版本**：1.0.0.250711.1

## 技术证据 — NFC硬件已存在且准备就绪

通过诊断分析，我确认我的车辆已具备所有必要的NFC硬件和软件组件，但该功能在**固件层面被禁用**：

1. **NFC芯片已安装** — NXP芯片通过I2C连接（`ro.nfc.port = I2C`）
2. **NFC HAL服务正在运行** — `vendor.nxp.hardware.nfc@1.2-service` 处于活动状态
3. **安全元件已存在** — `secure_element` 系统服务正在运行
4. **IntelligentEntry应用支持海外NFC** — 该应用（v1.0.0.250711.1）包含完整的海外市场NFC钥匙管理功能（Apple Wallet / NFC卡片钥匙）
5. **MCU确认NFC硬件存在** — CAN信号 `0x43600028` 返回值为**3**，确认MCU识别到NFC硬件

## 问题所在

IntelligentEntry应用仅在信号 `0x43600028` 等于**1**（功能已启用）时才激活NFC功能。我的MCU目前返回**3**（硬件存在，功能锁定）。这是一个可以由比亚迪远程更改的服务器端配置。

## 请求

请为我的车辆推送NFC数字钥匙激活配置。据巴西海豚车主论坛（dolphinbyd.com.br）的其他车主反馈，此激活可通过服务器端配置远程完成，车辆会短暂重启以应用更改。

据了解，贵团队已成功为其他巴西海豚车主在24小时内完成了此项激活。

感谢您的支持！

此致
敬礼

**[YOUR_NAME]**
[YOUR_PHONE]
[YOUR_EMAIL]
