package dev.inkov.inkvault.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

// выше 10000 записей клиент не проверялся
val MAX_ITEMS_RANGE = 1..10000

data class Settings(val jid: String, val host: String, val port: Int, val insecure: Boolean) {
    fun toJson(): String = JSONObject()
        .put("jid", jid).put("host", host).put("port", port).put("insecure", insecure).toString()

    companion object {
        fun fromJson(json: String) = JSONObject(json).let {
            Settings(it.getString("jid"), it.optString("host"), it.optInt("port", 5222), it.optBoolean("insecure"))
        }
    }
}

/**
 * Хранение на устройстве. В учетной записи только соль и обернутые ключи, снимок зашифрован
 * ключом из vault key. Пароль XMPP шифруется ключом Keystore: в Smack нет токенов FAST, а
 * переподключаться после обрыва нужно без участия пользователя.
 */
class Store(private val context: Context) {
    private val dir = context.filesDir
    private val accountFile = File(dir, "account.json")
    private val settingsFile = File(dir, "settings.json")
    private val xmppFile = File(dir, "xmpp.bin")
    private val deviceSecretFile = File(dir, "device.secret")
    private val deviceBlobFile = File(dir, "device.blob")

    fun account(): String? = accountFile.takeIf { it.exists() }?.readText()

    fun saveAccount(json: String) = writeAtomic(accountFile, json.toByteArray())

    /** Переход на другую учетную запись: прежняя копия сохраняется, без нее кэш этого устройства не открыть. */
    fun replaceAccount(json: String) {
        if (accountFile.exists()) accountFile.copyTo(File(dir, "account-previous.json"), overwrite = true)
        saveAccount(json)
    }

    fun settings(): Settings? = settingsFile.takeIf { it.exists() }?.let { Settings.fromJson(it.readText()) }

    fun saveSettings(settings: Settings) = writeAtomic(settingsFile, settings.toJson().toByteArray())

    fun snapshot(vaultId: String): ByteArray? = snapshotFile(vaultId).takeIf { it.exists() }?.readBytes()

    fun saveSnapshot(vaultId: String, data: ByteArray) = writeAtomic(snapshotFile(vaultId), data)

    fun xmppPassword(): String? {
        if (!xmppFile.exists()) return null
        val data = xmppFile.readBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, xmppKey(), GCMParameterSpec(128, data, 0, IV_LEN))
        return String(cipher.doFinal(data, IV_LEN, data.size - IV_LEN))
    }

    fun saveXmppPassword(password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, xmppKey())
        writeAtomic(xmppFile, cipher.iv + cipher.doFinal(password.toByteArray()))
    }

    /** Секрет устройства, зашифрованный ключом с биометрией (iv + шифротекст), и обертка user key. */
    fun device(): Pair<ByteArray, ByteArray>? =
        if (deviceSecretFile.exists() && deviceBlobFile.exists()) deviceSecretFile.readBytes() to deviceBlobFile.readBytes() else null

    fun saveDevice(encryptedSecret: ByteArray, blob: ByteArray) {
        writeAtomic(deviceSecretFile, encryptedSecret)
        writeAtomic(deviceBlobFile, blob)
    }

    fun clearDevice() {
        deviceSecretFile.delete()
        deviceBlobFile.delete()
    }

    /** Выход с устройства: удаляются учетная запись, кэш, пароль XMPP и биометрия. */
    fun forget() {
        dir.listFiles()?.forEach { it.delete() }
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.run {
            deleteEntry(XMPP_KEY)
            deleteEntry(Biometric.KEY_ALIAS)
        }
    }

    /** Хранилище удалено: все как при выходе, кроме настроек входа. */
    fun forgetVault() {
        val settings = settings()
        forget()
        settings?.let(::saveSettings)
    }

    private fun snapshotFile(vaultId: String) = File(dir, "vault-$vaultId.snapshot")

    private fun writeAtomic(file: File, data: ByteArray) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(file)) error(context.getString(R.string.store_write_failed, file.name))
    }

    private fun xmppKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(XMPP_KEY, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(XMPP_KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        private const val XMPP_KEY = "inkvault-xmpp"
    }
}
