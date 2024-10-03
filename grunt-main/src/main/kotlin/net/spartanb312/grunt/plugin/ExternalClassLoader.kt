package net.spartanb312.grunt.plugin

import net.spartanb312.grunt.utils.Platform
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Modifier
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.*
import java.util.zip.ZipInputStream

open class ExternalClassLoader : URLClassLoader(arrayOf(), ExternalClassLoader::class.java.classLoader) {

    private val classesCache = mutableMapOf<String, ByteArray>()
    private val resourceCache = mutableMapOf<String, URL>()
    private val classesURLs = mutableMapOf<String, URL>()
    private val resourcePaths = mutableSetOf<String>()
    private val systemPaths = System.getProperty("java.library.path").split(";".toRegex())
    private val dummyPaths = arrayOf("C:\\Windows\\System\\", "C:\\Windows\\System32\\")

    fun addURLs(vararg urls: URL?) {
        for (url in urls) {
            this.addURL(url)
        }
    }

    fun addPath(path: String) {
        resourcePaths.add(path)
    }

    fun removePath(path: String) {
        resourcePaths.remove(path)
    }

    override fun findClass(name: String): Class<*> {
        return try {
            super.findClass(name)
        } catch (exception: Exception) {
            val bytes = classesCache.getOrDefault(name, null)
            if (bytes != null) defineClass(name, bytes, 0, bytes.size)
            else throw ClassNotFoundException("Can't find class $name")
        }
    }

    override fun findResource(name: String): URL? {
        // Find in resource url caches
        val resInCache = resourceCache.getOrDefault(name, null)
        if (resInCache != null) return resInCache

        // Find in this classLoader
        val resInThis = super.findResource(name)
        if (resInThis != null) return resInThis

        // Find in parent classLoader
        val resInParent = parent.getResource(name)
        if (resInParent != null) return resInParent

        // Find external resources
        for (path in resourcePaths) {
            val res = findInPath(path, name)
            if (res != null) return res
        }

        // Find in system
        for (sysPath in systemPaths) {
            val res = findInPath(sysPath, name)
            if (res != null) return res
        }

        // Dummy stuff
        for (dummy in dummyPaths) {
            val res = findInPath(dummy, name)
            if (res != null) return res
        }
        return null
    }

    fun loadResource(file: File): URL {
        val url = file.toURI().toURL()
        resourceCache[file.toString().replace("\\", "/")] = url
        return url
    }

    fun loadJar(file: String) {
        try {
            loadJar(File(file))
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }

    fun loadJar(file: File) {
        val zip = ZipInputStream(Files.newInputStream(file.toPath()))
        while (true) {
            val entry = zip.nextEntry
            if (entry == null) break
            else if (entry.name.lowercase(Locale.getDefault()).endsWith(".class")) {
                classesCache[entry.name.replace("/", ".").removeSuffix(".class")] = zip.readBytes()
                val classURL = URL(
                    ("jar:file:"
                            + (if (Platform.platform.os == Platform.OS.Linux) "" else "/")
                            + file.absolutePath.replace("\\", "/")
                            + "!/" + entry.name)
                )
                classesURLs[entry.name] = classURL
            } else {
                val resourceURL = URL(
                    ("jar:file:"
                            + (if (Platform.platform.os == Platform.OS.Linux) "" else "/")
                            + file.absolutePath.replace("\\", "/")
                            + "!/" + entry.name)
                )
                resourceCache[entry.name] = resourceURL
            }
        }
    }

    @Throws(IOException::class)
    fun loadJar(inputStream: InputStream) {
        val temp = File.createTempFile("grunt-temp", ".jar")
        temp.deleteOnExit()
        val outputStream = FileOutputStream(temp)
        try {
            outputStream.write(inputStream.readBytes())
        } finally {
            outputStream.flush()
            outputStream.close()
        }
        loadJar(temp)
    }

    private fun findInPath(path: String, name: String): URL? {
        val adjustedPath = path.removeSuffix("/").removeSuffix("\\")
        val file = File("$adjustedPath/$name")
        return if (file.exists()) try {
            file.toURI().toURL()
        } catch (e: MalformedURLException) {
            null
        }
        else null
    }

    fun initKotlinObject(name: String): Class<*> {
        val clazz = loadClass(name)
        getInstanceField(clazz)
        return clazz
    }

    fun getInstanceField(clazz: Class<*>): Any? {
        val fields = clazz.declaredFields
        for (field in fields) {
            if (Modifier.isStatic(field.modifiers) && field.name == "INSTANCE") {
                return field[null]
            }
        }
        return null
    }

}