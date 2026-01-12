package com.example.syncro

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec



object LocalCrypto {

    fun generateECDHKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(256)
        return gen.generateKeyPair()
    }

    fun deriveAESKey(
        privateKey: java.security.PrivateKey,
        publicKey: java.security.PublicKey
    ): SecretKey {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(publicKey, true)

        val sharedSecret = ka.generateSecret()
        val hash = MessageDigest.getInstance("SHA-256")

        return SecretKeySpec(hash.digest(sharedSecret), "AES")
    }

    fun encrypt(
        data: ByteArray,
        key: SecretKey
    ): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Pair(cipher.doFinal(data), cipher.iv)
    }

    fun decrypt(
        data: ByteArray,
        key: SecretKey,
        iv: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, iv)
        )
        return cipher.doFinal(data)
    }
}
