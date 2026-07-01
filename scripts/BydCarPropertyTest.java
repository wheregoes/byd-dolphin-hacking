/**
 * Tests the DiCar / ICarPropertyService API path — a DIFFERENT API from BYDAutoManager.
 * CarSetting UI uses this path to toggle the external speaker.
 * The ContentProvider is exported (android:exported="true"), so app_process can access it.
 *
 * Key difference from BydAudioQuery:
 *   BydAudioQuery  → BYDAutoManager.setInt(devType, featureId, value) → libbydauto.so → SPI → MCU
 *   BydCarPropertyTest → ICarPropertyService.setProperty(hexId, value) → DiCarServer → MCU
 *
 * Uses pure reflection (no android imports) — compiles without android.jar.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/bydcpp scripts/BydCarPropertyTest.java
 *   d8 --output /tmp/bydcpp /tmp/bydcpp/BydCarPropertyTest.class
 *   adb push /tmp/bydcpp/classes.dex /data/local/tmp/bydcpp.dex
 *
 *   adb shell "CLASSPATH=/data/local/tmp/bydcpp.dex app_process / BydCarPropertyTest"
 *   adb shell "CLASSPATH=/data/local/tmp/bydcpp.dex app_process / BydCarPropertyTest read 0x35201036"
 *   adb shell "CLASSPATH=/data/local/tmp/bydcpp.dex app_process / BydCarPropertyTest set 0x1C10000E 1"
 */
public class BydCarPropertyTest {
    static final String DESCRIPTOR = "com.byd.car.property.ICarPropertyService";
    static final String CONTENT_URI = "content://com.byd.car.server.provider.CarServiceProvider/sync_binder";

    static final int TRANSACTION_setProperties = 1;
    static final int TRANSACTION_getProperty = 2;

    static Object ctx; // Context
    static Object remote; // IBinder

    // Cached reflection
    static Class<?> parcelClass;
    static Class<?> bundleClass;
    static Class<?> binderClass;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            ctx = at.getMethod("getSystemContext").invoke(t);

            parcelClass = Class.forName("android.os.Parcel");
            bundleClass = Class.forName("android.os.Bundle");
            binderClass = Class.forName("android.os.IBinder");

            System.out.println("=== BYD CarPropertyService (DiCar) Probe ===\n");

            remote = getRemoteBinder();
            if (remote == null) {
                System.out.println("FAILED: Could not get ICarPropertyService binder via ContentProvider");
                System.out.println("Trying ServiceManager...");
                remote = tryServiceManager();
                if (remote == null) {
                    System.out.println("FAILED: ServiceManager path also failed");
                    return;
                }
            }
            System.out.println("Got ICarPropertyService binder: " + remote);
            System.out.println("Interface descriptor: " + getInterfaceDescriptor(remote));
            System.out.println();

