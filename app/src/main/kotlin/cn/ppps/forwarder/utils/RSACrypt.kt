package cn.ppps.forwarder.utils

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Asymmetric EncryptionRSAEncryption and Decryption
 */
object RSACrypt {

    private const val TRANSFORMATION = "RSA"
    private const val ENCRYPT_MAX_SIZE = 245
    private const val DECRYPT_MAX_SIZE = 256

    /**
     * Private Key Encryption
     * @param input Original
     * @param privateKey Private Key
     */
    fun encryptByPrivateKey(input: String, privateKey: PrivateKey): String {

        //CreatecipherObject
        val cipher = Cipher.getInstance(TRANSFORMATION)
        //Initializecipher
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)

        //****Asymmetric Encryption****
        val byteArray = input.toByteArray()

        //Block encryption
        var temp: ByteArray?
        var offset = 0 //Current Offset

        val outputStream = ByteArrayOutputStream()

        //Splitinput
        while (byteArray.size - offset > 0) {
            //Maximum encryption at each time245bytes
            if (byteArray.size - offset >= ENCRYPT_MAX_SIZE) {
                //The remainder is greater than245
                //Encryption Complete245
                temp = cipher.doFinal(byteArray, offset, ENCRYPT_MAX_SIZE)
                //Recalculate Offset Location
                offset += ENCRYPT_MAX_SIZE
            } else {
                //Encrypt the last piece.
                temp = cipher.doFinal(byteArray, offset, byteArray.size - offset)
                //Recalculate Offset Location
                offset = byteArray.size
            }
            //Storage to Temporary Buffer
            outputStream.write(temp)
        }
        outputStream.close()

        return Base64.encode(outputStream.toByteArray())

    }

    /**
     * Public Key Encryption
     * @param input Original
     * @param publicKey Public Keys
     */
    fun encryptByPublicKey(input: String, publicKey: PublicKey): String {

        //CreatecipherObject
        val cipher = Cipher.getInstance(TRANSFORMATION)
        //Initializecipher
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        //****Asymmetric Encryption****
        val byteArray = input.toByteArray()

        var temp: ByteArray?
        var offset = 0 //Current Offset

        val outputStream = ByteArrayOutputStream()

        //Splitinput
        while (byteArray.size - offset > 0) {
            //Maximum encryption at each time117bytes
            if (byteArray.size - offset >= ENCRYPT_MAX_SIZE) {
                //The remainder is greater than117
                //Encryption Complete117
                temp = cipher.doFinal(byteArray, offset, ENCRYPT_MAX_SIZE)
                //Recalculate Offset Location
                offset += ENCRYPT_MAX_SIZE
            } else {
                //Encrypt the last piece.
                temp = cipher.doFinal(byteArray, offset, byteArray.size - offset)
                //Recalculate Offset Location
                offset = byteArray.size
            }
            //Storage to Temporary Buffer
            outputStream.write(temp)
        }
        outputStream.close()

        return Base64.encode(outputStream.toByteArray())

    }

    /**
     * Private key decryption
     * @param input Ciphertext
     * @param privateKey Private Key
     */
    fun decryptByPrivateKey(input: String, privateKey: PrivateKey): String {

        //CreatecipherObject
        val cipher = Cipher.getInstance(TRANSFORMATION)
        //Initializecipher
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        //****Asymmetric Encryption****
        val byteArray = Base64.decode(input)

        //Block decryption
        var temp: ByteArray?
        var offset = 0 //Current Offset

        val outputStream = ByteArrayOutputStream()

        //Splitinput
        while (byteArray.size - offset > 0) {
            //Every maximum decryption256bytes
            if (byteArray.size - offset >= DECRYPT_MAX_SIZE) {

                temp = cipher.doFinal(byteArray, offset, DECRYPT_MAX_SIZE)
                //Recalculate Offset Location
                offset += DECRYPT_MAX_SIZE
            } else {
                //Encrypt the last piece.
                temp = cipher.doFinal(byteArray, offset, byteArray.size - offset)
                //Recalculate Offset Location
                offset = byteArray.size
            }
            //Storage to Temporary Buffer
            outputStream.write(temp)
        }
        outputStream.close()

        return String(outputStream.toByteArray())

    }

    /**
     * Public Key Decrypt
     * @param input Ciphertext
     * @param publicKey Public Keys
     */
    fun decryptByPublicKey(input: String, publicKey: PublicKey): String {

        //CreatecipherObject
        val cipher = Cipher.getInstance(TRANSFORMATION)
        //Initializecipher
        cipher.init(Cipher.DECRYPT_MODE, publicKey)

        //****Asymmetric Encryption****
        val byteArray = Base64.decode(input)

        //Block decryption
        var temp: ByteArray?
        var offset = 0 //Current Offset

        val outputStream = ByteArrayOutputStream()

        //Splitinput
        while (byteArray.size - offset > 0) {
            //Every maximum decryption256bytes
            if (byteArray.size - offset >= DECRYPT_MAX_SIZE) {

                temp = cipher.doFinal(byteArray, offset, DECRYPT_MAX_SIZE)
                //Recalculate Offset Location
                offset += DECRYPT_MAX_SIZE
            } else {
                //Encrypt the last piece.
                temp = cipher.doFinal(byteArray, offset, byteArray.size - offset)
                //Recalculate Offset Location
                offset = byteArray.size
            }
            //Storage to Temporary Buffer
            outputStream.write(temp)
        }
        outputStream.close()

        return String(outputStream.toByteArray())

    }

    fun getPrivateKey(privateKeyStr: String): PrivateKey {
        //Parse key pair from string
        val generator = KeyFactory.getInstance("RSA")
        return generator.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privateKeyStr)))
    }

    fun getPublicKey(publicKeyStr: String): PublicKey {
        //Parse key pair from string
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePublic(X509EncodedKeySpec(Base64.decode(publicKeyStr)))
    }

}