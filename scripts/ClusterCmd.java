/**
 * Sends debug commands to the BYD instrument cluster (driver display) via the
 * AutoContainer system service.
 *
 * Discovered by decompiling ClusterDebug.apk (com.byd.clusterdebug):
 *   - The app calls AutoContainerManager.sendInfo(1000, cmdId, "")
 *   - AutoContainerManager wraps IAutoContainer.sendInfo(type, infoInt, infoStr)
 *   - IAutoContainer is served as binder service "AutoContainer"
 *   - Transaction code 2 = sendInfo(int type, int infoInt, String infoStr)
 *
 * The cluster is a separate embedded display that receives a video stream from
 * the head unit. These commands control the cluster renderer: themes, modes,
 * warning lights, ADAS display, skins, screen size, FPS overlay, etc.
 *
 * Uses pure reflection (no android imports) — compiles without android.jar.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/clustercmd scripts/ClusterCmd.java
 *   d8 --output /tmp/clustercmd /tmp/clustercmd/ClusterCmd.class
 *   adb push /tmp/clustercmd/classes.dex /data/local/tmp/clustercmd.dex
 *
 *   adb shell "CLASSPATH=/data/local/tmp/clustercmd.dex app_process / ClusterCmd"
 *   adb shell "CLASSPATH=/data/local/tmp/clustercmd.dex app_process / ClusterCmd 14"
 *   adb shell "CLASSPATH=/data/local/tmp/clustercmd.dex app_process / ClusterCmd 8"
 */
public class ClusterCmd {
    static final String DESCRIPTOR = "android.os.IAutoContainer";
    static final String SERVICE_NAME = "AutoContainer";
    static final int TRANSACTION_sendInfo = 2;
    static final int TYPE_CLUSTER = 1000;

