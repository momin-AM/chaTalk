package com.example.chatapk.security

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.content.Context
import android.content.SharedPreferences

class EncryptionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
    private val pubKeyKey = "identity_public_key"
    private val privKeyKey = "identity_private_key"

    fun getOrGenerateIdentityKeyPair(): PublicKey {
        val pubKeyStr = prefs.getString(pubKeyKey, null)
        val privKeyStr = prefs.getString(privKeyKey, null)

        if (pubKeyStr != null && privKeyStr != null) {
            return stringToPublicKey(pubKeyStr)
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        
        prefs.edit()
            .putString(pubKeyKey, Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
            .putString(privKeyKey, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
            .apply()

        return keyPair.public
    }

    fun getPublicKeyBase64(): String {
        return Base64.encodeToString(getOrGenerateIdentityKeyPair().encoded, Base64.NO_WRAP)
    }

    private fun getPrivateKey(): PrivateKey {
        val privKeyStr = prefs.getString(privKeyKey, null) ?: throw IllegalStateException("Private key missing")
        val keyBytes = Base64.decode(privKeyStr, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(keyBytes)
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

    fun encrypt(plainText: String, otherPublicKeyStr: String): String {
        val otherPublicKey = stringToPublicKey(otherPublicKeyStr)
        val sharedSecret = deriveSharedSecret(otherPublicKey)

        val secretKey = SecretKeySpec(sharedSecret.sliceArray(0 until 16), "AES")
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

        val secretKey = SecretKeySpec(sharedSecret.sliceArray(0 until 16), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
