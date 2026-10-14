package team.bjtuss.bjtuselfservice.shared.agent

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.File

/** Foundation's Trash API also works from our headless launcher. No permanent-delete fallback. */
internal fun moveMacAppToTrash(bundle: File, onTrashed: (File) -> Unit = {}): Boolean = runCatching {
    NativeLibrary.getInstance("/System/Library/Frameworks/Foundation.framework/Foundation")
    val objc = NativeLibrary.getInstance("objc")
    val send = objc.getFunction("objc_msgSend")
    fun cls(name: String): Pointer = objc.getFunction("objc_getClass").invokePointer(arrayOf(name))
    fun sel(name: String): Pointer = objc.getFunction("sel_registerName").invokePointer(arrayOf(name))
    fun ptr(receiver: Pointer, method: String, vararg args: Any?): Pointer =
        send.invokePointer(arrayOf(receiver, sel(method), *args))
    val pool = ptr(cls("NSAutoreleasePool"), "new")
    try {
        val bytes = (bundle.absolutePath + "\u0000").toByteArray(Charsets.UTF_8)
        val path = Memory(bytes.size.toLong()).also { it.write(0, bytes, 0, bytes.size) }
        val string = ptr(cls("NSString"), "stringWithUTF8String:", path)
        val url = ptr(cls("NSURL"), "fileURLWithPath:", string)
        val manager = ptr(cls("NSFileManager"), "defaultManager")
        val destination = PointerByReference()
        val moved = send.invokeInt(arrayOf(manager, sel("trashItemAtURL:resultingItemURL:error:"), url, destination, PointerByReference())) != 0
        if (moved && destination.value != null) {
            val destinationPath = ptr(destination.value, "path")
            onTrashed(File(ptr(destinationPath, "UTF8String").getString(0, "UTF-8")))
        }
        moved
    } finally { send.invokeVoid(arrayOf(pool, sel("drain"))) }
}.getOrDefault(false)
