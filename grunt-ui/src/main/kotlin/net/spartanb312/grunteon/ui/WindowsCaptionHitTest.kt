package net.spartanb312.grunteon.ui

import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.lang.foreign.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

object WindowsCaptionHitTest {
    val isSupported: Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)

    val isAvailable: Boolean
        get() = isSupported && runCatching {
            user32.find("GetAncestor").isPresent &&
                user32.find("GetWindowLongPtrW").isPresent &&
                user32.find("SetWindowLongPtrW").isPresent &&
                user32.find("CallWindowProcW").isPresent &&
                user32.find("ClientToScreen").isPresent &&
                user32.find("SendMessageW").isPresent &&
                user32.find("GetWindowRect").isPresent
        }.getOrDefault(false)

    private data class CaptionArea(
        val rootHwnd: Long,
        val height: Int,
        val startInset: Int,
        val endInset: Int,
    )

    private const val GWLP_WNDPROC = -4
    private const val WM_NCHITTEST = 0x0084
    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val WM_NCLBUTTONDBLCLK = 0x00A3
    private const val WM_LBUTTONDOWN = 0x0201
    private const val WM_LBUTTONDBLCLK = 0x0203
    private const val GA_ROOT = 2
    private const val HTCAPTION = 2L

    private const val RECT_SIZE = 16L
    private const val RECT_LEFT = 0L
    private const val RECT_TOP = 4L
    private const val RECT_RIGHT = 8L

    private val arena: Arena = Arena.global()
    private val linker: Linker by lazy { Linker.nativeLinker() }
    private val user32: SymbolLookup by lazy { SymbolLookup.libraryLookup("user32", arena) }

    private val originalWndProcs = ConcurrentHashMap<Long, Long>()
    private val captionAreas = ConcurrentHashMap<Long, CaptionArea>()
    private val loggedCaptionHits = ConcurrentHashMap.newKeySet<Long>()

    private val wndProcDescriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG
    )

    private val wndProcStub by lazy {
        val handle = MethodHandles.lookup().findStatic(
            WindowsCaptionHitTest::class.java,
            "windowProc",
            MethodType.methodType(
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            )
        )
        linker.upcallStub(handle, wndProcDescriptor, arena)
    }

    private val getAncestor by lazy {
        linker.downcallHandle(
            user32.find("GetAncestor").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
        )
    }

    private val setWindowLongPtrW by lazy {
        linker.downcallHandle(
            user32.find("SetWindowLongPtrW").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG
            )
        )
    }

    private val callWindowProcW by lazy {
        linker.downcallHandle(
            user32.find("CallWindowProcW").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG
            )
        )
    }

    private val getWindowRect by lazy {
        linker.downcallHandle(
            user32.find("GetWindowRect").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        )
    }

    private val clientToScreen by lazy {
        linker.downcallHandle(
            user32.find("ClientToScreen").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        )
    }

    private val sendMessageW by lazy {
        linker.downcallHandle(
            user32.find("SendMessageW").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG
            )
        )
    }

    fun installOrUpdate(
        window: Window,
        captionHeight: Int,
        captionStartInset: Int,
        captionEndInset: Int,
    ): Long {
        if (!isAvailable) {
            println("WindowsCaptionHitTest: unavailable")
            return 0L
        }

        val skiaLayer = findSkiaLayer(window)
        if (skiaLayer == null) {
            println("WindowsCaptionHitTest: SkiaLayer not found")
            return 0L
        }

        val layerHwnd = skiaLayer.windowHandle
        val contentHwnd = skiaLayer.contentHandle
        val hwnd = rootHwnd(layerHwnd)
        if (hwnd == 0L) {
            println("WindowsCaptionHitTest: zero hwnd from layer hwnd=0x${layerHwnd.toString(16)}")
            return 0L
        }

        val captionArea = CaptionArea(
            rootHwnd = hwnd,
            height = captionHeight.coerceAtLeast(0),
            startInset = captionStartInset.coerceAtLeast(0),
            endInset = captionEndInset.coerceAtLeast(0),
        )
        captionAreas[hwnd] = captionArea
        if (contentHwnd != 0L && contentHwnd != hwnd) {
            captionAreas[contentHwnd] = captionArea
        }

        if (!installWndProc(hwnd)) {
            captionAreas.remove(hwnd)
            captionAreas.remove(contentHwnd)
            return 0L
        }
        if (contentHwnd != 0L && contentHwnd != hwnd && !installWndProc(contentHwnd)) {
            captionAreas.remove(contentHwnd)
        }

        println(
            "WindowsCaptionHitTest: handles root=0x${hwnd.toString(16)} " +
                "layer=0x${layerHwnd.toString(16)} content=0x${contentHwnd.toString(16)}"
        )
        return hwnd
    }

    fun uninstall(hwnd: Long) {
        if (hwnd == 0L) return

        val handles = captionAreas.entries
            .filter { it.value.rootHwnd == hwnd }
            .map { it.key }
        handles.forEach { handle ->
            captionAreas.remove(handle)
            loggedCaptionHits.remove(handle)
            originalWndProcs.remove(handle)?.let { originalProc ->
                runCatching {
                    setWindowLongPtrW.invoke(handle, GWLP_WNDPROC, originalProc)
                }
            }
        }
    }

    private fun installWndProc(hwnd: Long): Boolean {
        if (originalWndProcs.containsKey(hwnd)) return true

        val originalProc = setWindowLongPtrW.invoke(hwnd, GWLP_WNDPROC, wndProcStub.address()) as Long
        if (originalProc == 0L) {
            println("WindowsCaptionHitTest: SetWindowLongPtrW failed hwnd=0x${hwnd.toString(16)}")
            return false
        }
        originalWndProcs[hwnd] = originalProc
        return true
    }

    private fun rootHwnd(hwnd: Long): Long {
        if (hwnd == 0L) return 0L
        val root = getAncestor.invoke(hwnd, GA_ROOT) as Long
        return root.takeIf { it != 0L } ?: hwnd
    }

    @JvmStatic
    private fun windowProc(hwnd: Long, msg: Int, wParam: Long, lParam: Long): Long {
        val captionArea = captionAreas[hwnd]
        if (msg == WM_NCHITTEST && isInCaptionArea(hwnd, lParam)) {
            if (loggedCaptionHits.add(hwnd)) {
                println("WindowsCaptionHitTest: HTCAPTION hit hwnd=0x${hwnd.toString(16)}")
            }
            return HTCAPTION
        }
        if (
            (msg == WM_NCLBUTTONDOWN || msg == WM_NCLBUTTONDBLCLK) &&
            captionArea != null &&
            hwnd != captionArea.rootHwnd &&
            wParam == HTCAPTION
        ) {
            println(
                "WindowsCaptionHitTest: forwarding non-client click " +
                    "from=0x${hwnd.toString(16)} to=0x${captionArea.rootHwnd.toString(16)}"
            )
            sendMessageW.invoke(captionArea.rootHwnd, msg, wParam, lParam)
            return 0L
        }
        if ((msg == WM_LBUTTONDOWN || msg == WM_LBUTTONDBLCLK) && isClientPointInCaptionArea(hwnd, lParam)) {
            val rootHwnd = captionArea?.rootHwnd ?: return callOriginalWindowProc(hwnd, msg, wParam, lParam)
            val nonClientMessage = if (msg == WM_LBUTTONDBLCLK) WM_NCLBUTTONDBLCLK else WM_NCLBUTTONDOWN
            val screenPoint = clientPointToScreen(hwnd, lParam)
            println(
                "WindowsCaptionHitTest: forwarding ${if (msg == WM_LBUTTONDBLCLK) "double click" else "click"} " +
                    "from=0x${hwnd.toString(16)} to=0x${rootHwnd.toString(16)}"
            )
            sendMessageW.invoke(rootHwnd, nonClientMessage, HTCAPTION, screenPoint)
            return 0L
        }
        return callOriginalWindowProc(hwnd, msg, wParam, lParam)
    }

    private fun callOriginalWindowProc(hwnd: Long, msg: Int, wParam: Long, lParam: Long): Long {
        val originalProc = originalWndProcs[hwnd] ?: return 0L
        return callWindowProcW.invoke(originalProc, hwnd, msg, wParam, lParam) as Long
    }

    private fun isInCaptionArea(hwnd: Long, lParam: Long): Boolean {
        val captionArea = captionAreas[hwnd] ?: return false
        if (captionArea.height == 0) return false

        Arena.ofConfined().use { localArena ->
            val rect = localArena.allocate(RECT_SIZE)
            if ((getWindowRect.invoke(captionArea.rootHwnd, rect) as Int) == 0) return false

            val localX = signedLowWord(lParam) - rect.get(ValueLayout.JAVA_INT, RECT_LEFT)
            val localY = signedHighWord(lParam) - rect.get(ValueLayout.JAVA_INT, RECT_TOP)
            val width = rect.get(ValueLayout.JAVA_INT, RECT_RIGHT) - rect.get(ValueLayout.JAVA_INT, RECT_LEFT)
            val dragEnd = width - captionArea.endInset

            return localY in 0 until captionArea.height &&
                localX >= captionArea.startInset &&
                localX < dragEnd
        }
    }

    private fun isClientPointInCaptionArea(hwnd: Long, lParam: Long): Boolean {
        return isInCaptionArea(hwnd, clientPointToScreen(hwnd, lParam))
    }

    private fun clientPointToScreen(hwnd: Long, lParam: Long): Long {
        Arena.ofConfined().use { localArena ->
            val point = localArena.allocate(ValueLayout.JAVA_INT.byteSize() * 2)
            point.set(ValueLayout.JAVA_INT, 0, signedLowWord(lParam))
            point.set(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT.byteSize(), signedHighWord(lParam))
            if ((clientToScreen.invoke(hwnd, point) as Int) == 0) return lParam
            return packPoint(
                point.get(ValueLayout.JAVA_INT, 0),
                point.get(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT.byteSize())
            )
        }
    }

    private fun packPoint(x: Int, y: Int): Long {
        return (x.toLong() and 0xFFFFL) or ((y.toLong() and 0xFFFFL) shl 16)
    }

    private fun findSkiaLayer(component: Component): SkiaLayer? {
        if (component is SkiaLayer) return component
        if (component is Container) {
            component.components.forEach { child ->
                findSkiaLayer(child)?.let { return it }
            }
        }
        return null
    }

    private fun signedLowWord(value: Long): Int {
        return value.toInt().toShort().toInt()
    }

    private fun signedHighWord(value: Long): Int {
        return (value ushr 16).toInt().toShort().toInt()
    }
}
