package com.syncro.core.crypto

import com.syncro.core.util.toHex
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyPair
import java.security.PrivateKey

/**
 * Long-term device identity. The device id is derived from the public key, so an id can never be
 * claimed by a different key; trust decisions additionally compare the full fingerprint.
 */
class DeviceIdentity private constructor(private val privateKey: PrivateKey, val publicKey: ByteArray) {

    val fingerprint: ByteArray = Crypto.sha256(publicKey)
    val deviceId: String = idFromFingerprint(fingerprint)
    val displayFingerprint: String get() = formatFingerprint(fingerprint)

    fun sign(data: ByteArray): ByteArray = Crypto.sign(privateKey, data)

    fun export(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            val encodedPrivate = privateKey.encoded
            out.writeShort(encodedPrivate.size)
            out.write(encodedPrivate)
            out.writeShort(publicKey.size)
            out.write(publicKey)
        }
        return bytes.toByteArray()
    }

    companion object {
        private const val MAGIC = 0x53594944 // "SYID"

        fun generate(): DeviceIdentity {
            val pair: KeyPair = Crypto.generateKeyPair()
            return DeviceIdentity(pair.private, Crypto.encodePublicKey(pair.public))
        }

        fun import(bytes: ByteArray): DeviceIdentity {
            DataInputStream(bytes.inputStream()).use { input ->
                require(input.readInt() == MAGIC) { "Not a Syncro identity" }
                val encodedPrivate = ByteArray(input.readUnsignedShort()).also(input::readFully)
                val publicKey = ByteArray(input.readUnsignedShort()).also(input::readFully)
                Crypto.decodePublicKey(publicKey)
                return DeviceIdentity(Crypto.decodePrivateKey(encodedPrivate), publicKey)
            }
        }

        /** Loads the identity stored in [file], creating (and persisting) a new one if missing or corrupt. */
        fun loadOrCreate(file: File): DeviceIdentity {
            if (file.isFile) {
                runCatching { return import(file.readBytes()) }
            }
            val identity = generate()
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeBytes(identity.export())
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            return identity
        }

        fun idFromFingerprint(fingerprint: ByteArray): String = fingerprint.copyOf(8).toHex()

        fun idForPublicKey(publicKey: ByteArray): String = idFromFingerprint(Crypto.sha256(publicKey))

        fun formatFingerprint(fingerprint: ByteArray): String =
            fingerprint.copyOf(12).toHex().uppercase().chunked(4).joinToString(" ")
    }
}
