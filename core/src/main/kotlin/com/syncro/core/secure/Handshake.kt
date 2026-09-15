package com.syncro.core.secure

import com.syncro.core.DeviceInfo
import com.syncro.core.DeviceType
import com.syncro.core.ProtocolException
import com.syncro.core.SecurityException
import com.syncro.core.Syncro
import com.syncro.core.crypto.Crypto
import com.syncro.core.crypto.DeviceIdentity
import com.syncro.core.sanitizeDeviceName
import com.syncro.core.transport.Connection
import com.syncro.core.util.fromBase64
import com.syncro.core.util.lengthPrefixed
import com.syncro.core.util.toBase64
import kotlinx.serialization.SerializationException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.PublicKey

/** An authenticated, encrypted session with a peer whose identity key has been verified. */
class SecureSession internal constructor(
    val connection: Connection,
    val channel: SecureChannel,
    val peer: DeviceInfo,
    val peerPublicKey: ByteArray,
    /** 4-digit code derived from the session keys. Both screens show the same PIN only without a MITM. */
    val pin: String,
) {
    val peerFingerprint: ByteArray = Crypto.sha256(peerPublicKey)
}

/**
 * Mutually authenticated key exchange with a hash commitment (in the spirit of UKEY2):
 *
 * 1. C → S  ClientInit { commit = SHA-256(ClientKey) }
 * 2. S → C  ServerInit { ephemeral S, nonce, identity key, device }
 * 3. C → S  ClientKey  { ephemeral C, nonce, identity key, device }   (S checks the commitment)
 * 4. T = SHA-256(ClientInit || ServerInit || ClientKey); secret = ECDH(ephC, ephS)
 *    keys/PIN = HKDF(salt = T, secret)
 * 5. Encrypted: C → S Auth{ ECDSA(idC, "C" || T) }, S → C Auth{ ECDSA(idS, "S" || T) }
 *
 * The commitment stops a man-in-the-middle from grinding ephemeral keys until both PINs match,
 * so a 4-digit PIN gives a 1-in-10,000 chance per attempt. Ephemeral keys give forward secrecy.
 */
object Handshake {
    private const val MAX_HANDSHAKE_FRAME = 16 * 1024
    private val AUTH_CLIENT = "syncro-v1 auth client".toByteArray()
    private val AUTH_SERVER = "syncro-v1 auth server".toByteArray()

    fun client(connection: Connection, identity: DeviceIdentity, self: DeviceInfo): SecureSession {
        val wire = Wire(connection)
        val ephemeral = Crypto.generateKeyPair()
        val clientKey = encode(
            ClientKey.serializer(),
            ClientKey(
                eph = Crypto.encodePublicKey(ephemeral.public).toBase64(),
                nonce = Crypto.randomBytes(32).toBase64(),
                key = identity.publicKey.toBase64(),
                device = self.toWire(),
            ),
        )
        val clientInit = encode(
            ClientInit.serializer(),
            ClientInit(Syncro.PROTOCOL, Syncro.PROTOCOL_VERSION, Crypto.sha256(clientKey).toBase64()),
        )
        wire.writePlain(clientInit)

        val serverInitBytes = wire.readPlain(MAX_HANDSHAKE_FRAME)
        val serverInit = decode(ServerInit.serializer(), serverInitBytes)
        if (serverInit.proto != Syncro.PROTOCOL) throw ProtocolException("Not a Syncro device")
        if (serverInit.v != Syncro.PROTOCOL_VERSION) throw ProtocolException("Unsupported protocol version ${serverInit.v}")
        val serverEphemeral = publicKey(serverInit.eph)
        val serverIdentityKey = serverInit.key.b64()
        val server = verifiedDevice(serverInit.device, serverIdentityKey)

        wire.writePlain(clientKey)

        val transcript = Crypto.sha256(lengthPrefixed(clientInit, serverInitBytes, clientKey))
        val keys = deriveKeys(Crypto.ecdh(ephemeral.private, serverEphemeral), transcript)
        val channel = SecureChannel(wire, keys.clientToServer, keys.serverToClient, connection::close)

        channel.sendControl(Msg.Auth(identity.sign(AUTH_CLIENT + transcript).toBase64()))
        val auth = channel.receiveControl() as? Msg.Auth ?: throw ProtocolException("Expected authentication")
        if (!Crypto.verify(publicKey(serverInit.key), AUTH_SERVER + transcript, auth.sig.b64())) {
            throw SecurityException("Device failed to prove its identity")
        }
        return SecureSession(connection, channel, server, serverIdentityKey, keys.pin)
    }

