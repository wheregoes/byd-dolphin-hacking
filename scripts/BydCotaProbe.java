import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BydCotaProbe {
    static final String SECRET = "[REDACTED_API_SECRET]";
    static final String APP_ID = "39701099963858720";

    static String vin = "";
    static String iccid = "";
    static String mcuVer = "";
    static String socVer = "";
    static String country = "";
    static String mediaType = "";
    static String carSeries = "";
    static String carType = "";
    static String vehicleProject = "";
    static String uiGeneration = "";
    static int deviceType = -1;
    static String resolution = "1920x1080";
    static int cotaVer = 2;
    static String areaHost = "";

    public static void main(String[] args) {
        System.out.println("=== BYD COTA API Probe ===");
        System.out.println();

        gatherSystemProperties();
        gatherVehicleProperties();

        System.out.println("--- Collected Vehicle Info ---");
        System.out.println("  VIN: " + vin);
        System.out.println("  ICCID: " + iccid);
        System.out.println("  IMEI: " + getProp("ril.imei"));
        System.out.println("  MCU version: " + mcuVer);
        System.out.println("  SOC version: " + socVer);
        System.out.println("  Country code: " + country);
        System.out.println("  Media type: " + mediaType);
        System.out.println("  Car series: " + carSeries);
        System.out.println("  Car type: " + carType);
        System.out.println("  Vehicle project: " + vehicleProject);
        System.out.println("  Device type: " + deviceType);
        System.out.println("  UI generation: " + uiGeneration);
        System.out.println("  Resolution: " + resolution);
        System.out.println("  COTA version: " + cotaVer);
        System.out.println("  Area prefix: " + getProp("persist.sys.area_prefix"));
        System.out.println("  Region: " + getProp("ro.build.region"));
        System.out.println("  SIM operator: " + getProp("gsm.sim.operator.alpha"));
        System.out.println("  MCC/MNC: " + getProp("gsm.sim.operator.numeric"));
        System.out.println();

        String area = getProp("persist.sys.area_prefix");
        if (!area.isEmpty()) {
            areaHost = "https://idilink-" + area + ".byd.auto";
        }

        for (String arg : args) {
            if (arg.startsWith("--vin=")) {
                vin = arg.substring(6);
                System.out.println("  VIN override: " + vin);
            }
        }

        boolean infoOnly = false;
        for (String arg : args) {
            if (arg.equals("--info-only")) infoOnly = true;
        }
        if (infoOnly) {
            System.out.println("--info-only: skipping API calls");
            return;
        }

        System.out.println("========================================");
        System.out.println("  Test 1: Area Resolution (private API)");
        System.out.println("========================================");
        testAreaLookup();
        System.out.println();

        if (areaHost.isEmpty()) {
            System.out.println("ERROR: No area host resolved, cannot call COTA APIs.");
            return;
        }

        System.out.println("  Using COTA host: " + areaHost);
        System.out.println();

        System.out.println("========================================");
        System.out.println("  Test 2: getEnvConfig");
        System.out.println("========================================");
        testGetEnvConfig();
        System.out.println();

        System.out.println("========================================");
        System.out.println("  Test 3: groupConfigs query");
        System.out.println("========================================");
        testGroupConfigs();
        System.out.println();

        System.out.println("=== COTA Probe Complete ===");
    }

    static void gatherSystemProperties() {
        iccid = getProp("ril.csim.iccid");
        mcuVer = getProp("mcu_version");
        if (mcuVer.isEmpty()) mcuVer = getProp("persist.sys.mcu_version");
        socVer = getProp("apps.setting.product.outswver");
        country = getProp("sys.byd.countrycode");

        String inswver = getProp("apps.setting.product.inswver");
        if (!inswver.isEmpty()) {
            String[] parts = inswver.split("_");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                String up = p.toUpperCase();
                if (up.startsWith("USER") || up.startsWith("USERDEBUG")) break;
                if (sb.length() > 0) sb.append("_");
                sb.append(p);
            }
            mediaType = sb.toString();
        }

        vin = getProp("persist.sys.cloud.last_vin");
        if (vin.isEmpty()) vin = getProp("sys.virtual.vin");

        carType = getProp("persist.sys.car.type");
        String vehicleType = getProp("ro.vehicle.type");
        if (!vehicleType.isEmpty()) uiGeneration = vehicleType;
    }

    static void gatherVehicleProperties() {
        try {
            Class.forName("android.os.Looper").getMethod("prepareMainLooper").invoke(null);
        } catch (Exception e) {}

        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Object context = atClass.getMethod("getSystemContext").invoke(thread);

            // Try BYDAutoBodyworkDevice for real VIN
            try {
                Class<?> bodyworkClass = Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice");
                Method getInstance = null;
                for (Method m : bodyworkClass.getMethods()) {
                    if (m.getName().equals("getInstance") && m.getParameterCount() == 1) {
                        getInstance = m;
                        break;
                    }
                }
                if (getInstance != null) {
                    Object bodywork = getInstance.invoke(null, context);
                    if (bodywork != null) {
                        Object realVin = bodyworkClass.getMethod("getRealAutoVIN").invoke(bodywork);
                        if (realVin != null && !realVin.toString().isEmpty()) {
                            vin = realVin.toString();
                            System.out.println("  [VIN] from BYDAutoBodyworkDevice: " + mask(vin, 5));
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("  [VIN] BYDAutoBodyworkDevice error: " + e.getMessage());
            }

            // Try BYDAutoManager getBuffer for VIN
            try {
                Object mgr = context.getClass().getMethod("getSystemService", String.class).invoke(context, "auto");
                if (mgr != null) {
                    Method getBuffer = mgr.getClass().getMethod("getBuffer", int.class, int.class);
                    byte[] vinBuf = (byte[]) getBuffer.invoke(mgr, 1001, 0x4C600001);
                    if (vinBuf != null && vinBuf.length > 0) {
                        String vinStr = new String(vinBuf).trim().replaceAll("[^A-Z0-9]", "");
                        if (vinStr.length() >= 17) {
                            vin = vinStr;
                            System.out.println("  [VIN] from BYDAutoManager CAN: " + mask(vin, 5));
                        } else {
                            System.out.print("  [VIN] CAN buffer (" + vinBuf.length + "b): ");
                            for (byte b : vinBuf) System.out.printf("%02x", b);
                            System.out.println();
                        }
                    }

                    // Try getInt for vehicle type/device type info
                    Method getInt = mgr.getClass().getMethod("getInt", int.class, int.class);
                    try {
                        int ct = (int) getInt.invoke(mgr, 1001, 0x4C600002);
                        if (ct > 0 && carType.isEmpty()) carType = String.valueOf(ct);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                System.out.println("  [VIN] BYDAutoManager error: " + e.getMessage());
            }

            // Try DiCar for carSeries, carType, vehicleId, deviceType, uiName
            try {
                Class<?> diCarClass = Class.forName("com.byd.car.DiCar");
                Class<?> infoMgrClass = Class.forName("com.byd.car.ICarInfoManager");
                Class<?> cabinMgrClass = Class.forName("com.byd.car.ICarCabinManager");

                Method getCarMgr = null;
                for (Method m : diCarClass.getMethods()) {
                    if (m.getName().equals("getCarManager") && m.getParameterCount() == 2) {
                        getCarMgr = m;
                        break;
                    }
                }

                if (getCarMgr != null) {
                    Object infoMgr = getCarMgr.invoke(null, context, infoMgrClass);
                    if (infoMgr != null) {
                        try {
                            Object b = infoMgrClass.getMethod("getBrand").invoke(infoMgr);
                            if (b != null && !b.toString().isEmpty()) carSeries = b.toString();
                        } catch (Exception ignored) {}
                        try {
                            Object t = infoMgrClass.getMethod("getCarType").invoke(infoMgr);
                            if (t != null && !t.toString().isEmpty()) carType = t.toString();
                        } catch (Exception ignored) {}
                        try {
                            Object v = infoMgrClass.getMethod("getVehicleId").invoke(infoMgr);
                            if (v != null) vehicleProject = v.toString();
                        } catch (Exception ignored) {}
                        System.out.println("  [DiCar] series=" + carSeries + " type=" + carType + " project=" + vehicleProject);
                    }

                    Object cabinMgr = getCarMgr.invoke(null, context, cabinMgrClass);
                    if (cabinMgr != null) {
                        try {
                            Object dt = cabinMgrClass.getMethod("getDeviceType").invoke(cabinMgr);
                            if (dt != null) deviceType = ((Number) dt).intValue();
                        } catch (Exception ignored) {}
                        try {
                            Object ui = cabinMgrClass.getMethod("getUIName").invoke(cabinMgr);
                            if (ui != null && !ui.toString().isEmpty()) uiGeneration = ui.toString();
                        } catch (Exception ignored) {}
                        System.out.println("  [DiCar] deviceType=" + deviceType + " uiGen=" + uiGeneration);
                    }
                }
            } catch (ClassNotFoundException e) {
                System.out.println("  [DiCar] not available: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  [DiCar] error: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("  ActivityThread init error: " + e.getMessage());
        }
    }

    static String getProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class, String.class).invoke(null, key, "");
        } catch (Exception e) {
            return "";
        }
    }

    static String sign(String message, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(message.getBytes());
            byte[] b64bytes = Base64.getEncoder().encodeToString(hmac).getBytes();
            char[] hexDigits = "0123456789abcdef".toCharArray();
            char[] result = new char[b64bytes.length * 2];
            for (int i = 0; i < b64bytes.length; i++) {
                result[i * 2] = hexDigits[(b64bytes[i] >>> 4) & 0x0F];
                result[i * 2 + 1] = hexDigits[b64bytes[i] & 0x0F];
            }
            return new String(result);
        } catch (Exception e) {
            System.out.println("  HMAC sign error: " + e.getMessage());
            return "";
        }
    }

    static Map<String, String> buildHeaders(String protocolVer) {
        long timestamp = System.currentTimeMillis();
        int nonce = new Random().nextInt(9000) + 1000;
        String message = SECRET + nonce + timestamp;
        String signature = sign(message, SECRET);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("timestamp", String.valueOf(timestamp));
        headers.put("appid", APP_ID);
        headers.put("nonce", String.valueOf(nonce));
        headers.put("sign", signature);
        headers.put("vin", vin);
        headers.put("iccid", iccid);
        headers.put("mcuVer", mcuVer);
        headers.put("socVer", socVer);
        headers.put("country", country);
        headers.put("cotaVer", String.valueOf(cotaVer));
        headers.put("carSeries", carSeries);
        headers.put("vehicleProject", vehicleProject);
        headers.put("deviceType", String.valueOf(deviceType));
        headers.put("mediaType", mediaType);
        headers.put("resolution", resolution);
        headers.put("uiGeneration", uiGeneration);
        headers.put("carType", carType);
        if (protocolVer != null && !protocolVer.isEmpty()) {
            headers.put("protocolVer", protocolVer);
        }
        return headers;
    }

    static String httpRequest(String urlStr, String method, Map<String, String> headers, String body) {
        StringBuilder sb = new StringBuilder();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    conn.setRequestProperty(h.getKey(), h.getValue());
                }
            }
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
            }

            int code = conn.getResponseCode();
            sb.append("HTTP ").append(code).append(" ").append(conn.getResponseMessage()).append("\n");

            // Print response headers
            for (int i = 0; ; i++) {
                String key = conn.getHeaderFieldKey(i);
                String val = conn.getHeaderField(i);
                if (val == null) break;
                if (key != null) sb.append("  ").append(key).append(": ").append(val).append("\n");
            }

            BufferedReader reader;
            try {
                reader = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream()));
            } catch (Exception e) {
                sb.append("  (no response body)");
                return sb.toString();
            }
            sb.append("  Body: ");
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    static void testAreaLookup() {
        String simOp = getProp("gsm.sim.operator.alpha");
        String mccMnc = getProp("gsm.sim.operator.numeric");
        System.out.println("  SIM: operator=" + simOp + " mccMnc=" + mccMnc + " country=" + country);

        String url = "http://idilink-private-global.iov.byd.auto/apis/config/getEnvConfig"
            + "?operator=" + encode(simOp) + "&mccMnc=" + encode(mccMnc) + "&countryCode=" + encode(country);
        System.out.println("  URL: " + url);

        Map<String, String> headers = buildHeaders("");
        String resp = httpRequest(url, "GET", headers, null);
        System.out.println("  " + resp);
    }

    static void testGetEnvConfig() {
        String url = areaHost + "/apis/config/getEnvConfig";
        System.out.println("  URL: " + url);
        Map<String, String> headers = buildHeaders("1.0");
        String resp = httpRequest(url, "GET", headers, null);
        System.out.println("  " + resp);
    }

    static void testGroupConfigs() {
        if (areaHost.isEmpty()) return;
        String url = areaHost + "/vehicle-data-api/data/groupConfigs";
        System.out.println("  URL: " + url);

        Map<String, String> headers = buildHeaders("1.0");
        String body = "{\"appInfos\":["
            + "{\"appPkgName\":\"com.byd.intelligententry\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.cota.globalapp\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.cluster\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.car.setting\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.otaupdate\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.automultipletheme\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.car.server\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.avasplayer\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.cota\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.cloudmanager\",\"configVer\":0},"
            + "{\"appPkgName\":\"com.byd.nfc\",\"configVer\":0}"
            + "]}";
        System.out.println("  Request body: " + body);
        String resp = httpRequest(url, "POST", headers, body);
        System.out.println("  " + resp);
    }

    static String mask(String s, int visible) {
        if (s == null || s.length() <= visible) return s;
        return s.substring(0, visible) + "***" + s.substring(s.length() - 2);
    }

    static String encode(String s) {
        if (s == null) return "";
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
