package com.aviansh.aifilemanager.domain.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun getSecret(key: String): String?
    fun saveSecret(key: String, value: String)
    fun deleteSecret(key: String)
}

class AndroidKeystoreSecretStore(private val context: Context) : SecretStore {

    private val alias = "AiFileManagerSecretKey"
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("encrypted_secret_store_prefs", Context.MODE_PRIVATE)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getKey(alias, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun getSecret(key: String): String? {
        val encryptedData = prefs.getString("${key}_data", null)
        val ivStr = prefs.getString("${key}_iv", null)

        if (encryptedData != null && ivStr != null) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val secretKey = getOrCreateSecretKey()
                val iv = Base64.decode(ivStr, Base64.NO_WRAP)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                val decryptedBytes = cipher.doFinal(Base64.decode(encryptedData, Base64.NO_WRAP))
                return String(decryptedBytes, Charsets.UTF_8)
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    override fun saveSecret(key: String, value: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = getOrCreateSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val encryptedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
            prefs.edit()
                .putString("${key}_data", encryptedData)
                .putString("${key}_iv", ivStr)
                .remove(key)
                .apply()
        } catch (e: Exception) {
            throw IllegalStateException("Unable to encrypt secret with Android Keystore", e)
        }
    }

    override fun deleteSecret(key: String) {
        prefs.edit()
            .remove("${key}_data")
            .remove("${key}_iv")
            .remove(key)
            .apply()
    }
}

class InMemorySecretStore : SecretStore {
    private val secrets = mutableMapOf<String, String>()
    override fun getSecret(key: String): String? = secrets[key]
    override fun saveSecret(key: String, value: String) { secrets[key] = value }
    override fun deleteSecret(key: String) { secrets.remove(key) }
}
