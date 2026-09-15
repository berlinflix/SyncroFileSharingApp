package com.syncro.core

import com.syncro.core.crypto.Crypto
import com.syncro.core.crypto.DeviceIdentity
import com.syncro.core.link.ConnectLink
import com.syncro.core.util.FileNames
import com.syncro.core.util.hexToBytes
import com.syncro.core.util.toHex
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoTest {
    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToBytes()
        val salt = "000102030405060708090a0b0c".hexToBytes()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToBytes()
        val prk = Crypto.hkdfExtract(salt, ikm)
        assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", prk.toHex())
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            Crypto.hkdfExpand(prk, info, 42).toHex(),
        )
    }

    @Test
    fun publicKeyEncodingRoundTripsAndRejectsInvalidPoints() {
        val pair = Crypto.generateKeyPair()
        val encoded = Crypto.encodePublicKey(pair.public)
        assertEquals(91, encoded.size)
        assertTrue(encoded.contentEquals(Crypto.encodePublicKey(Crypto.decodePublicKey(encoded))))

        val tampered = encoded.copyOf().also { it[70] = (it[70].toInt() xor 1).toByte() }
        assertFailsWith<GeneralSecurityException> { Crypto.decodePublicKey(tampered) }
    }

    @Test
    fun ecdhAgreesAndSignaturesVerify() {
        val a = Crypto.generateKeyPair()
        val b = Crypto.generateKeyPair()
        assertTrue(Crypto.ecdh(a.private, b.public).contentEquals(Crypto.ecdh(b.private, a.public)))

        val identity = DeviceIdentity.generate()
        val restored = DeviceIdentity.import(identity.export())
        assertEquals(identity.deviceId, restored.deviceId)
        val signature = restored.sign("hello".toByteArray())
        val key = Crypto.decodePublicKey(identity.publicKey)
        assertTrue(Crypto.verify(key, "hello".toByteArray(), signature))
        assertFalse(Crypto.verify(key, "hellO".toByteArray(), signature))
    }

    @Test
    fun connectLinkRoundTrip() {
        val link = ConnectLink("0123456789abcdef", "Suyash's PC & laptop", DeviceType.DESKTOP, listOf("192.168.1.20", "10.0.0.4"), 47821)
        assertEquals(link, ConnectLink.parse(link.toUri()))
        assertNull(ConnectLink.parse("https://example.com"))
        assertNull(ConnectLink.parse("syncro://connect?id=zz&h=1.2.3.4"))
        assertNotNull(ConnectLink.parse("syncro://connect?id=0123456789ABCDEF&h=1.2.3.4"))
    }

    @Test
    fun fileNamesAreSanitized() {
        assertEquals("passwd", FileNames.sanitize("../../etc/passwd"))
        assertEquals("evil.exe", FileNames.sanitize("C:\\Windows\\evil.exe"))
        assertEquals("_CON.txt", FileNames.sanitize("CON.txt"))
        assertEquals("a_b_c", FileNames.sanitize("a<b>c"))
        assertEquals("file", FileNames.sanitize(".."))
        assertEquals("photo (2).jpg", FileNames.unique("photo.jpg") { it == "photo.jpg" || it == "photo (1).jpg" })
    }
}
