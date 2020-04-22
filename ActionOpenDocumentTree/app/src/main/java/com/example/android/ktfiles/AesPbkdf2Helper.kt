package com.example.android.ktfiles

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


class AesPbkdf2Helper {

    companion object {

        val HARDCODED_PASSWORD = "123456"

        val PREF_SECRET_KEY = "secretKey"

//        val PROVIDER_BC = "BC"
        val ALG_AES = "AES"
        val ALG_AES_GCM_NOPADDING = "AES/GCM/NoPadding"
        val ALG_PBKDF2 = "PBKDF2withHmacSHA256"

        val gcmTagLen = 16
        val gcmIvSize = 12

        val saltLength = 16
        val keyLength = 256
        val iterationCount = 100000

        fun saveSecretKey(sharedPref: SharedPreferences, secretKey: SecretKey): String {
            val encodedKey = Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
            sharedPref.edit().putString(PREF_SECRET_KEY, encodedKey).apply()
            return encodedKey
        }

        fun getSecretKey(context: Context): SecretKey {

            val sharedPref = context.getSharedPreferences("AesPbkdf2Helper", 0)
            val key = sharedPref.getString(PREF_SECRET_KEY, null)

            if (key == null) {
                //generate secure random
                val secretKey = generateSecretKey(HARDCODED_PASSWORD)
                saveSecretKey(sharedPref, secretKey!!)
                return secretKey
            }

            val decodedKey = Base64.decode(key, Base64.NO_WRAP)
            val originalKey = SecretKeySpec(decodedKey, 0, decodedKey.size, ALG_AES)

            return originalKey
        }

        @Throws(Exception::class)
        fun readFile(filePath: String): ByteArray {
            val file = File(filePath)
            val fileContents = file.readBytes()
            val inputBuffer = BufferedInputStream(
                FileInputStream(file)
            )

            inputBuffer.read(fileContents)
            inputBuffer.close()

            return fileContents
        }

        @Throws(Exception::class)
        fun writeFile(fileData: ByteArray, path: String) {
            val file = File(path)
            val bos = BufferedOutputStream(FileOutputStream(file, false))
            bos.write(fileData)
            bos.flush()
            bos.close()
        }

        @Throws(Exception::class)
        fun generateSecretKey(password: String): SecretKey? {

            val rnd = SecureRandom()
            val salt = ByteArray(saltLength)
            rnd.nextBytes(salt)

            val factory = SecretKeyFactory.getInstance(ALG_PBKDF2)

            val skeySpec = PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength)

            Log.e("AesPbkdf2Helper", "generateSecretKey")
            return factory.generateSecret(skeySpec)
        }

        @Throws(Exception::class)
        fun encrypt(yourKey: SecretKey, fileData: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(ALG_AES_GCM_NOPADDING)
            cipher.init(
                Cipher.ENCRYPT_MODE, yourKey, GCMParameterSpec(
                    gcmTagLen * 8, ByteArray(
                        gcmIvSize
                    )
                )
            )
            return cipher.doFinal(fileData)
        }

        @Throws(Exception::class)
        fun decrypt(yourKey: SecretKey, fileData: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(ALG_AES_GCM_NOPADDING)
            cipher.init(
                Cipher.DECRYPT_MODE,
                yourKey,
                GCMParameterSpec(gcmTagLen * 8, ByteArray(gcmIvSize))
            )
            return cipher.doFinal(fileData)
        }
    }

}