package net.spartanb312.everett.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Standalone regression harness; this Java-only module does not depend on a test framework. */
public final class ExternalClassLoaderRegressionTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("bootstrap-loader-test-");
        try {
            indexesCentralDirectoryWithoutInflating(directory);
            preservesClassResourceAndPeerLookup(directory);
            closesClassStreamsOnSuccessAndReadFailure();
            assertNoOpenFiles(directory);
            System.out.println("ExternalClassLoaderRegressionTest: 3 tests passed");
        } finally {
            ExternalClassLoader.loaderPool.clear();
            ExternalClassLoader.resources.clear();
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void indexesCentralDirectoryWithoutInflating(Path directory) throws Exception {
        Path archive = directory.resolve("corrupt-payload.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new JarEntry("large-resource.dat"));
            byte[] block = new byte[8192];
            for (int i = 0; i < 1024; i++) output.write(block);
            output.closeEntry();
            output.putNextEntry(new JarEntry("other.txt"));
            output.write(42);
            output.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(archive);
        int nameLength = (bytes[26] & 255) | ((bytes[27] & 255) << 8);
        int extraLength = (bytes[28] & 255) | ((bytes[29] & 255) << 8);
        bytes[30 + nameLength + extraLength] = 6; // Reserved DEFLATE block type; central directory remains valid.
        Files.write(archive, bytes);
        boolean corrupt = false;
        try (JarFile jar = new JarFile(archive.toFile()); InputStream input = jar.getInputStream(jar.getJarEntry("large-resource.dat"))) {
            input.read();
        } catch (IOException expected) {
            corrupt = true;
        }
        check(corrupt, "Fixture must fail when its payload is inflated");
        for (int i = 0; i < 64; i++) {
            try (ExternalClassLoader loader = new ExternalClassLoader("central-directory")) {
                loader.loadJar(archive.toFile());
                check(loader.findResource("large-resource.dat") != null, "Missing resource metadata");
                check(loader.findResource("other.txt") != null, "Enumeration inflated the previous entry");
                assertNoOpenFiles(directory);
                ExternalClassLoader.loaderPool.remove(loader);
            }
        }
    }

    private static void preservesClassResourceAndPeerLookup(Path directory) throws Exception {
        Path archive = directory.resolve("space # ü.jar");
        String classPath = Payload.class.getName().replace('.', '/') + ".class";
        byte[] classBytes = payloadBytes();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new JarEntry("data/"));
            output.closeEntry();
            output.putNextEntry(new JarEntry(classPath));
            output.write(classBytes);
            output.closeEntry();
            output.putNextEntry(new JarEntry("data/message.txt"));
            output.write("message".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (ExternalClassLoader owner = new ExternalClassLoader("owner");
             ExternalClassLoader peer = new ExternalClassLoader("peer")) {
            owner.loadJar(archive.toFile());
            Class<?> loaded = owner.loadClass(Payload.class.getName());
            check(loaded != Payload.class && loaded.getClassLoader() == owner, "Cached classes must remain child-first");
            check("payload".equals(loaded.getMethod("value").invoke(null)), "Incorrect class bytes");
            check(owner.loadClass("java.lang.String") == String.class, "Parent delegation changed");
            check(peer.findClass(Payload.class.getName()) == loaded, "Peer lookup changed");
            check(owner.findResource("data/") == null, "Directory must not be cached as a resource");
            URL resource = owner.findResource("data/message.txt");
            check(resource != null, "Missing cached resource");
            URLConnection connection = resource.openConnection();
            connection.setUseCaches(false);
            try (InputStream input = connection.getInputStream()) {
                check("message".equals(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)), "Resource content changed");
            }
            check(peer.findResource("data/message.txt").equals(resource), "Shared resource lookup changed");
            check(owner.getResources("data/message.txt").hasMoreElements(), "Resource enumeration changed");
            assertNoOpenFiles(directory);
            ExternalClassLoader.loaderPool.remove(owner);
            ExternalClassLoader.loaderPool.remove(peer);
        }
    }

    private static void closesClassStreamsOnSuccessAndReadFailure() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            AtomicInteger opened = new AtomicInteger();
            AtomicInteger closed = new AtomicInteger();
            byte[] bytes = payloadBytes();
            URL url = new URL(null, "fixture:class", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL target) {
                    return new URLConnection(target) {
                        @Override public void connect() { }
                        @Override public InputStream getInputStream() {
                            check(!getUseCaches(), "Class connections must not retain a globally cached JarFile");
                            opened.incrementAndGet();
                            return new FilterInputStream(new ByteArrayInputStream(bytes)) {
                                @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                                    if (fail) throw new IOException("Fixture read failure");
                                    return super.read(buffer, offset, length);
                                }
                                @Override public void close() throws IOException {
                                    closed.incrementAndGet();
                                    super.close();
                                }
                            };
                        }
                    };
                }
            });
            try (ExternalClassLoader loader = new ExternalClassLoader("stream-lifetime")) {
                String name = fail ? "missing.fixture.Type" : Payload.class.getName();
                cacheClass(loader, name, url);
                try {
                    Class<?> result = loader.loadClass(name);
                    check(!fail && result.getClassLoader() == loader, "Unexpected class loading result");
                } catch (ClassNotFoundException expected) {
                    check(fail, "Valid class failed to load");
                }
                check(opened.get() > 0 && opened.get() == closed.get(), "Class stream was leaked");
                ExternalClassLoader.loaderPool.remove(loader);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void cacheClass(ExternalClassLoader loader, String name, URL url) throws Exception {
        Field field = ExternalClassLoader.class.getDeclaredField("classesCache");
        field.setAccessible(true);
        ((Map<String, URL>) field.get(loader)).put(name, url);
    }

    private static byte[] payloadBytes() throws IOException {
        try (InputStream input = Payload.class.getResourceAsStream("/" + Payload.class.getName().replace('.', '/') + ".class")) {
            return input.readAllBytes();
        }
    }

    private static void assertNoOpenFiles(Path directory) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        if (!Files.isDirectory(descriptors)) return;
        try (var files = Files.list(descriptors)) {
            for (Path descriptor : files.toList()) {
                Path target;
                try { target = Files.readSymbolicLink(descriptor); }
                catch (IOException vanished) { continue; }
                check(!target.startsWith(directory), "Leaked archive descriptor: " + target);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static class Payload {
        public static String value() { return "payload"; }
    }
}