            if (args.length == 0) {
                runFullProbe();
            } else if (args[0].equals("read") && args.length >= 2) {
                String result = getProperty(args[1]);
                System.out.println("getProperty(" + args[1] + ") = " + result);
            } else if (args[0].equals("set") && args.length >= 3) {
                int val = Integer.parseInt(args[2]);
                int code = setProperty(args[1], val);
                System.out.println("setProperty(" + args[1] + ", " + val + ") → Status code = " + code +
                    formatStatus(code));
            } else {
                System.out.println("Usage: BydCarPropertyTest [read <hexId> | set <hexId> <value>]");
                System.out.println("  (no args = full probe)");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static void runFullProbe() throws Exception {
        System.out.println("--- External Speaker Config & Switch ---");
        String[][] extSpeakerSignals = {
            {"0x35201036", "AUDIO_EXTERIOR_SPEAKER_CONFIG (visibility: 2=visible)"},
            {"0x35201040", "AUDIO_EXTERIOR_SPEAKER_SWITCH_STATUS (1=on, 2=off)"},
            {"0x3520103F", "AUDIO_EXTERIOR_PROMPT_TONE_SOURCE_STATUS"},
            {"0x35201042", "AUDIO_AVAS_FAULT_STATUS"},
            {"0x4C60002D", "AVAS_SOUND_SOURCE_STATE"},
            {"0x99000162", "AVAS_SOURCE_TYPE"},
        };
        for (String[] sig : extSpeakerSignals) {
            String result = getProperty(sig[0]);
            System.out.println("  " + sig[0] + " [" + sig[1] + "]");
            System.out.println("    → " + result);
        }

        System.out.println("\n--- Try SET External Speaker Switch ON (via DiCar path) ---");
        System.out.println("  (previously FAILED via BYDAutoManager — testing via ICarPropertyService)");
        int code = setProperty("0x1C10000E", 1);
        System.out.println("  setProperty(0x1C10000E, 1) [ON] → " + code + formatStatus(code));
        Thread.sleep(500);
        String status = getProperty("0x35201040");
        System.out.println("  Readback STATUS = " + status);
        code = setProperty("0x1C10000E", 2);
        System.out.println("  setProperty(0x1C10000E, 2) [OFF] → " + code + formatStatus(code));

        System.out.println("\n--- UE Broadcast Signals (UNTESTED via DiCar) ---");
        String[][] ueSignals = {
            {"0x35202028", "UE_BROADCAST_SOUND_SOURCE"},
            {"0x35202010", "UE_BROADCAST_VOLUME"},
            {"0x35201046", "UE_BROADCAST_TRIGGERS_LOW_PRESSURE"},
        };
        for (String[] sig : ueSignals) {
            String result = getProperty(sig[0]);
            System.out.println("  " + sig[0] + " [" + sig[1] + "] → " + result);
        }

        System.out.println("\n--- UE Broadcast SET tests ---");
        code = setProperty("0x32B1C028", 1);
        System.out.println("  UE_BROADCAST_SOUND_SOURCE_SET(0x32B1C028, 1) → " + code + formatStatus(code));
        code = setProperty("0x1A900040", 10);
        System.out.println("  UE_BROADCAST_VOLUME_SET(0x1A900040, 10) → " + code + formatStatus(code));
        code = setProperty("0xAA000346", 0);
        System.out.println("  NON_BRANDED_AMP_UE_MUTE_SET(0xAA000346, 0=unmute) → " + code + formatStatus(code));
        code = setProperty("0xAA000332", 10);
        System.out.println("  NON_BRANDED_AMP_UE_VOLUME_SET(0xAA000332, 10) → " + code + formatStatus(code));

        System.out.println("\n--- Prompt Volume Level ---");
        String pvl = getProperty("0x99000307");
        System.out.println("  PROMPT_VOLUME_LEVEL_STATUS(0x99000307) → " + pvl);
        code = setProperty("0xAA000299", 3);
        System.out.println("  PROMPT_VOLUME_LEVEL_SET(0xAA000299, 3=high) → " + code + formatStatus(code));
        Thread.sleep(300);
        pvl = getProperty("0x99000307");
        System.out.println("  Readback → " + pvl);

        System.out.println("\n--- HW Sounding Direction ---");
        String hwl1 = getProperty("0x35202020");
        System.out.println("  HW_L1_SOUNDING_DIRECTION_STATUS(0x35202020) → " + hwl1);
        code = setProperty("0x32B1C020", 1);
        System.out.println("  HW_L1_SOUNDING_DIRECTION_SET(0x32B1C020, 1) → " + code + formatStatus(code));

        System.out.println("\n--- Key Sound Source ---");
        String kss = getProperty("0x35201010");
        System.out.println("  KEY_SOUND_SOURCE_STATUS(0x35201010) → " + kss);
        code = setProperty("0x32B1C010", 1);
        System.out.println("  KEY_SOUND_SOURCE_SET(0x32B1C010, 1) → " + code + formatStatus(code));

        System.out.println("\n--- DSP / A2B Fault Status (NEW) ---");
        String[][] faultSignals = {
            {"0x99000247", "FAULT_TYPE_A2B_STATUS"},
            {"0x99000248", "FAULT_TYPE_DSP_STATUS"},
            {"0x99000249", "FAULT_TYPE_PAD_INIT_STATUS"},
            {"0x99000246", "FAULT_TYPE_PA_STATUS"},
        };
        for (String[] sig : faultSignals) {
            String result = getProperty(sig[0]);
            System.out.println("  " + sig[0] + " [" + sig[1] + "] → " + result);
        }

        System.out.println("\n--- DSP Info ---");
        String[][] dspSignals = {
            {"0x99000215", "DSP_TYPE"},
            {"0x99000214", "AMPLIFIER_TYPE"},
            {"0x4FD00030", "AMPLIFIER_CONFIG"},
            {"0x99000223", "OTA_DSP_SOUND_SOURCE_PACKAGE"},
            {"0x99000364", "AUDIO_DSP_READY"},
            {"0x99000266", "SUPPORT_VARIABLE_SOUND_SOURCE"},
        };
        for (String[] sig : dspSignals) {
            String result = getProperty(sig[0]);
            System.out.println("  " + sig[0] + " [" + sig[1] + "] → " + result);
        }

        System.out.println("\n=== Done ===");
        System.out.println("Key question: did setProperty(0x1C10000E) SUCCEED via DiCar path?");
        System.out.println("If yes — the external speaker toggle works via ICarPropertyService!");
        System.out.println("Next: enable external speaker, then try I2S audio routing to AVAS.");
    }

    // --- ICarPropertyService access via ContentProvider ---

    static Object getRemoteBinder() throws Exception {
        // Get ContentResolver from Context
        Object resolver = ctx.getClass().getMethod("getContentResolver").invoke(ctx);

        // Parse URI
        Class<?> uriClass = Class.forName("android.net.Uri");
        Object uri = uriClass.getMethod("parse", String.class).invoke(null, CONTENT_URI);

        // Call ContentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        Object cursor = resolver.getClass().getMethod("query",
            uriClass, String[].class, String.class, String[].class, String.class)
            .invoke(resolver, uri, null, null,
                new String[]{"com.byd.car.property.ICarPropertyService"}, null);

        if (cursor == null) {
            System.out.println("  ContentProvider query returned null cursor");
            return null;
        }
        try {
            // cursor.moveToFirst()
            cursor.getClass().getMethod("moveToFirst").invoke(cursor);
            // cursor.getExtras() → Bundle
            Object extras = cursor.getClass().getMethod("getExtras").invoke(cursor);
            if (extras == null) {
                System.out.println("  Cursor extras = null");
                return null;
            }
            // extras.getParcelable("binder") → BinderParcelable
            Object binderParcel = bundleClass.getMethod("getParcelable", String.class).invoke(extras, "binder");
            if (binderParcel == null) {
                System.out.println("  Binder parcel = null (service not registered in DiCarServer?)");
                return null;
            }
            // Call getBinder() on BinderParcelable
            try {
                return binderParcel.getClass().getMethod("getBinder").invoke(binderParcel);
            } catch (NoSuchMethodException e) {
                // Fallback: read field directly
                java.lang.reflect.Field f = binderParcel.getClass().getDeclaredField("mBinder");
                f.setAccessible(true);
                return f.get(binderParcel);
            }
        } finally {
            cursor.getClass().getMethod("close").invoke(cursor);
        }
    }

