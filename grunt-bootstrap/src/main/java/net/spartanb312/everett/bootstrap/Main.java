package net.spartanb312.everett.bootstrap;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Module(name = "Launch Wrapper", version = Main.LAUNCH_WRAPPER_VERSION, description = "Bootstrap launch wrapper", author = "B_312")
public class Main {

    public static final String LAUNCH_WRAPPER_VERSION = "1.2.0";
    public static String[] args;
    public static List<ExternalClassLoader> classLoaders = new ArrayList<>();
    private static final String defaultEntry = "net.spartanb312.everett.bootstrap.DefaultEntry";
    public static String applicationEntry = defaultEntry;

    public static void main(String[] args) throws Exception {
        LaunchLogger.info("Initializing launch wrapper...");

        // Detect system
        LaunchLogger.info("Running on platform: " + Platform.getPlatform().getPlatformName());

        // Check program args
        Main.args = args;
        boolean devMode = Arrays.stream(args).toList().contains("-DevMode");

        // Launch Engine
        if (!devMode) {
            LaunchLogger.info("Loading dependencies...");
            readFiles("libs/", ".jar", true, file -> {
                LaunchLogger.info(" - " + file.getName());
                LaunchClassLoader.loadJarFile(file);
            });
            LaunchLogger.info("Loading components...");
            readFiles("eons/", ".jar", true, file -> {
                LaunchLogger.info(" - " + file.getName());
                try (var classLoader = new ExternalClassLoader(new URL[0], Main.class.getClassLoader(), LaunchClassLoader.launchCTM)) {
                    classLoader.loadJar(file);
                    classLoaders.add(classLoader);
                    var entryInJar = findEntry(classLoader.findResource("META-INF/MANIFEST.MF"));
                    if (entryInJar != null) applicationEntry = entryInJar;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            LaunchClassLoader.INSTANCE.initKotlinObject(applicationEntry);
        } else {
            if (args.length == 2) {
                LaunchLogger.info("Using Entry: " + args[1]);
                ExternalClassLoader.invokeKotlinObjectField(Class.forName(args[1]));
            } else {
                URL manifest = Main.class.getResource("/META-INF/MANIFEST.MF");
                assert manifest != null;
                ExternalClassLoader.invokeKotlinObjectField(Class.forName(findEntry(manifest)));
            }
        }
    }

    private static String findEntry(URL manifest) {
        String entry = defaultEntry;
        try {
            InputStreamReader ir = new InputStreamReader(manifest.openStream());
            BufferedReader br = new BufferedReader(ir);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Launch-Entry: ")) {
                    entry = line.substring(14);
                }
            }
        } catch (Exception ignored) {
        }
        LaunchLogger.info("Using Entry: " + entry);
        return entry;
    }

    private interface FileTask {
        void invoke(File file);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "CallToPrintStackTrace"})
    private static void readFiles(String path, String suffix, boolean ignoreCase, FileTask action) {
        File current = new File(path);
        if (!current.exists()) {
            try {
                current.getParentFile().mkdirs();
                current.createNewFile();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return;
        }
        if (current.isDirectory()) {
            String[] list = current.list();
            if (list != null) {
                for (String child : list) {
                    File childFile = new File(path + child);
                    if (childFile.isDirectory()) readFiles(path + child, suffix, ignoreCase, action);
                    else if (endWith(childFile.getName(), suffix, ignoreCase)) try {
                        action.invoke(childFile);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        } else if (endWith(current.getName(), suffix, ignoreCase)) try {
            action.invoke(current);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static boolean endWith(String src, String suffix, boolean ignoreCase) {
        if (ignoreCase) return src.toLowerCase().endsWith(suffix.toLowerCase());
        else return src.endsWith(suffix);
    }

}
