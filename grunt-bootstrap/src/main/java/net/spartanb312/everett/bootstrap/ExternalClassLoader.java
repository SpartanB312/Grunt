package net.spartanb312.everett.bootstrap;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.jar.JarFile;

import static java.lang.Math.max;

public class ExternalClassLoader extends URLClassLoader {

    public static final Set<ExternalClassLoader> loaderPool = new HashSet<>();
    public static final ConcurrentHashMap<String, List<URL>> resources = new ConcurrentHashMap<>();
    public String name;

    public ExternalClassLoader(String name, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        loaderPool.add(this);
        this.name = name;
    }

    public ExternalClassLoader(String name, ClassLoader parent) {
        super(new URL[0], parent);
        loaderPool.add(this);
        this.name = name;
    }

    public ExternalClassLoader(String name) {
        super(new URL[0], ExternalClassLoader.class.getClassLoader());
        loaderPool.add(this);
        this.name = name;
    }

    private final HashMap<String, URL> classesCache = new HashMap<>();
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

    protected Class<?> findClass(String name, boolean deepScan) throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) return loaded;
        var clazz = loadClassFromURLCache(name);
        if (clazz != null) return clazz;
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

    @SuppressWarnings("CallToPrintStackTrace")
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return findClass(name, true);
        } catch (ClassNotFoundException ex) {
            // ex.printStackTrace();
            return super.findClass(name);
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> c;
        synchronized (getClassLoadingLock(name)) {
            c = findLoadedClass(name);
            if (c == null) {
                var clazz = loadClassFromURLCache(name);
                if (clazz != null) return clazz;
            }
        }
        return c == null ? super.loadClass(name, resolve) : c;
    }

    private Class<?> loadClassFromURLCache(String name) {
        URL url = classesCache.getOrDefault(name, null);
        if (url != null) {
            byte[] bytes = null;
            try {
                URLConnection connection = url.openConnection();
                // Closing a cached JarURLConnection stream alone leaves its JarFile globally retained.
                connection.setUseCaches(false);
                try (InputStream input = connection.getInputStream()) {
                    bytes = readBytes(input);
                }
            } catch (IOException ignored) {
                System.out.println("Failed to load class from URL. Class: " + name);
            }
            if (bytes != null) {
                // FastBootService.regClass(name, url.toString());
                return defineClass(name, bytes, 0, bytes.length);
            } else System.out.println("URL is empty! Class: " + name);
        }
        return null;
    }

    @Override
    @SuppressWarnings("ALL")
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> urlSet = new HashSet<>();
        // Find in this class loader
        URL resInCache = resourceCache.getOrDefault(name, null);
        if (resInCache != null) urlSet.add(resInCache);
        URL resInThis = super.findResource(name);
        if (resInThis != null) urlSet.add(resInThis);
        URL resInParent = getParent().getResource(name);
        if (resInParent != null) urlSet.add(resInParent);
        for (String path : resourcePaths) {
            URL res = findInPath(path, name);
            if (res != null) urlSet.add(res);
        }
        for (String sysPath : systemPaths) {
            URL res = findInPath(sysPath, name);
            if (res != null) urlSet.add(res);
        }
        for (String dummy : dummyPaths) {
            URL res = findInPath(dummy, name);
            if (res != null) urlSet.add(res);
        }
        // Deep scan other class loader
        ExternalClassLoader.resources.getOrDefault(name, new ArrayList<>()).forEach(url -> urlSet.add(url));
        return Collections.enumeration(urlSet);
    }

    @Override
    public URL findResource(String name) {
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
        // Deep scan in resource pool
        var list = ExternalClassLoader.resources.getOrDefault(name, new ArrayList<>());
        if (!list.isEmpty()) return list.getFirst();
        return null;
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
        // Enumerate the central directory without reading or inflating entry payloads.
        try (JarFile jar = new JarFile(file)) {
            Enumeration<? extends ZipEntry> entries = jar.entries();
            String jarUrl = "jar:" + file.toURI().toURL() + "!/";
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                URL url = new URL(jarUrl + entry.getName());
                if (entry.getName().toLowerCase().endsWith(".class")) {
                    classesCache.put(removeSuffix(entry.getName().replace("/", "."), ".class"), url);
                }
                // Cache everything except directory
                if (!entry.isDirectory()) {
                    resources.computeIfAbsent(entry.getName(), k -> {
                        var list = new ArrayList<URL>();
                        list.add(url);
                        return list;
                    });
                    resourceCache.put(entry.getName(), url);
                }
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