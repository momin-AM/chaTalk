package com.example.chatapk.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

class EncryptionManager(context: Context) {
    private val wrapperKeyAlias = "security_wrapper_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val prefs: SharedPreferences = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    private val pubKeyKey = "identity_public_key"
    private val privKeyEncKey = "identity_private_key_enc"
    private val privKeyIvKey = "identity_private_key_iv"

    fun getOrGenerateIdentityKeyPair(): PublicKey {
        val pubKeyStr = prefs.getString(pubKeyKey, null)
        if (pubKeyStr != null) {
            return stringToPublicKey(pubKeyStr)
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        
        val publicKeyStr = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        
        // Encrypt private key before storing
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrapperKey())
        val iv = cipher.iv
        val encryptedPrivKey = cipher.doFinal(keyPair.private.encoded)

        prefs.edit()
            .putString(pubKeyKey, publicKeyStr)
            .putString(privKeyEncKey, Base64.encodeToString(encryptedPrivKey, Base64.NO_WRAP))
            .putString(privKeyIvKey, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()

        return keyPair.public
    }

    fun getPublicKeyBase64(): String {
        return Base64.encodeToString(getOrGenerateIdentityKeyPair().encoded, Base64.NO_WRAP)
    }

    private fun getPrivateKey(): PrivateKey {
        val encPrivKeyStr = prefs.getString(privKeyEncKey, null) ?: throw IllegalStateException("Private key missing")
        val ivStr = prefs.getString(privKeyIvKey, null) ?: throw IllegalStateException("IV missing")
        
        val iv = Base64.decode(ivStr, Base64.NO_WRAP)
        val encPrivKey = Base64.decode(encPrivKeyStr, Base64.NO_WRAP)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrapperKey(), GCMParameterSpec(128, iv))
        val privKeyBytes = cipher.doFinal(encPrivKey)
        
        val spec = PKCS8EncodedKeySpec(privKeyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePrivate(spec)
    }

    private fun stringToPublicKey(publicKeyStr: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyStr, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(spec)
    }

    private fun deriveSharedSecret(otherPublicKey: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(getPrivateKey())
        ka.doPhase(otherPublicKey, true)
        return ka.generateSecret()
    }

    // HKDF implementation: HMAC-based Extract-and-Expand Key Derivation Function
    private fun hkdf(secret: ByteArray, salt: ByteArray?, info: ByteArray?, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val realSalt = salt ?: ByteArray(mac.macLength)
        mac.init(SecretKeySpec(realSalt, "HmacSHA256"))
        val prk = mac.doFinal(secret)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        var t = ByteArray(0)
        val result = ByteArray(length)
        var generated = 0
        var i = 1
        while (generated < length) {
            val bos = java.io.ByteArrayOutputStream()
            bos.write(t)
            if (info != null) bos.write(info)
            bos.write(i)
            t = mac.doFinal(bos.toByteArray())
            val copyLen = minOf(t.size, length - generated)
            System.arraycopy(t, 0, result, generated, copyLen)
            generated += copyLen
            i++
        }
        return result
    }

    fun encrypt(plainText: String, otherPublicKeyStr: String): String {
        val otherPublicKey = stringToPublicKey(otherPublicKeyStr)
        val sharedSecret = deriveSharedSecret(otherPublicKey)
        
        // Use HKDF to derive 256-bit AES key
        val aesKeyBytes = hkdf(sharedSecret, null, "chaTalk-aes-gcm".toByteArray(), 32)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(iv + encryptedBytes, Base64.NO_WRAP)
    }

    fun decrypt(encryptedTextWithIv: String, otherPublicKeyStr: String): String {
        val otherPublicKey = stringToPublicKey(otherPublicKeyStr)
        val data = Base64.decode(encryptedTextWithIv, Base64.NO_WRAP)
        
        val ivSize = 12 
        if (data.size < ivSize) return "[Invalid Data]"
        
        val iv = data.sliceArray(0 until ivSize)
        val encryptedBytes = data.sliceArray(ivSize until data.size)

        val sharedSecret = deriveSharedSecret(otherPublicKey)
        
        // Use HKDF to derive 256-bit AES key
        val aesKeyBytes = hkdf(sharedSecret, null, "chaTalk-aes-gcm".toByteArray(), 32)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun getDatabasePassphrase(): ByteArray {
        val encryptedPassphrase = prefs.getString("db_passphrase_enc", null)
        val ivStr = prefs.getString("db_passphrase_iv", null)

        if (encryptedPassphrase != null && ivStr != null) {
            try {
                val iv = Base64.decode(ivStr, Base64.NO_WRAP)
                val encryptedBytes = Base64.decode(encryptedPassphrase, Base64.NO_WRAP)
                
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrapperKey(), GCMParameterSpec(128, iv))
                return cipher.doFinal(encryptedBytes)
            } catch (e: Exception) {
                // Regeneration if decryption fails
            }
        }

        val newPassphrase = ByteArray(32)
        SecureRandom().nextBytes(newPassphrase)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrapperKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(newPassphrase)

        prefs.edit()
            .putString("db_passphrase_enc", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("db_passphrase_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()

        return newPassphrase
    }

    private fun getOrCreateWrapperKey(): java.security.Key {
        if (!keyStore.containsAlias(wrapperKeyAlias)) {
            val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    wrapperKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }
        return keyStore.getKey(wrapperKeyAlias, null)
    }
}
