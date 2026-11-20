package br.com.otavioesteves.finances.domain.repository

import java.io.InputStream
import java.io.OutputStream

/** Portable protection; the password and derived keys must never be persisted. */
interface BackupEncryption {
    fun encrypt(output: OutputStream, password: CharArray, checkCancelled: () -> Unit): OutputStream
    fun decrypt(input: InputStream, password: CharArray, checkCancelled: () -> Unit): InputStream
}

class BackupProtectionException(val reason: Reason) : IllegalArgumentException(when (reason) {
    Reason.PASSWORD_REQUIRED -> "Este backup é protegido. Informe a senha usada na criação."
    Reason.INVALID -> "Senha incorreta ou backup danificado. Confira a senha e selecione uma cópia íntegra."
    Reason.UNSUPPORTED -> "Formato de backup protegido incompatível. Atualize o Finances."
    Reason.WEAK_PASSWORD -> "Use uma senha com pelo menos 12 caracteres e no máximo 1024."
}) {
    enum class Reason { PASSWORD_REQUIRED, INVALID, UNSUPPORTED, WEAK_PASSWORD }
}

object ProtectedBackupFormat {
    val magic: ByteArray get() = "FNBKENC\n".toByteArray(Charsets.US_ASCII)
}
