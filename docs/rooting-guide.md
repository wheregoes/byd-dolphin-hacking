# Rooting the BYD Dolphin Head Unit via Magisk

## Prerequisites

- USB cable connected to the head unit (WiFi ADB is not enough for fastboot)
- `fastboot` and `adb` installed on your PC
- [Magisk APK](https://github.com/topjohnwu/Magisk/releases) (latest stable)
- The head unit's bootloader is already unlocked (`ro.boot.flash.locked=0`)

## Why This Won't Brick

1. **A/B partition scheme** — the device has two boot slots (`_a` and `_b`). Currently running on `_b`. If one slot fails, the bootloader falls back to the other.
2. **Bootloader is already unlocked** — fastboot always works at the hardware level, even if Android won't boot. You can always reflash.
3. **Magisk only patches the ramdisk** inside the boot image — it doesn't touch the kernel binary or critical partitions.
4. **We flash to the INACTIVE slot first** — if it fails, switch back to the working slot with one command.

## Device Info

| Property | Value |
|----------|-------|
| Current boot slot | `_b` |
| `boot_a` | `/dev/block/sde11` |
| `boot_b` | `/dev/block/sde30` |
| `recovery_a` | `/dev/block/sda7` |
| `recovery_b` | `/dev/block/sda8` |
| `vbmeta_a` | `/dev/block/sde16` |
| `vbmeta_b` | `/dev/block/sde35` |
| Kernel | `4.14.117-perf` aarch64 |
| Android | 10 (API 29) |
| SoC | Qualcomm SM6125 (Trinket) |
| Verified boot | orange (unlocked) |

## Step-by-Step Procedure

### Step 1: Enter fastboot mode

```bash
adb reboot bootloader
```

Wait for the device to reboot into fastboot. Verify with:

```bash
fastboot devices
fastboot getvar all
```

Save the `getvar all` output somewhere — it's your reference if anything goes wrong.

### Step 2: Extract the current boot image (BACKUP)

```bash
# Extract boot from the CURRENT slot (_b)
fastboot flash boot_b boot_b_backup.img   # This is wrong — see below

# The correct way to EXTRACT (pull) a partition via fastboot:
# Option A: If your fastboot supports "fetch" (newer versions):
fastboot fetch boot_b boot_b_original.img

# Option B: Boot into recovery, then use ADB:
fastboot reboot recovery
# In recovery, if ADB root is available:
adb shell dd if=/dev/block/sde30 of=/data/local/tmp/boot.img bs=4096
adb pull /data/local/tmp/boot.img boot_b_original.img

# Option C: Use fastboot boot with a TWRP image to get root shell,
# then dd the boot partition.
```

**IMPORTANT**: Keep `boot_b_original.img` safe. This is your recovery lifeline.

### Step 3: Patch the boot image with Magisk

**Option A — On a phone:**
1. Install the Magisk APK on an Android phone
2. Transfer `boot_b_original.img` to the phone
3. Open Magisk → Install → "Select and Patch a File"
4. Select the boot image
5. Magisk produces `magisk_patched-XXXXX.img` in Downloads
6. Transfer it back to your PC

**Option B — On PC (using Magisk's boot_patch.sh):**
1. Rename `Magisk-vXX.X.apk` to `Magisk.zip` and extract it
2. Find `assets/boot_patch.sh` and the arm64 binaries
3. Run the patch script (requires a Linux/WSL environment)

### Step 4: Flash to the INACTIVE slot first

Flash the patched image to `boot_a` (the slot you're NOT using):

```bash
fastboot flash boot_a magisk_patched-XXXXX.img
```

### Step 5: Disable verified boot on the target slot

Create a disabled vbmeta image or flash with flags:

```bash
fastboot --disable-verity --disable-verification flash vbmeta_a vbmeta_a.img
```

If you don't have `vbmeta_a.img`, you can create an empty one:

```bash
# Extract current vbmeta first (if possible), or use:
fastboot --disable-verity --disable-verification flash vbmeta_a vbmeta.img
```

### Step 6: Switch to the patched slot

```bash
fastboot set_active a
```

### Step 7: Reboot

```bash
fastboot reboot
```

### Step 8: Verify root

Once the device boots:

```bash
adb shell su -c id
# Should show: uid=0(root) gid=0(root)
```

Install the Magisk APK on the head unit for management:

```bash
adb install Magisk-vXX.X.apk
```

## If Something Goes Wrong

### Boot loops or doesn't boot after flashing slot_a

```bash
# Switch back to the known-good slot:
fastboot set_active b
fastboot reboot
```

You're back to the original working system.

### Both slots broken (extremely unlikely)

```bash
# Reflash the original boot image:
fastboot flash boot_b boot_b_original.img
fastboot set_active b
fastboot reboot
```

### Can't enter fastboot

On Qualcomm devices, hold **Volume Down + Power** during boot to enter fastboot/EDL mode. The exact key combination may differ on the head unit — check for physical buttons on the unit.

### Nuclear option: EDL (Emergency Download)

Qualcomm SM6125 supports EDL (Emergency Download) mode via Qualcomm's QFIL tool. This can reflash the entire device even if the bootloader is corrupted. Requires the stock firmware image for the BYD Dolphin head unit.

## After Rooting — What to Do

With root access, the following become possible:

```bash
# Direct SPI access (bypass Java 128-byte limit):
adb shell su -c "cat /dev/spidev_ivi | xxd | head"

# Read kernel symbols (KASLR bypass):
adb shell su -c "cat /proc/kallsyms | head"

# Modify system partition:
adb shell su -c "mount -o remount,rw /system"

# Access ALSA mixer (AVAS audio routing):
adb shell su -c "tinymix"

# Read dmesg (kernel debug):
adb shell su -c dmesg

# Restore AVAH test tone (direct SPI reset):
# Use BydSpiDirect.java or write a native tool that runs as root
```

## Magisk vs KernelSU Comparison

| Factor | Magisk | KernelSU |
|--------|--------|----------|
| **Kernel requirement** | Any kernel | GKI 5.10+ (or manual kernel build) |
| **Our kernel (4.14.117)** | **Compatible** | **NOT compatible** — non-GKI, too old |
| **Installation** | Patch boot.img, flash via fastboot | Patch kernel image or build LKM module |
| **BYD known issues** | DiLink 5.0 has issues (ours is 3.x/4.0) | No BYD data at all |
| **Community support** | Massive, mature (since 2016) | Newer, less BYD-specific knowledge |
| **Reversibility** | Flash original boot.img → fully stock | Same, but requires custom kernel revert |
| **Detection by car systems** | Possible but manageable with MagiskHide | Built-in hiding, but untested on BYD |

**Verdict**: Magisk is the only viable option. KernelSU requires GKI kernel 5.10+, and
our car runs kernel 4.14.117-perf (non-GKI, pre-GKI era). Using KernelSU would require
BYD's kernel source code (not published) and building a custom kernel — extremely risky.

**BYD-specific Magisk notes**:
- GitHub issues #6717 and #9470 report Magisk problems on **DiLink 5.0** — our car
  appears to be DiLink 3.x/4.0, which predates these problems
- Recommended version: **Magisk v25210** (stable, tested on BYD) rather than latest v30.4
- Car uses A/B partition slots — Magisk handles this natively

## Stock Firmware Availability

**No matching stock firmware found.** Available BYD Dolphin firmware on GitHub
(BYDcar repos) is **Di3.0_13.1.22.xxx** for the **msm8953** chipset. Our car runs
**QCM6125** with firmware **13.5.x** — completely different platform. Flashing
msm8953 firmware would brick the car.

| Source | Firmware | Chipset | Match? |
|--------|----------|---------|--------|
| GitHub BYDcar repos | Di3.0_13.1.22.xxx | msm8953 | **NO** |
| Our car (0x99000001) | 13.5.2.2312260.1 | QCM6125 (SM6125) | — |
| Our car DSP (0x99000002) | 13.5.5.2505300.2 | — | — |

**Implication**: Boot image MUST be extracted from the device before patching. There
is no downloadable stock firmware to fall back to if the backup is lost.

On-device OTA packages (`com.byd.cota`, `com.byd.otaupdate`, `com.byd.otgupdate`)
may have cached update packages, but their private storage is not accessible without root.

## Root Status

**Not pursued.** The bootloader is unlocked and Magisk root is technically viable,
but the user chose not to root the vehicle. All non-root approaches to custom AVAS
audio have been exhausted (see `sound-and-themes.md` for full test results).

## Notes

- The cross-compiler toolchain is at `/tmp/aarch64-toolchain/extracted/usr/bin/`
- Native ARM64 binaries can be compiled with:
  ```bash
  export PATH="/tmp/aarch64-toolchain/extracted/usr/bin:$PATH"
  export LD_LIBRARY_PATH="/tmp/aarch64-toolchain/extracted/usr/lib/x86_64-linux-gnu:$LD_LIBRARY_PATH"
  aarch64-linux-gnu-gcc-13 --sysroot=/tmp/aarch64-toolchain/extracted/usr/aarch64-linux-gnu \
      -static -O2 -o binary source.c
  adb push binary /data/local/tmp/
  ```
- Probe binaries already on device: `/data/local/tmp/binder_probe`, `kgsl_probe`, `kgsl_deep`, `privesc_probe`
