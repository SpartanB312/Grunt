package net.spartanb312.everett.bootstrap;

import net.spartanb312.everett.bootstrap.transform.ClassTransformerManager;

import java.io.File;
import java.net.URL;

public class LaunchClassLoader extends ExternalClassLoader {

    public final static ClassTransformerManager launchCTM = new ClassTransformerManager();
    public final static LaunchClassLoader INSTANCE = new LaunchClassLoader(launchCTM);

    public LaunchClassLoader(ClassTransformerManager ctm) {
        super(new URL[0], Main.class.getClassLoader(), ctm);
    }

    public static void loadJarFile(String file) {
        loadJarFile(new File(file));
    }

    public static void loadJarFile(File file) {
        try {
            INSTANCE.loadJar(file);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public static URL loadResourceFile(File file) {
        try {
            return INSTANCE.loadResource(file);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return null;
    }

    public static void addResourcePath(String path) {
        INSTANCE.addPath(path);
    }

    public static void removeResourcePath(String path) {
        INSTANCE.removePath(path);
    }

}