    static Class<?> parcelClass;
    static Class<?> serviceManagerClass;
    static Class<?> ibinderClass;

    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);

            parcelClass = Class.forName("android.os.Parcel");
            serviceManagerClass = Class.forName("android.os.ServiceManager");
            ibinderClass = Class.forName("android.os.IBinder");

            System.out.println("=== BYD Cluster Command Sender ===");
            System.out.println("Service: " + SERVICE_NAME);
            System.out.println("Descriptor: " + DESCRIPTOR);
            System.out.println();

            Object binder = getService(SERVICE_NAME);
            if (binder == null) {
                System.out.println("FAILED: Could not get " + SERVICE_NAME + " service");
                return;
            }
            System.out.println("Got binder: " + binder);
            System.out.println("Interface descriptor: " + getInterfaceDescriptor(binder));
            System.out.println();

            if (args.length == 0) {
                printCommandList();
                System.out.println();
                System.out.println("Usage: ClusterCmd <cmdId>");
                System.out.println("  e.g. ClusterCmd 14  (FPS display on)");
                System.out.println("       ClusterCmd 15  (FPS display off)");
                System.out.println("       ClusterCmd 8   (Classic mode theme)");
                System.out.println("       ClusterCmd 9   (Tech mode theme)");
                System.out.println("       ClusterCmd 6   (Day mode)");
                System.out.println("       ClusterCmd 7   (Night mode)");
            } else {
                int cmdId = Integer.parseInt(args[0]);
                int result = sendInfo(binder, TYPE_CLUSTER, cmdId, "");
                System.out.println("sendInfo(1000, " + cmdId + ", \"\") → result = " + result +
                    formatResult(result));
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static int sendInfo(Object binder, int type, int infoInt, String infoStr) throws Exception {
        Object data = parcelClass.getMethod("obtain").invoke(null);
        Object reply = parcelClass.getMethod("obtain").invoke(null);
        try {
            parcelClass.getMethod("writeInterfaceToken", String.class).invoke(data, DESCRIPTOR);
            parcelClass.getMethod("writeInt", int.class).invoke(data, type);
            parcelClass.getMethod("writeInt", int.class).invoke(data, infoInt);
            parcelClass.getMethod("writeString", String.class).invoke(data, infoStr);

            Boolean status = (Boolean) ibinderClass.getMethod("transact", int.class, parcelClass, parcelClass, int.class)
                .invoke(binder, TRANSACTION_sendInfo, data, reply, 0);

            if (!status) {
                System.out.println("transact returned false (service not found?)");
                return -1;
            }
            parcelClass.getMethod("readException").invoke(reply);
            return (int) parcelClass.getMethod("readInt").invoke(reply);
        } finally {
            parcelClass.getMethod("recycle").invoke(data);
            parcelClass.getMethod("recycle").invoke(reply);
        }
    }

    static Object getService(String name) throws Exception {
        return serviceManagerClass.getMethod("getService", String.class).invoke(null, name);
    }

    static String getInterfaceDescriptor(Object binder) throws Exception {
        Object data = parcelClass.getMethod("obtain").invoke(null);
        Object reply = parcelClass.getMethod("obtain").invoke(null);
        try {
            ibinderClass.getMethod("transact", int.class, parcelClass, parcelClass, int.class)
                .invoke(binder, 1598968902, data, reply, 0);
            return (String) parcelClass.getMethod("readString").invoke(reply);
        } finally {
            parcelClass.getMethod("recycle").invoke(data);
            parcelClass.getMethod("recycle").invoke(reply);
        }
    }

    static String formatResult(int code) {
        if (code == 0) return " (OK)";
        if (code == -5) return " (PERMISSION_DENIED / not native container)";
        if (code == -1) return " (GENERIC_FAILURE)";
        return " (unknown)";
    }

    static void printCommandList() {
        System.out.println("=== Cluster Debug Commands (from ClusterDebug.apk) ===");
        System.out.println("DiLink 3.0 / Di4.0 command set (SecondActivity.infoListInit):");
        System.out.println("  0  = Resume cluster video stream");
        System.out.println("  1  = Disconnect cluster video stream");
        System.out.println("  2  = All warning lights ON");
        System.out.println("  3  = All warning lights OFF");
        System.out.println("  4  = All warning lights 4Hz flash");
        System.out.println("  5  = Warning lights per actual CAN");
        System.out.println("  --- THEMES / MODES ---");
        System.out.println("  6  = Day mode");
        System.out.println("  7  = Night mode");
        System.out.println("  8  = Classic mode (classic dashboard)");
        System.out.println("  9  = Tech mode (tech dashboard)");
        System.out.println("  --- ADAS ---");
        System.out.println("  12 = Show ADAS");
        System.out.println("  13 = Hide ADAS");
        System.out.println("  --- DISPLAY ---");
        System.out.println("  14 = FPS display ON");
        System.out.println("  15 = FPS display OFF");
        System.out.println("  16 = Full-screen cast ON");
        System.out.println("  17 = Half-screen cast ON");
        System.out.println("  18 = Cast OFF");
        System.out.println("  19 = OSD sequence frames ON");
        System.out.println("  20 = OSD sequence frames OFF");
        System.out.println("  --- VEHICLE TYPE ---");
        System.out.println("  21 = Vehicle type: pure electric");
        System.out.println("  22 = Vehicle type: hybrid");
        System.out.println("  23 = Vehicle type: fuel");
        System.out.println("  --- SCREEN SIZE ---");
        System.out.println("  29 = Switch to 8.8 inch screen");
        System.out.println("  30 = Switch to 12.3 inch screen");
        System.out.println("  31 = Switch to 10.25 inch screen");
        System.out.println("  --- ADAS v2 ---");
        System.out.println("  32 = 3D ADAS auto-refresh ON");
        System.out.println("  33 = 3D ADAS auto-refresh OFF");
        System.out.println("  --- DILINK VERSION ---");
        System.out.println("  34 = Di3.0 mode");
        System.out.println("  35 = Di4.0 mode");
        System.out.println("  --- DEBUG ---");
        System.out.println("  36 = Qt screenshot / ADB screenshot / dump surfaceflinger");
        System.out.println("  37 = Log level DEBUG");
        System.out.println("  38 = Log level INFO");
        System.out.println("  39 = Simple navigation");
        System.out.println("  40 = Dump some info");
        System.out.println("  41 = Stress test ON (DO NOT USE ON REAL CAR!)");
        System.out.println("  42 = Stress test OFF");
        System.out.println("  --- SKINS (Di5) ---");
        System.out.println("  74 = No skin");
        System.out.println("  75 = Load skin 1");
        System.out.println("  76 = Load skin 2");
        System.out.println("  83 = Force Denza UI");
        System.out.println("  84 = Force Dynasty UI");
        System.out.println("  85 = Show UI by car model");
        System.out.println("  88 = No car body image");
        System.out.println("  89 = Load car body image");
        System.out.println("  90 = Ocean classic mode dashboard");
        System.out.println("  --- SCREEN RECORDING ---");
        System.out.println("  218 = Cluster screen recording ON");
        System.out.println("  219 = Cluster screen recording OFF");
        System.out.println("  --- INDICATOR LIGHTS ---");
        System.out.println("  257 = Lights indicators OFF");
        System.out.println("  258 = Lights indicators ON");
        System.out.println("  259 = ADAS indicators OFF");
        System.out.println("  260 = ADAS indicators ON");
        System.out.println("  --- RACING MODE ---");
        System.out.println("  278 = R4 original racing mode");
        System.out.println("  279 = R4 Nurburgring racing mode");
    }
}
