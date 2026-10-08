package net.spartanb312.grunteon.backend;

/** Tiny isolated process for timeout/interruption/descendant cleanup tests; no obfuscator or services. */
public final class SleepingChild {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("probe")) {
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                    java.nio.file.Path.of(args[1]), java.nio.file.StandardOpenOption.READ, java.nio.file.StandardOpenOption.WRITE)) {
                java.nio.channels.FileLock lock = channel.tryLock();
                System.out.println(lock == null ? "locked" : "free");
                if (lock != null) lock.release();
            }
            return;
        }
        if (args.length > 0 && args[0].equals("tree")) {
            String executable = java.nio.file.Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            Process child = new ProcessBuilder(executable, "-Xmx16m", "-cp", System.getProperty("java.class.path"),
                    SleepingChild.class.getName()).start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                child.destroyForcibly();
                try { child.waitFor(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }));
            System.out.println(child.pid());
        } else System.out.println("ready");
        System.out.flush();
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
