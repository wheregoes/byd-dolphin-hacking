import java.lang.reflect.Method;

/**
 * Calls ICloudServiceApp.sendMsg to trigger cloud remote control commands locally.
 * Bridges to cloudmanager's native listener -> server_data_to_mcu.
 *
 * Build & run:
 *   javac -source 11 -target 11 -d /tmp/cs scripts/CloudFlash.java
 *   d8 --output /tmp/cs /tmp/cs/CloudFlash.class
 *   adb push /tmp/cs/classes.dex /data/local/tmp/cloudflash.dex
 *   adb shell "CLASSPATH=/data/local/tmp/cloudflash.dex app_process / CloudFlash [id]"
 */
public class CloudFlash {
    public static void main(String[] args) {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);

            Method getService = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            Object binder = getService.invoke(null, "cloud_server_app_service");
            if (binder == null) { System.out.println("ERROR: cloud_server_app_service not found"); return; }

            int cmdId = args.length > 0 ? Integer.parseInt(args[0]) : 532;
            String module = "test.app";

            byte[] data;
            if (cmdId == 532) {
                data = hexToBytes("9b895083217c427a8c78405b4aa47ec41600000000");
            } else if (cmdId == 22) {
                data = hexToBytes("9b895083217c427a8c78405b4aa47ec41600000000");
            } else if (cmdId == 1) {
                data = new byte[]{0x01};
            } else {
                data = new byte[]{0x01};
            }

            System.out.println("Calling sendMsg(module=" + module + ", id=" + cmdId + ", len=" + data.length + ")");

            Class<?> parcelClass = Class.forName("android.os.Parcel");
            Method obtain = parcelClass.getMethod("obtain");
            Object _data = obtain.invoke(null);
            Object _reply = obtain.invoke(null);

            Method writeInterfaceToken = parcelClass.getMethod("writeInterfaceToken", String.class);
            Method writeString = parcelClass.getMethod("writeString", String.class);
            Method writeInt = parcelClass.getMethod("writeInt", int.class);
            Method writeByteArray = parcelClass.getMethod("writeByteArray", byte[].class);
            Method transact = binder.getClass().getMethod("transact", int.class, parcelClass, parcelClass, int.class);
            Method readException = parcelClass.getMethod("readException");

            writeInterfaceToken.invoke(_data, "com.byd.cloudserviceapp.aidl.ICloudServiceApp");
            writeString.invoke(_data, module);
            writeInt.invoke(_data, cmdId);
            writeByteArray.invoke(_data, data);
            writeInt.invoke(_data, data.length);

            System.out.println("Sending transact(3, ...)...");
            boolean status = (boolean) transact.invoke(binder, 3, _data, _reply, 0);
            System.out.println("transact status: " + status);

            if (status) {
                readException.invoke(_reply);
                System.out.println("sendMsg OK — no exception. Check if lights flashed!");
            } else {
                System.out.println("transact returned false — service may not be reachable");
            }

            Method recycle = parcelClass.getMethod("recycle");
            recycle.invoke(_data);
            recycle.invoke(_reply);

        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    static byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s", "");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
