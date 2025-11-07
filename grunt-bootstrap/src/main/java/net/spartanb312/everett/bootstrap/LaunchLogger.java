package net.spartanb312.everett.bootstrap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressWarnings("ALL")
public class LaunchLogger {
    private static BufferedWriter bufferedWriter;

    static {
        File file = new File("logs/launch/" + new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()) + ".txt");
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
            bufferedWriter = new BufferedWriter(new FileWriter(file));
        } catch (Exception ignored) {
        }
    }

    public static void info(String msg) {
        log(msg, "INFO");
    }

    public static void warn(String msg) {
        log(msg, "WARN");
    }

    public static void error(String msg) {
        log(msg, "ERROR");
    }

    public static void fatal(String msg) {
        log(msg, "FATAL");
    }

    public static void debug(String msg) {
        log(msg, "DEBUG");
    }

    private static void log(String msg, String level) {
        String time = new SimpleDateFormat("MM-dd HH:mm:ss").format(new Date());
        String str = "[" + time + "][" + Thread.currentThread().getName() + "/" + level + "][Launch] " + msg;
        System.out.println(str);
        try {
            BufferedWriter bw = bufferedWriter;
            if (bw != null) {
                bw.write(str);
                bw.flush();
                bw.newLine();
            }
        } catch (Exception ignored) {
        }
    }
}
