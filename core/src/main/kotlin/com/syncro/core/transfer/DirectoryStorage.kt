package com.syncro.core.transfer

import com.syncro.core.DeviceInfo
import com.syncro.core.util.FileNames
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/** Saves received files into a directory, writing to a hidden `.part` file and renaming on success. */
class DirectoryStorage(private val directory: () -> File) : ReceiveStorage {

    override fun availableBytes(): Long {
        val dir = directory().also { it.mkdirs() }
        return dir.usableSpace.takeIf { it > 0 } ?: Long.MAX_VALUE
    }

    override fun open(transferId: String, peer: DeviceInfo, item: TransferItemInfo): IncomingFile {
        val dir = directory()
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create ${dir.absolutePath}")
        val safeName = FileNames.sanitize(item.name)
        val partial = File(dir, ".$safeName.${transferId.take(8)}.syncro-part")
        return PartialFile(dir, safeName, partial)
    }

    private class PartialFile(private val dir: File, private val name: String, private val partial: File) : IncomingFile {
        override val output: OutputStream = BufferedOutputStream(FileOutputStream(partial), 256 * 1024)

        override fun commit(): String {
            output.close()
            synchronized(LOCK) {
                val target = FileNames.uniqueFile(dir, name)
                if (!partial.renameTo(target)) {
                    partial.copyTo(target)
                    partial.delete()
                }
                return target.absolutePath
            }
        }

        override fun abort() {
            runCatching { output.close() }
            partial.delete()
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
