import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BydUpgradeProbe {
    static final String SERVER_DESCRIPTOR = "com.byd.upgradesdk.IUpgradeServer";
    static final String LISTENER_DESCRIPTOR = "com.byd.upgradesdk.IUpgradeListener";
    static final int TRANSACTION_updateIVI = 1;
    static final int TRANSACTION_updateMcu = 2;
    static final int TRANSACTION_updateOS = 3;
    static final int INTERFACE_TRANSACTION = 1598968902;

    static final String[] TX_NAMES = {"", "updateIVI", "updateMcu", "updateOS"};

    static volatile String lastCallbackInfo = null;
    static volatile CountDownLatch callbackLatch;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
        } catch (Exception e) {}

        System.out.println("=== BYD upgrade_server Binder Probe ===");
        try {
            Class<?> processClass = Class.forName("android.os.Process");
            int uid = (int) processClass.getMethod("myUid").invoke(null);
            int pid = (int) processClass.getMethod("myPid").invoke(null);
            System.out.println("UID: " + uid + " PID: " + pid);
        } catch (Exception e) {}
        System.out.println();

        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            Object binder = getService.invoke(null, "upgrade_server");

            if (binder == null) {
                System.out.println("FAIL: upgrade_server service NOT found");
                listServices(smClass);
                return;
            }

            Class<?> ibinderClass = Class.forName("android.os.IBinder");
            System.out.println("OK: upgrade_server service found");
            System.out.println("  binder: " + binder);
            System.out.println("  class: " + binder.getClass().getName());
            System.out.println("  alive: " + ibinderClass.getMethod("isBinderAlive").invoke(binder));

            try {
                String desc = (String) ibinderClass.getMethod("getInterfaceDescriptor").invoke(binder);
                System.out.println("  descriptor: " + desc);
            } catch (Exception e) {
                System.out.println("  descriptor error: " + e.getMessage());
            }
            System.out.println();

            probeInterface(binder, ibinderClass);
            System.out.println();

            boolean noCall = args.length > 0 && args[0].equals("--no-call");
            if (noCall) {
                System.out.println("--no-call specified, skipping transaction probes");
                return;
            }

            String testPath = "/sdcard/test_probe_nonexistent.zip";
            for (int tx = 1; tx <= 3; tx++) {
                probeTransaction(binder, ibinderClass, tx, testPath);
                System.out.println();
            }

            System.out.println("=== Probe Complete ===");
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    static void probeInterface(Object binder, Class<?> ibinderClass) {
        System.out.println("--- Interface Transaction Query ---");
        try {
            Class<?> parcelClass = Class.forName("android.os.Parcel");
            Method obtain = parcelClass.getMethod("obtain");
            Method writeInterfaceToken = parcelClass.getMethod("writeInterfaceToken", String.class);
            Method readString = parcelClass.getMethod("readString");
            Method recycle = parcelClass.getMethod("recycle");
            Method transact = ibinderClass.getMethod("transact", int.class, parcelClass, parcelClass, int.class);

            Object data = obtain.invoke(null);
            Object reply = obtain.invoke(null);
            writeInterfaceToken.invoke(data, SERVER_DESCRIPTOR);

            boolean result = (boolean) transact.invoke(binder, INTERFACE_TRANSACTION, data, reply, 0);
            System.out.println("  transact result: " + result);
            String desc = (String) readString.invoke(reply);
            System.out.println("  interface descriptor: " + desc);

            recycle.invoke(reply);
            recycle.invoke(data);
        } catch (Exception e) {
            System.out.println("  ERROR: " + unwrap(e));
        }
    }

    static void probeTransaction(Object binder, Class<?> ibinderClass, int txCode, String filePath) {
        String txName = TX_NAMES[txCode];
        System.out.println("--- Probing " + txName + " (tx=" + txCode + ") ---");
        System.out.println("  filePath: " + filePath);

        try {
            Class<?> parcelClass = Class.forName("android.os.Parcel");
            Class<?> binderClass = Class.forName("android.os.Binder");
            Method obtain = parcelClass.getMethod("obtain");
            Method writeInterfaceToken = parcelClass.getMethod("writeInterfaceToken", String.class);
            Method writeString = parcelClass.getMethod("writeString", String.class);
            Method writeStrongBinder = parcelClass.getMethod("writeStrongBinder", ibinderClass);
            Method readException = parcelClass.getMethod("readException");
            Method dataAvail = parcelClass.getMethod("dataAvail");
            Method recycle = parcelClass.getMethod("recycle");
            Method transact = ibinderClass.getMethod("transact", int.class, parcelClass, parcelClass, int.class);

            // Create a minimal listener Binder stub
            Object listenerBinder = binderClass.getDeclaredConstructor().newInstance();
            // Attach the listener interface descriptor
            try {
                Method attachInterface = binderClass.getMethod("attachInterface",
                    Class.forName("android.os.IInterface"), String.class);
                attachInterface.invoke(listenerBinder, null, LISTENER_DESCRIPTOR);
            } catch (Exception e) {
                System.out.println("  (couldn't attach listener interface: " + e.getMessage() + ")");
            }

            Object data = obtain.invoke(null);
            Object reply = obtain.invoke(null);

            writeInterfaceToken.invoke(data, SERVER_DESCRIPTOR);
            writeString.invoke(data, filePath);
            writeStrongBinder.invoke(data, listenerBinder);

            System.out.println("  sending transact...");
            boolean result = (boolean) transact.invoke(binder, txCode, data, reply, 0);
            System.out.println("  transact returned: " + result);

            try {
                readException.invoke(reply);
                System.out.println("  reply: NO EXCEPTION — call was accepted!");
            } catch (Exception e) {
                String msg = unwrap(e);
                if (msg.contains("SecurityException")) {
                    System.out.println("  SECURITY EXCEPTION: " + msg);
                    System.out.println("  >> Service rejected call — caller not authorized");
                } else {
                    System.out.println("  reply exception: " + msg);
                }
            }

            int avail = (int) dataAvail.invoke(reply);
            if (avail > 0) {
                System.out.println("  reply data remaining: " + avail + " bytes");
                try {
                    Method readInt = parcelClass.getMethod("readInt");
                    int val = (int) readInt.invoke(reply);
                    System.out.println("  reply int value: " + val + " (" + errorCodeName(val) + ")");
                } catch (Exception ignored) {}
            }

            recycle.invoke(reply);
            recycle.invoke(data);

            // Wait briefly for async callback
            Thread.sleep(2000);

        } catch (Exception e) {
            String msg = unwrap(e);
            if (msg.contains("SecurityException")) {
                System.out.println("  SECURITY EXCEPTION: " + msg);
                System.out.println("  >> Service rejected call from shell UID");
            } else {
                System.out.println("  ERROR: " + msg);
            }
        }
    }

    static String errorCodeName(int code) {
        switch (code) {
            case 1: return "PACKAGE_NOT_EXIST";
            case 2: return "COPY_ERROR";
            case 3: return "UNZIP_ERROR";
            case 4: return "CONFIG_NOT_MATCH";
            case 5: return "PAYLOAD_CONTENT_ERROR";
            case 6: return "VERIFY_PACKAGE_ERROR";
            case 11: return "MCU_UPGRADE_FAIL";
            case 12: return "MCU_XCD_FILE_ERROR";
            case 13: return "DSP_XCD_FILE_ERROR";
            case 110: return "UPGRADE_IN_PROGRESS";
            case 999: return "OTHER_ERROR";
            case 3000: return "MEDIA_UPGRADE_SUCCESS";
            case 3001: return "MEDIA_UPGRADE_FAIL";
            default: return "UNKNOWN_" + code;
        }
    }

    static void listServices(Class<?> smClass) {
        System.out.println("\nSearching for upgrade/OTA related services...");
        try {
            String[] services = (String[]) smClass.getMethod("listServices").invoke(null);
            for (String s : services) {
                String lower = s.toLowerCase();
                if (lower.contains("upgrade") || lower.contains("ota") ||
                    lower.contains("update") || lower.contains("fota")) {
                    System.out.println("  found: " + s);
                }
            }
        } catch (Exception e) {
            System.out.println("  listServices failed: " + e.getMessage());
        }
    }

    static String unwrap(Exception e) {
        Throwable cause = e.getCause();
        if (cause != null) return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
