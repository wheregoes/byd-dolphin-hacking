import java.io.*;

public class SysMix {
    public static void main(String[] args) {
        try {
            String cmd = args.length > 0 ? String.join(" ", args) : "tinymix";
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) System.out.println(line);
            BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = er.readLine()) != null) System.err.println(line);
            p.waitFor();
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }
}
