package dev.playagain32.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * The game signing key lives in Android's key store; no shared private key ships in this app.
 */
object Signing {
    // Keep the earlier alpha's alias so prepared-game updates retain their signing identity.
    private const val ALIAS = "legacybridge-games"

    private class Key(val key: PrivateKey, val certificates: List<X509Certificate>)

    private var cached: Key? = null

    @Synchronized
    private fun key(): Key = cached ?: phoneKey().also { cached = it }

    private fun phoneKey(): Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            // APK signatures use RSA with PKCS#1 padding; SHA-1 is for the old-style (v1) signature
            // of games that still run on Android 4.2 and older.
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setCertificateSubject(X500Principal("CN=PlayAgain32"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(0))
                // Android ignores a game's certificate dates, but far-off ones keep any checker quiet.
                .setCertificateNotAfter(Date(4102444800000)) // 2100-01-01
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }
        val entry = store.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return Key(entry.privateKey, entry.certificateChain.map { it as X509Certificate })
    }

    fun sign(context: Context, input: File, output: File) {
        val key = key()
        val signer = ApkSigner.SignerConfig.Builder("CERT", key.key, key.certificates).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .build()
            .sign()
    }
}
