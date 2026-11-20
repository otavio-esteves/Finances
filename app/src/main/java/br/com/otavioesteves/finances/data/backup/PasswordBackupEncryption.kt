package br.com.otavioesteves.finances.data.backup

import br.com.otavioesteves.finances.domain.repository.*
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import java.io.*
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Tink owns authenticated segmentation, nonce allocation and final-segment verification. */
class PasswordBackupEncryption : BackupEncryption {
    override fun encrypt(output: OutputStream, password: CharArray, checkCancelled: () -> Unit): OutputStream {
        if (password.size !in 12..1024) throw BackupProtectionException(BackupProtectionException.Reason.WEAK_PASSWORD)
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val header = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                write(ProtectedBackupFormat.magic); writeByte(1); writeByte(1); writeInt(ITERATIONS); write(salt)
            }
        }.toByteArray()
        checkCancelled()
        val key = derive(password, salt, ITERATIONS)
        return try {
            checkCancelled()
            output.write(header)
            AesGcmHkdfStreaming(key, "HmacSha256", 32, SEGMENT_BYTES, 0).newEncryptingStream(output, header)
        } finally { key.fill(0); salt.fill(0) }
    }

    override fun decrypt(input: InputStream, password: CharArray, checkCancelled: () -> Unit): InputStream {
        if (password.isEmpty()) throw BackupProtectionException(BackupProtectionException.Reason.PASSWORD_REQUIRED)
        if (password.size > 1024) throw BackupProtectionException(BackupProtectionException.Reason.INVALID)
        val header = ByteArray(46)
        try { DataInputStream(input).readFully(header) }
        catch (_: EOFException) { throw BackupProtectionException(BackupProtectionException.Reason.INVALID) }
        val fields = DataInputStream(header.inputStream())
        val magic = ByteArray(8).also { fields.readFully(it) }
        if (!magic.contentEquals(ProtectedBackupFormat.magic)) throw BackupProtectionException(BackupProtectionException.Reason.INVALID)
        if (fields.readUnsignedByte() != 1 || fields.readUnsignedByte() != 1)
            throw BackupProtectionException(BackupProtectionException.Reason.UNSUPPORTED)
        val iterations = fields.readInt()
        // Bound unauthenticated work before password derivation; this format currently mandates one work factor.
        if (iterations != ITERATIONS) throw BackupProtectionException(BackupProtectionException.Reason.UNSUPPORTED)
        val salt = ByteArray(32).also { fields.readFully(it) }
        checkCancelled()
        val key = derive(password, salt, iterations)
        return try {
            checkCancelled()
            AesGcmHkdfStreaming(key, "HmacSha256", 32, SEGMENT_BYTES, 0).newDecryptingStream(input, header)
        } finally { key.fill(0); salt.fill(0) }
    }

    internal fun derive(password: CharArray, salt: ByteArray, iterations: Int, forceFallback: Boolean = false): ByteArray {
        if (!forceFallback) {
            val factory = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }
            catch (_: java.security.NoSuchAlgorithmException) { null } // Android 24–25.
            if (factory != null) {
                val spec = PBEKeySpec(password, salt, iterations, 256)
                return try { factory.generateSecret(spec).encoded } finally { spec.clearPassword() }
            }
        }
        val bytes = org.bouncycastle.crypto.PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password)
        return try {
            PKCS5S2ParametersGenerator(SHA256Digest()).apply { init(bytes, salt, iterations) }
                .generateDerivedParameters(256).let { (it as KeyParameter).key }
        } finally { bytes.fill(0) }
    }

    companion object { const val ITERATIONS = 600_000; const val SEGMENT_BYTES = 65_536 }
}
