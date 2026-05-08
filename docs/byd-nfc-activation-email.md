# NFC Digital Key Activation Request — Email to BYD

## Email Details

### BYD China (primary — server-side activation)

| Field | Value |
|-------|-------|
| **To** | db@byd.com |
| **CC** | shouhoufuwu@byd.com |
| **Subject** | NFC Digital Key Activation Request — BYD Dolphin GS (Brazil) / NFC数字钥匙激活请求 |
| **Language** | Chinese (中文版本) + English version below |

### BYD Brasil (local support)

| Field | Value |
|-------|-------|
| **To** | sac@byd.com.br |
| **Subject** | Solicitação de Ativação da Chave Digital NFC — BYD Dolphin GS |
| **Language** | Português (versão PT-BR abaixo) |

## Owner & Vehicle Information

| Property | Value |
|----------|-------|
| Owner | [YOUR_NAME] |
| CPF | [YOUR_CPF] |
| Phone | [YOUR_PHONE] |
| Email | [YOUR_EMAIL] |
| VIN | [YOUR_VIN] |
| Device ID | [YOUR_DEVICE_ID] |
| Model | BYD Dolphin GS 25/26 |
| License Plate | [YOUR_LICENSE_PLATE] |
| Country | Brazil (code 55) |
| Dealer | [YOUR_DEALER] |
| Purchase Date | Q4 2025 |
| Car Type ID | 127 |
| SOC Software | 13.1.32.2507250.1 |
| MCU Software | 13.5.2.2312260.1 |
| DSP Software | 13.5.5.2505300.2 |
| Internal Build | 6125f_USER_SIGN_SW264_202507251150_Q2700 |
| Platform | DiLink 3.0, QCM6125, Android 10 |
| IntelligentEntry Version | 1.0.0.250711.1 |
| IMEI | [YOUR_IMEI] |

---

## 中文版本 (Chinese Version — send to db@byd.com)

尊敬的比亚迪技术支持团队：

您好！我是一名巴西的比亚迪海豚GS车主，写信请求开通我车辆的**NFC数字钥匙**功能。

### 车主信息

- **姓名**：[YOUR_NAME]
- **电话**：[YOUR_PHONE]
- **邮箱**：[YOUR_EMAIL]

### 车辆信息

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

**[YOUR_NAME]**
[YOUR_PHONE]
[YOUR_EMAIL]

---

## English Version (send to db@byd.com alongside Chinese version)

Dear BYD Technical Support Team,

I am writing to request the activation of the **NFC Digital Key** feature on my BYD Dolphin GS, purchased in Brazil.

### Owner Details

- **Name**: [YOUR_NAME]
- **Phone**: [YOUR_PHONE]
- **Email**: [YOUR_EMAIL]

### Vehicle Details

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
**[YOUR_NAME]**
[YOUR_PHONE]
[YOUR_EMAIL]

---

## Versao em Portugues (enviar para sac@byd.com.br)

Prezada equipe de suporte tecnico da BYD Brasil,

Meu nome e [YOUR_NAME] e sou proprietario de um BYD Dolphin GS, adquirido em [YOUR_CITY] - [YOUR_STATE]. Escrevo para solicitar a ativacao do recurso de **Chave Digital NFC** no meu veiculo.

### Dados do Proprietario

- **Nome**: [YOUR_NAME]
- **CPF**: [YOUR_CPF]
- **Telefone**: [YOUR_PHONE]
- **E-mail**: [YOUR_EMAIL]

### Dados do Veiculo

- **VIN (chassi)**: [YOUR_VIN]
- **ID do dispositivo**: [YOUR_DEVICE_ID]
- **Modelo**: BYD Dolphin GS 25/26
- **Placa**: [YOUR_LICENSE_PLATE]
- **Concessionaria**: [YOUR_DEALER]
- **Data de compra**: 4o trimestre de 2025
- **Firmware SOC**: 13.1.32.2507250.1
- **Firmware MCU**: 13.5.2.2312260.1
- **App IntelligentEntry**: v1.0.0.250711.1

### Evidencias Tecnicas — Hardware NFC Presente e Pronto

Atraves de analise diagnostica, confirmei que meu veiculo possui todos os componentes de hardware e software NFC necessarios instalados, porem o recurso esta **desabilitado no nivel do firmware**:

1. **Chip NFC fisicamente presente** — Chip NXP conectado via I2C (`ro.nfc.port = I2C`)
2. **HAL NFC em execucao** — Servico `vendor.nxp.hardware.nfc@1.2-service` esta ativo
3. **Elemento Seguro presente** — Servico de sistema `secure_element` em execucao
4. **App IntelligentEntry suporta NFC para mercado externo** — O aplicativo (v1.0.0.250711.1) inclui gerenciamento completo de chave NFC para mercados internacionais (Apple Wallet / cartao NFC)
5. **MCU confirma hardware NFC presente** — Sinal CAN `0x43600028` retorna valor **3**, confirmando que a MCU reconhece o hardware NFC

### O Problema

O aplicativo IntelligentEntry so ativa o NFC quando o sinal `0x43600028` e igual a **1** (recurso habilitado). Minha MCU atualmente retorna **3** (hardware presente, recurso bloqueado). Esta e uma configuracao do lado do servidor que a BYD pode alterar remotamente.

### Solicitacao

Solicito que a ativacao da Chave Digital NFC seja enviada para o meu veiculo. De acordo com relatos de outros proprietarios brasileiros do Dolphin (no forum dolphinbyd.com.br), esta ativacao pode ser feita remotamente por meio de um push de configuracao do servidor, e o carro reinicia brevemente para aplicar a alteracao.

Tenho conhecimento de que esta ativacao ja foi realizada com sucesso para outros proprietarios brasileiros do Dolphin pela equipe da BYD China (db@byd.com) em menos de 24 horas. Solicito que a BYD Brasil tambem possa intermediar ou realizar esta ativacao.

Agradeco desde ja pela atencao e suporte.

Atenciosamente,
**[YOUR_NAME]**
CPF: [YOUR_CPF]
[YOUR_PHONE]
[YOUR_EMAIL]

---

## Notes

- Send the **Chinese version** to db@byd.com (CC shouhoufuwu@byd.com) — this is the proven path from forum reports
- Send the **Portuguese version** to sac@byd.com.br for BYD Brasil local support
- The activation is a server-side config push, NOT a full OTA update — the car restarts briefly (~5 seconds) to apply
- Forum reference: Brazilian owners on dolphinbyd.com.br report activation within 24 hours after emailing db@byd.com
