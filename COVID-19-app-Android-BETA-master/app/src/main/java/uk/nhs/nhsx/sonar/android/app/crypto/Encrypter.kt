/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app.crypto

import android.annotation.SuppressLint
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.KDF2BytesGenerator
import org.bouncycastle.crypto.params.KDFParameters
import uk.nhs.nhsx.sonar.android.app.http.PublicKeyStorage
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class Encrypter @Inject constructor(
    private val keyStorage: PublicKeyStorage,
    private val ephemeralKeyProvider: EphemeralKeyProvider
) {
    fun canEncrypt(): Boolean =
        keyStorage.providePublicKey() != null

    fun encrypt(
        encodedStartDate: ByteArray,
        encodedEndDate: ByteArray,
        sonarID: ByteArray,
        countryCode: ByteArray
    ): Cryptogram {
        val ephemeralKeyPair = ephemeralKeyProvider.provideEphemeralKeys()

        val localPrivateKey = ephemeralKeyPair.private
        val serverPublicKey = keyStorage.providePublicKey()
        val sharedSecret = generateSharedSecret(localPrivateKey, serverPublicKey!!)

        val sharedInformation = ephemeralKeyPair.public.toPublicKeyPoint()

        val derivedKeyAndIV = deriveSecretKeyAndIV(sharedSecret, sharedInformation)
        val plainText = encodedStartDate + encodedEndDate + sonarID + countryCode
        val encryptionResult =
            aesGcmEncrypt(derivedKeyAndIV.secretKey, derivedKeyAndIV.iv, plainText)

        return Cryptogram(
            sharedInformation.sliceArray(1 until sharedInformation.size),
            encryptionResult.payload,
            encryptionResult.tag
        )
    }

    class DerivedKeyAndIV(keyDerivationOutput: ByteArray) {
        init {
            if (keyDerivationOutput.size != 32) {
                throw IllegalArgumentException("X9.63 should generate 32 bytes of data")
            }
        }

        val secretKey: SecretKey = SecretKeySpec(keyDerivationOutput.sliceArray((0 until 16)), AES)
        val iv: ByteArray = keyDerivationOutput.sliceArray((16 until 32))
    }

    class EncryptionResult(encryptedBytes: ByteArray, plainTextSize: Int) {
        init {
            if (encryptedBytes.size != plainTextSize + 16) {
                throw IllegalArgumentException("AES-GCM should generate encrypted payload of same size as plaintext, with an additional 16 byte tag.")
            }
        }

        val payload = encryptedBytes.sliceArray((0 until plainTextSize))
        val tag = encryptedBytes.sliceArray((plainTextSize until encryptedBytes.size))
    }

    private fun generateSharedSecret(
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): ByteArray {
        val keyAgreement = KeyAgreement.getInstance(ECDH, PROVIDER_NAME)
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        val secret = keyAgreement.generateSecret()
        if (secret.size != 32)
            throw IllegalArgumentException("Shared secret should be 256 bits (32 bytes)")
        return secret
    }

    private fun deriveSecretKeyAndIV(
        sharedSecret: ByteArray,
        sharedInformation: ByteArray
    ): DerivedKeyAndIV {
        val kdfParams = KDFParameters(sharedSecret, sharedInformation)
        // https://www.bouncycastle.org/docs/docs1.5on/org/bouncycastle/crypto/generators/KDF2BytesGenerator.html
        // Based on the ISO18033 KDF which turns out to derived from ANSI-X9.63-KDF
        val keyDerivationFunctionGenerator = KDF2BytesGenerator(SHA256Digest())
        keyDerivationFunctionGenerator.init(kdfParams)
        val keyDerivationOutput = ByteArray(32)
        keyDerivationFunctionGenerator.generateBytes(keyDerivationOutput, 0, 32)
        return DerivedKeyAndIV(keyDerivationOutput)
    }

    @SuppressLint("GetInstance")
    private fun aesGcmEncrypt(
        encryptionKey: SecretKey,
        iv: ByteArray,
        data: ByteArray
    ): EncryptionResult {
        val cipher = Cipher.getInstance(AES_GCM_NoPadding, PROVIDER_NAME)
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmParameterSpec)
        val encrypted = cipher.doFinal(data)

        return EncryptionResult(encrypted, data.size)
    }
}