    static Object tryServiceManager() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        String[] names = {"car_property_service", "CarPropertyService", "dicar_property",
                          "CarService", "car_service"};
        for (String name : names) {
            try {
                Object binder = sm.getMethod("getService", String.class).invoke(null, name);
                if (binder != null) {
                    System.out.println("  Found via ServiceManager: " + name);
                    return binder;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    static String getInterfaceDescriptor(Object binder) {
        try {
            return (String) binder.getClass().getMethod("getInterfaceDescriptor").invoke(binder);
        } catch (Exception e) {
            return "unknown (" + e.getMessage() + ")";
        }
    }

    /**
     * ICarPropertyService.getProperty(String) → Response
     * Transaction code 2.
     */
    static String getProperty(String propertyKey) {
        Object data = null;
        Object reply = null;
        try {
            data = parcelClass.getMethod("obtain").invoke(null);
            reply = parcelClass.getMethod("obtain").invoke(null);

            // data.writeInterfaceToken(DESCRIPTOR)
            parcelClass.getMethod("writeInterfaceToken", String.class).invoke(data, DESCRIPTOR);
            // data.writeString(propertyKey)
            parcelClass.getMethod("writeString", String.class).invoke(data, propertyKey);

            // remote.transact(2, data, reply, 0)
            Boolean res = (Boolean) binderClass.getMethod("transact", int.class,
                parcelClass, parcelClass, int.class).invoke(remote, TRANSACTION_getProperty, data, reply, 0);
            if (!res) return "TRANSACT_FAILED";

            // reply.readException() — reads int(0) if no exception
            parcelClass.getMethod("readException").invoke(reply);
            int hasResult = (int) parcelClass.getMethod("readInt").invoke(reply);
            if (hasResult == 0) return "null (no result)";

            // Read Response: writeParcelable(status), writeParcelable(result)
            // readParcelable reads: String className, then creates from parcel
            // But we parse manually since we don't have the classes
            // Status: writeString(className), writeInt(code), writeString(description)
            // Actually writeParcelable writes: writeString(Parcelable.class.getName()), then writeToParcel
            String statusClass = (String) parcelClass.getMethod("readString").invoke(reply);
            int statusCode = (int) parcelClass.getMethod("readInt").invoke(reply);
            String statusDesc = (String) parcelClass.getMethod("readString").invoke(reply);

            // Result CarPropertyValue: writeString(className), then CarPropertyValue.writeToParcel
            String resultClass = (String) parcelClass.getMethod("readString").invoke(reply);
            if (resultClass == null) {
                return "status=" + statusCode + " result=null";
            }
            // CarPropertyValue fields: writeString(key), writeString(id), writeString(valueType), value
            String key = (String) parcelClass.getMethod("readString").invoke(reply);
            String id = (String) parcelClass.getMethod("readString").invoke(reply);
            String valueType = (String) parcelClass.getMethod("readString").invoke(reply);
            String value = readValue(reply, valueType);
            return "key=" + key + " value=" + value + " (status=" + statusCode + formatStatus(statusCode) + ")";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        } finally {
            if (data != null) try { parcelClass.getMethod("recycle").invoke(data); } catch (Exception ignored) {}
            if (reply != null) try { parcelClass.getMethod("recycle").invoke(reply); } catch (Exception ignored) {}
        }
    }

    /**
     * ICarPropertyService.setProperties(CarPropertyValue[]) → Status
     * Transaction code 1.
     */
    static int setProperty(String propertyKey, int value) {
        Object data = null;
        Object reply = null;
        try {
            data = parcelClass.getMethod("obtain").invoke(null);
            reply = parcelClass.getMethod("obtain").invoke(null);

            // data.writeInterfaceToken(DESCRIPTOR)
            parcelClass.getMethod("writeInterfaceToken", String.class).invoke(data, DESCRIPTOR);

            // writeTypedArray with 1 CarPropertyValue element
            // writeInt(1) — array length
            parcelClass.getMethod("writeInt", int.class).invoke(data, 1);
            // writeInt(1) — presence marker (1 = not null)
            parcelClass.getMethod("writeInt", int.class).invoke(data, 1);
            // CarPropertyValue.writeToParcel for Integer:
            // writeString(propertyKey), writeString(null), writeString("java.lang.Integer"), writeInt(value)
            parcelClass.getMethod("writeString", String.class).invoke(data, propertyKey);
            parcelClass.getMethod("writeString", String.class).invoke(data, (String) null);
            parcelClass.getMethod("writeString", String.class).invoke(data, "java.lang.Integer");
            parcelClass.getMethod("writeInt", int.class).invoke(data, value);

            // remote.transact(1, data, reply, 0)
            Boolean res = (Boolean) binderClass.getMethod("transact", int.class,
                parcelClass, parcelClass, int.class).invoke(remote, TRANSACTION_setProperties, data, reply, 0);
            if (!res) return -99999;

            // reply.readException()
            parcelClass.getMethod("readException").invoke(reply);
            int hasStatus = (int) parcelClass.getMethod("readInt").invoke(reply);
            if (hasStatus == 0) return -1;
            int code = (int) parcelClass.getMethod("readInt").invoke(reply);
            return code;
        } catch (Exception e) {
            System.out.println("  setProperty error: " + e);
            return -88888;
        } finally {
            if (data != null) try { parcelClass.getMethod("recycle").invoke(data); } catch (Exception ignored) {}
            if (reply != null) try { parcelClass.getMethod("recycle").invoke(reply); } catch (Exception ignored) {}
        }
    }

    static String readValue(Object parcel, String type) {
        try {
            if (type == null) return "null";
            if (type.equals("java.lang.String"))
                return (String) parcelClass.getMethod("readString").invoke(parcel);
            if (type.equals("java.lang.Integer"))
                return String.valueOf(parcelClass.getMethod("readInt").invoke(parcel));
            if (type.equals("java.lang.Boolean"))
                return String.valueOf((int) parcelClass.getMethod("readInt").invoke(parcel) != 0);
            if (type.equals("java.lang.Long"))
                return String.valueOf(parcelClass.getMethod("readLong").invoke(parcel));
            if (type.equals("java.lang.Float"))
                return String.valueOf(parcelClass.getMethod("readFloat").invoke(parcel));
            if (type.equals("java.lang.Double"))
                return String.valueOf(parcelClass.getMethod("readDouble").invoke(parcel));
            if (type.equals("[B")) {
                byte[] b = (byte[]) parcelClass.getMethod("createByteArray").invoke(parcel);
                return "bytes[" + (b != null ? b.length : 0) + "]";
            }
            return "unknown(" + type + ")";
        } catch (Exception e) {
            return "readError(" + type + ": " + e.getMessage() + ")";
        }
    }

    static String formatStatus(int code) {
        if (code == 0) return " (SUCCESS)";
        if (code == -10011) return " (UNAVAILABLE)";
        if (code == -2147482648) return " (FAILED)";
        if (code == -2147482647) return " (BLOCKING)";
        if (code == -2147482646) return " (TIMEOUT)";
        if (code == -2147482645) return " (INVALID_ARG)";
        return " (code=" + code + ")";
    }
}
