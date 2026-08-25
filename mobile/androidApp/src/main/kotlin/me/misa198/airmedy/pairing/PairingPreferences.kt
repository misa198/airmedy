package me.misa198.airmedy.pairing

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.MobilePlatform
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.pairing.PairingBindingStore
import me.misa198.airmedy.pairing.PairingIdentityProvider
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec

private val Context.pairingDataStore by preferencesDataStore(name = "pairing")
private val MobileIdKey = stringPreferencesKey("mobile_id")
private val MobilePublicKey = stringPreferencesKey("mobile_public_key")
private val EncryptedPrivateKey = stringPreferencesKey("encrypted_mobile_private_key")
private val DesktopIdKey = stringPreferencesKey("desktop_id")
private val DesktopNameKey = stringPreferencesKey("desktop_name")
private val DesktopPublicKey = stringPreferencesKey("desktop_public_key")
private val DesktopHostKey = stringPreferencesKey("desktop_host")
private val DesktopPortKey = stringPreferencesKey("desktop_port")
private val LastSyncedAtKey = longPreferencesKey("last_synced_at")

class PairingPreferences(private val context: Context) : PairingIdentityProvider, PairingBindingStore {
    private val mutex = Mutex()
    private var key: EdDSAPrivateKey? = null

    override val pairedDesktop: Flow<PairedDesktop?> = context.pairingDataStore.data.map { preferences ->
        val id = preferences[DesktopIdKey] ?: return@map null
        val name = preferences[DesktopNameKey] ?: return@map null
        val publicKey = decode(preferences[DesktopPublicKey]) ?: return@map null
        val host = preferences[DesktopHostKey]
        val port = preferences[DesktopPortKey]?.toIntOrNull()
        PairedDesktop(id, name, publicKey, host, port)
    }

    override suspend fun current(): PairedDesktop? = pairedDesktop.first()

    val lastSyncedAt: Flow<Long?> = context.pairingDataStore.data.map { it[LastSyncedAtKey] }

    suspend fun markSyncCompleted(atMillis: Long) {
        context.pairingDataStore.edit { it[LastSyncedAtKey] = atMillis }
    }

    override suspend fun save(desktop: PairedDesktop) {
        context.pairingDataStore.edit { preferences ->
            preferences[DesktopIdKey] = desktop.desktopId
            preferences[DesktopNameKey] = desktop.displayName
            preferences[DesktopPublicKey] = encode(desktop.publicKey)
            desktop.host?.let { preferences[DesktopHostKey] = it }
            desktop.port?.let { preferences[DesktopPortKey] = it.toString() }
        }
    }

    override suspend fun clear() {
        context.pairingDataStore.edit { preferences ->
            listOf(DesktopIdKey, DesktopNameKey, DesktopPublicKey, DesktopHostKey, DesktopPortKey).forEach(preferences::remove)
            preferences.remove(LastSyncedAtKey)
        }
    }

    override suspend fun identity(): MobileIdentity = mutex.withLock {
        val privateKey = loadOrCreateKey()
        val preferences = context.pairingDataStore.data.first()
        val id = preferences[MobileIdKey] ?: UUID.randomUUID().toString().also { newId ->
            context.pairingDataStore.edit { it[MobileIdKey] = newId }
        }
        MobileIdentity(id, deviceName(), MobilePlatform.Android, privateKey.getAbyte())
    }

    override suspend fun randomBytes(size: Int): ByteArray = ByteArray(size).also { java.security.SecureRandom().nextBytes(it) }

    override suspend fun sign(input: ByteArray): ByteArray = mutex.withLock {
        val signer = EdDSAEngine(MessageDigest.getInstance("SHA-512"))
        signer.initSign(loadOrCreateKey())
        signer.update(input)
        signer.sign()
    }

    override suspend fun verify(publicKey: ByteArray, input: ByteArray, signature: ByteArray): Boolean = runCatching {
        val curve = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
        val verifier = EdDSAEngine(MessageDigest.getInstance("SHA-512"))
        verifier.initVerify(EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, curve)))
        verifier.update(input)
        verifier.verify(signature)
    }.getOrDefault(false)

    private suspend fun loadOrCreateKey(): EdDSAPrivateKey {
        key?.let { return it }
        val preferences = context.pairingDataStore.data.first()
        val encrypted = preferences[EncryptedPrivateKey]
        if (encrypted != null) {
            val encoded = decrypt(decode(encrypted) ?: error("Invalid pairing identity"))
            val curve = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
            return EdDSAPrivateKey(EdDSAPrivateKeySpec(encoded, curve)).also { key = it }
        }
        val generated = KeyPairGenerator().generateKeyPair().private as EdDSAPrivateKey
        context.pairingDataStore.edit { values ->
            values[EncryptedPrivateKey] = encode(encrypt(generated.seed))
            values[MobilePublicKey] = encode(generated.getAbyte())
        }
        return generated.also { key = it }
    }

    private fun encrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return cipher.iv + cipher.doFinal(bytes)
    }

    private fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size > 12) { "Invalid pairing identity" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }

    private fun keystoreKey(): javax.crypto.SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "airmedy.mobile.pairing.identity.v1"
        (store.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun deviceName(): String = Build.MODEL.trim().ifBlank { "Android device" }.take(64)
    private fun encode(bytes: ByteArray): String = android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    private fun decode(value: String?): ByteArray? = value?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP) }.getOrNull() }
}
