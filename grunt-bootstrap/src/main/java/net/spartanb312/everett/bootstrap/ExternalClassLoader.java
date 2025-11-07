package net.spartanb312.everett.bootstrap;

import net.spartanb312.everett.bootstrap.transform.ClassTransformerManager;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.lang.Math.max;

public class ExternalClassLoader extends URLClassLoader {

    public static Set<ExternalClassLoader> loaderPool = new HashSet<>();

    public ExternalClassLoader(URL[] urls, ClassLoader parent, ClassTransformerManager ctm) {
        super(urls, parent);
        loaderPool.add(this);
        this.ctm = ctm;
    }

    public ExternalClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        loaderPool.add(this);
        ctm = null;
    }

    public ExternalClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        loaderPool.add(this);
        ctm = null;
    }

    public ExternalClassLoader() {
        super(new URL[0], ExternalClassLoader.class.getClassLoader());
        loaderPool.add(this);
        ctm = null;
    }

    private final ClassTransformerManager ctm;

    private final HashMap<String, byte[]> classesCache = new HashMap<>();
    private final HashMap<String, URL> resourceCache = new HashMap<>();
    private final Set<String> resourcePaths = new HashSet<>();
    private final String[] systemPaths = System.getProperty("java.library.path").split(";");
    private final String[] dummyPaths = {"C:\\Windows\\System\\", "C:\\Windows\\System32\\"};

    public void addURLs(URL... urls) {
        for (URL url : urls) {
            this.addURL(url);
        }
    }

    public void addPath(String path) {
        resourcePaths.add(path);
    }

    public void removePath(String path) {
        resourcePaths.remove(path);
    }

    private Class<?> findClass(String name, boolean deepScan) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) return loaded;
            byte[] bytes = classesCache.getOrDefault(name, null);
            if (bytes != null) return defineClass(name, bytes, 0, bytes.length);
            if (deepScan) {
                for (ExternalClassLoader loader : loaderPool) {
                    if (loader == this) continue;
                    try {
                        return loader.findClass(name, false);
                    } catch (ClassNotFoundException ignored) {
                        // Not found class in this loader
                    }
                }
            }
            throw new ClassNotFoundException(name);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return findClass(name, true);
        } catch (ClassNotFoundException ignored) {
            // Find class in parent loader
            return super.findClass(name);
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                byte[] bytes = classesCache.getOrDefault(name, null);
                if (bytes != null) return defineClass(name, bytes, 0, bytes.length);
            }
            return c == null ? super.loadClass(name, resolve) : c;
        }
    }

    private URL findResource(String name, boolean deepScan) {
        // Find in this class loader
        URL resInCache = resourceCache.getOrDefault(name, null);
        if (resInCache != null) return resInCache;
        URL resInThis = super.findResource(name);
        if (resInThis != null) return resInThis;
        URL resInParent = getParent().getResource(name);
        if (resInParent != null) return resInParent;
        for (String path : resourcePaths) {
            URL res = findInPath(path, name);
            if (res != null) return res;
        }
        for (String sysPath : systemPaths) {
            URL res = findInPath(sysPath, name);
            if (res != null) return res;
        }
        for (String dummy : dummyPaths) {
            URL res = findInPath(dummy, name);
            if (res != null) return res;
        }
        // Deep scan other class loader
        if (deepScan) {
            for (ExternalClassLoader loader : loaderPool) {
                if (loader == this) continue;
                var inOther = findResource(name, false);
                if (inOther != null) return inOther;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("ALL")
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> urlList = new HashSet<>();
        // Find in this class loader
        URL resInCache = resourceCache.getOrDefault(name, null);
        if (resInCache != null) urlList.add(resInCache);
        URL resInThis = super.findResource(name);
        if (resInThis != null) urlList.add(resInThis);
        URL resInParent = getParent().getResource(name);
        if (resInParent != null) urlList.add(resInParent);
        for (String path : resourcePaths) {
            URL res = findInPath(path, name);
            if (res != null) urlList.add(res);
        }
        for (String sysPath : systemPaths) {
            URL res = findInPath(sysPath, name);
            if (res != null) urlList.add(res);
        }
        for (String dummy : dummyPaths) {
            URL res = findInPath(dummy, name);
            if (res != null) urlList.add(res);
        }
        // Deep scan other class loader
        for (ExternalClassLoader loader : loaderPool) {
            if (loader == this) continue;
            var inOther = findResource(name, false);
            if (inOther != null) urlList.add(inOther);
        }
        return Collections.enumeration(urlList);
    }

    @Override
    public URL findResource(String name) {
        return findResource(name, true);
    }

    public URL loadResource(File file) throws IOException {
        URL url = file.toURI().toURL();
        resourceCache.put(file.toString().replace("\\", "/"), url);
        return url;
    }

    public void loadJar(String file) {
        try {
            loadJar(new File(file));
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    public void loadJar(File file) throws IOException {
        ZipInputStream zip = new ZipInputStream(new FileInputStream(file));
        while (true) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null) break;
            else if (entry.getName().toLowerCase().endsWith(".class")) {
                classesCache.put(removeSuffix(entry.getName().replace("/", "."), ".class"), readBytes(zip));
            } else {
                URL resourceURL = new URL(
                        "jar:file:"
                                + (Platform.getPlatform().getOS() == Platform.OS.Linux ? "" : "/")
                                + file.getAbsolutePath().replace("\\", "/")
                                + "!/" + entry.getName()
                );
                resourceCache.put(entry.getName(), resourceURL);
            }
        }
    }

    private static byte[] readBytes(InputStream input) throws IOException {
        int size = max(8 * 1024, input.available());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(size);
        copyTo(input, buffer, size);
        return buffer.toByteArray();
    }

    private static void copyTo(InputStream in, OutputStream out, int bufferSize) throws IOException {
        byte[] buffer = new byte[bufferSize];
        var bytes = in.read(buffer);
        while (bytes >= 0) {
            out.write(buffer, 0, bytes);
            bytes = in.read(buffer);
        }
    }

    private static String removeSuffix(String value, String suffix) {
        if (value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length());
        } else return value;
    }

    private static URL findInPath(String path, String name) {
        String adjustedPath = removeSuffix(removeSuffix(path, "/"), "\\");
        File file = new File(adjustedPath + "/" + name);
        if (file.exists()) try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
        else return null;
    }

    public void initKotlinObject(String name) throws Exception {
        invokeKotlinObjectField(loadClass(name));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void invokeKotlinObjectField(Class<?> clazz) throws Exception {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().equals("INSTANCE")) {
                field.get(null);
                break;
            }
        }
    }

}