    fun server(connection: Connection, identity: DeviceIdentity, self: DeviceInfo): SecureSession {
        val wire = Wire(connection)
        val clientInitBytes = wire.readPlain(MAX_HANDSHAKE_FRAME)
        val clientInit = decode(ClientInit.serializer(), clientInitBytes)
        if (clientInit.proto != Syncro.PROTOCOL) throw ProtocolException("Not a Syncro client")
        if (clientInit.v != Syncro.PROTOCOL_VERSION) throw ProtocolException("Unsupported protocol version ${clientInit.v}")
        val commitment = clientInit.commit.b64()

        val ephemeral = Crypto.generateKeyPair()
        val serverInit = encode(
            ServerInit.serializer(),
            ServerInit(
                proto = Syncro.PROTOCOL,
                v = Syncro.PROTOCOL_VERSION,
                eph = Crypto.encodePublicKey(ephemeral.public).toBase64(),
                nonce = Crypto.randomBytes(32).toBase64(),
                key = identity.publicKey.toBase64(),
                device = self.toWire(),
            ),
        )
        wire.writePlain(serverInit)

        val clientKeyBytes = wire.readPlain(MAX_HANDSHAKE_FRAME)
        if (!Crypto.constantTimeEquals(Crypto.sha256(clientKeyBytes), commitment)) {
            throw SecurityException("Key commitment mismatch")
        }
        val clientKey = decode(ClientKey.serializer(), clientKeyBytes)
        val clientEphemeral = publicKey(clientKey.eph)
        val clientIdentityKey = clientKey.key.b64()
        val client = verifiedDevice(clientKey.device, clientIdentityKey)

        val transcript = Crypto.sha256(lengthPrefixed(clientInitBytes, serverInit, clientKeyBytes))
        val keys = deriveKeys(Crypto.ecdh(ephemeral.private, clientEphemeral), transcript)
        val channel = SecureChannel(wire, keys.serverToClient, keys.clientToServer, connection::close)

        val auth = channel.receiveControl() as? Msg.Auth ?: throw ProtocolException("Expected authentication")
        if (!Crypto.verify(publicKey(clientKey.key), AUTH_CLIENT + transcript, auth.sig.b64())) {
            throw SecurityException("Device failed to prove its identity")
        }
        channel.sendControl(Msg.Auth(identity.sign(AUTH_SERVER + transcript).toBase64()))
        return SecureSession(connection, channel, client, clientIdentityKey, keys.pin)
    }

    private class Keys(val clientToServer: ByteArray, val serverToClient: ByteArray, val pin: String)

    private fun deriveKeys(sharedSecret: ByteArray, transcript: ByteArray): Keys {
        val prk = Crypto.hkdfExtract(transcript, sharedSecret)
        val pinValue = ByteBuffer.wrap(Crypto.hkdfExpand(prk, "syncro-v1 pin".toByteArray(), 4)).int.toLong() and 0xffffffffL
        return Keys(
            clientToServer = Crypto.hkdfExpand(prk, "syncro-v1 c2s".toByteArray(), 32),
            serverToClient = Crypto.hkdfExpand(prk, "syncro-v1 s2c".toByteArray(), 32),
            pin = (pinValue % 10_000).toString().padStart(4, '0'),
        )
    }

    private fun verifiedDevice(device: WireDevice, identityKey: ByteArray): DeviceInfo {
        publicKey(identityKey.toBase64())
        val expectedId = DeviceIdentity.idForPublicKey(identityKey)
        if (device.id != expectedId) throw SecurityException("Device id does not match its key")
        return DeviceInfo(expectedId, sanitizeDeviceName(device.name), DeviceType.parse(device.type))
    }

    private fun publicKey(base64: String): PublicKey = try {
        Crypto.decodePublicKey(base64.b64())
    } catch (e: GeneralSecurityException) {
        throw SecurityException("Invalid key: ${e.message}")
    }

    private fun String.b64(): ByteArray = try {
        fromBase64()
    } catch (_: IllegalArgumentException) {
        throw ProtocolException("Malformed handshake")
    }

    private fun DeviceInfo.toWire() = WireDevice(id, name, type.name)

    private fun <T> encode(serializer: kotlinx.serialization.KSerializer<T>, value: T): ByteArray =
        WireJson.encodeToString(serializer, value).toByteArray()

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, bytes: ByteArray): T = try {
        WireJson.decodeFromString(serializer, String(bytes, Charsets.UTF_8))
    } catch (e: SerializationException) {
        throw ProtocolException("Malformed handshake")
    } catch (e: IllegalArgumentException) {
        throw ProtocolException("Malformed handshake")
    }
}
