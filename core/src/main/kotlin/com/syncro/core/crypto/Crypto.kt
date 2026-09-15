package com.syncro.core.crypto

import com.syncro.core.util.hexToBytes
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Primitive wrappers built only on JCA algorithms that exist on both Android (API 26+) and desktop JVMs:
 * NIST P-256 ECDH/ECDSA, SHA-256, HMAC-SHA256 (HKDF) and AES-256-GCM.
 */
object Crypto {
    private val random = SecureRandom()

    /** DER prefix of a SubjectPublicKeyInfo for an uncompressed P-256 point. */
    private val P256_SPKI_PREFIX = "3059301306072a8648ce3d020106082a8648ce3d030107034200".hexToBytes()
    private const val PUBLIC_KEY_SIZE = 91
    private val P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
    private val B = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)
    private val THREE = BigInteger.valueOf(3)

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach(digest::update)
        return digest.digest()
    }

    fun hmacSha256(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach(mac::update)
        return mac.doFinal()
    }

    /** HKDF-Extract (RFC 5869). */
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray =
        hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)

    /** HKDF-Expand (RFC 5869). */
    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32)
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var position = 0
        var counter = 1
        while (position < length) {
            previous = hmacSha256(prk, previous, info, byteArrayOf(counter.toByte()))
            val n = minOf(previous.size, length - position)
            System.arraycopy(previous, 0, out, position, n)
            position += n
            counter++
        }
        return out
    }

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"), random)
            generateKeyPair()
        }

    /** Canonical 91-byte X.509 encoding, identical regardless of the security provider that made the key. */
    fun encodePublicKey(key: PublicKey): ByteArray {
        val point = (key as ECPublicKey).w
        val out = ByteArray(PUBLIC_KEY_SIZE)
        System.arraycopy(P256_SPKI_PREFIX, 0, out, 0, P256_SPKI_PREFIX.size)
        out[P256_SPKI_PREFIX.size] = 0x04
        writeCoordinate(point.affineX, out, P256_SPKI_PREFIX.size + 1)
        writeCoordinate(point.affineY, out, P256_SPKI_PREFIX.size + 33)
        return out
    }

    /** Decodes a canonical P-256 key and checks that the point is on the curve (invalid-curve protection). */
    fun decodePublicKey(bytes: ByteArray): PublicKey {
        if (bytes.size != PUBLIC_KEY_SIZE) throw GeneralSecurityException("Bad public key length")
        for (i in P256_SPKI_PREFIX.indices) {
            if (bytes[i] != P256_SPKI_PREFIX[i]) throw GeneralSecurityException("Unsupported public key")
        }
        if (bytes[P256_SPKI_PREFIX.size] != 0x04.toByte()) throw GeneralSecurityException("Compressed keys unsupported")
        val x = BigInteger(1, bytes.copyOfRange(27, 59))
        val y = BigInteger(1, bytes.copyOfRange(59, 91))
        if (x >= P || y >= P) throw GeneralSecurityException("Coordinate out of range")
        val lhs = y.multiply(y).mod(P)
        val rhs = x.pow(3).subtract(x.multiply(THREE)).add(B).mod(P)
        if (lhs != rhs) throw GeneralSecurityException("Point is not on P-256")
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun decodePrivateKey(bytes: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))

    fun ecdh(privateKey: PrivateKey, publicKey: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }

    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }

    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean =
        try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(data)
                verify(signature)
            }
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    /**
     * On HotSpot JVMs AES-GCM only reaches hardware-accelerated speed once the JIT has compiled the cipher
     * with its intrinsics. Many small operations get there far sooner than a first multi-hundred-megabyte
     * transfer would, so desktop apps call this once in the background at startup.
     */
    fun warmUp(iterations: Int = 3_000) {
        val key = javax.crypto.spec.SecretKeySpec(randomBytes(32), "AES")
        val plain = ByteArray(16 * 1024)
        val sealed = ByteArray(plain.size + 16)
        val opened = ByteArray(plain.size + 16)
        val encrypt = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val decrypt = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12)
        for (i in 0 until iterations) {
            nonce[8] = (i ushr 24).toByte(); nonce[9] = (i ushr 16).toByte(); nonce[10] = (i ushr 8).toByte(); nonce[11] = i.toByte()
            val spec = javax.crypto.spec.GCMParameterSpec(128, nonce.copyOf())
            encrypt.init(javax.crypto.Cipher.ENCRYPT_MODE, key, spec)
            val n = encrypt.doFinal(plain, 0, plain.size, sealed, 0)
            decrypt.init(javax.crypto.Cipher.DECRYPT_MODE, key, spec)
            decrypt.doFinal(sealed, 0, n, opened, 0)
        }
    }

    private fun writeCoordinate(value: BigInteger, out: ByteArray, offset: Int) {
        val raw = value.toByteArray()
        val trimmed = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
        System.arraycopy(trimmed, 0, out, offset + 32 - trimmed.size, trimmed.size)
    }
}
