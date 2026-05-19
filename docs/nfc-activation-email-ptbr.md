# Solicitacao de Ativacao da Chave Digital NFC

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

**Para**: cliente@byd.com
**Assunto**: Solicitacao de Ativacao da Chave Digital NFC — BYD Dolphin GS

---

Prezada equipe de suporte tecnico da BYD Brasil,

Meu nome e [YOUR_NAME] e sou proprietario de um BYD Dolphin GS, adquirido em [YOUR_CITY] - [YOUR_STATE]. Escrevo para solicitar a ativacao do recurso de **Chave Digital NFC** no meu veiculo.

## Dados do Proprietario

- **Nome**: [YOUR_NAME]
- **CPF**: [YOUR_CPF]
- **Telefone**: [YOUR_PHONE]
- **E-mail**: [YOUR_EMAIL]

## Dados do Veiculo

- **VIN (chassi)**: [YOUR_VIN]
- **ID do dispositivo**: [YOUR_DEVICE_ID]
- **Modelo**: BYD Dolphin GS 25/26
- **Placa**: [YOUR_LICENSE_PLATE]
- **Concessionaria**: [YOUR_DEALER]
- **Data de compra**: 4o trimestre de 2025
- **Firmware SOC**: 13.1.32.2507250.1
- **Firmware MCU**: 13.5.2.2312260.1
- **App IntelligentEntry**: v1.0.0.250711.1

## Evidencias Tecnicas — Hardware NFC Presente e Pronto

Atraves de analise diagnostica, confirmei que meu veiculo possui todos os componentes de hardware e software NFC necessarios instalados, porem o recurso esta **desabilitado no nivel do firmware**:

1. **Chip NFC fisicamente presente** — Chip NXP conectado via I2C (`ro.nfc.port = I2C`)
2. **HAL NFC em execucao** — Servico `vendor.nxp.hardware.nfc@1.2-service` esta ativo
3. **Elemento Seguro presente** — Servico de sistema `secure_element` em execucao
4. **App IntelligentEntry suporta NFC para mercado externo** — O aplicativo (v1.0.0.250711.1) inclui gerenciamento completo de chave NFC para mercados internacionais (Apple Wallet / cartao NFC)
5. **MCU confirma hardware NFC presente** — Sinal CAN `0x43600028` retorna valor **3**, confirmando que a MCU reconhece o hardware NFC

## O Problema

O aplicativo IntelligentEntry so ativa o NFC quando o sinal `0x43600028` e igual a **1** (recurso habilitado). Minha MCU atualmente retorna **3** (hardware presente, recurso bloqueado). Esta e uma configuracao do lado do servidor que a BYD pode alterar remotamente.

## Solicitacao

Solicito que a ativacao da Chave Digital NFC seja enviada para o meu veiculo. De acordo com relatos de outros proprietarios brasileiros do Dolphin (no forum dolphinbyd.com.br), esta ativacao pode ser feita remotamente por meio de um push de configuracao do servidor, e o carro reinicia brevemente para aplicar a alteracao.

Tenho conhecimento de que esta ativacao ja foi realizada com sucesso para outros proprietarios brasileiros do Dolphin pela equipe da BYD China (db@byd.com) em menos de 24 horas. Solicito que a BYD Brasil tambem possa intermediar ou realizar esta ativacao.

Agradeco desde ja pela atencao e suporte.

Atenciosamente,
**[YOUR_NAME]**
CPF: [YOUR_CPF]
[YOUR_PHONE]
[YOUR_EMAIL]
