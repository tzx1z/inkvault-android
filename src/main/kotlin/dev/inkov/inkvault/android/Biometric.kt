package dev.inkov.inkvault.android

import android.app.Activity
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Быстрая разблокировка отпечатком. Ключ Keystore требует BIOMETRIC_STRONG на каждую операцию
 * и аннулируется при добавлении нового отпечатка. Им шифруется секрет устройства из ядра,
 * которым ядро открывает user key без мастер-пароля.
 */
object Biometric {
    const val KEY_ALIAS = "inkvault-device"

    // BIOMETRIC_STRONG и setUserAuthenticationParameters есть с Android 11
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    class Invalidated : Exception()

    fun encrypt(activity: Activity, secret: ByteArray, onDone: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val cipher = Cipher.getInstance(Store.TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, createKey())
        prompt(activity, cipher, activity.getString(R.string.biometric_enable), { c -> onDone(c.iv + c.doFinal(secret)) }, onError)
    }

    fun decrypt(activity: Activity, encrypted: ByteArray, onDone: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val cipher = Cipher.getInstance(Store.TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: throw Invalidated(), GCMParameterSpec(128, encrypted, 0, Store.IV_LEN))
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw Invalidated()
        }
        prompt(activity, cipher, activity.getString(R.string.biometric_unlock_title), { c -> onDone(c.doFinal(encrypted, Store.IV_LEN, encrypted.size - Store.IV_LEN)) }, onError)
    }

    private fun prompt(activity: Activity, cipher: Cipher, title: String, onDone: (Cipher) -> Unit, onError: (String) -> Unit) {
        val executor = activity.mainExecutor
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButton(activity.getString(R.string.master_password), executor) { _, _ -> onError(activity.getString(R.string.biometric_canceled)) }
            .build()
        prompt.authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                runCatching { onDone(result.cryptoObject?.cipher ?: error(activity.getString(R.string.biometric_no_key))) }
                    .onFailure { onError(it.message ?: activity.getString(R.string.biometric_key_error)) }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        })
    }

    private fun existingKey(): SecretKey? = KeyStore.getInstance(Store.KEYSTORE).apply { load(null) }.getKey(KEY_ALIAS, null) as? SecretKey

    private fun createKey(): SecretKey {
        val build = { strongBox: Boolean ->
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .setIsStrongBoxBacked(strongBox)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Store.KEYSTORE).apply { init(spec) }.generateKey()
        }
        // StrongBox есть не на всех устройствах, без него ключ живет в TEE
        return try {
            build(true)
        } catch (e: StrongBoxUnavailableException) {
            build(false)
        }
    }
}
