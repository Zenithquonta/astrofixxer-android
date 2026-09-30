package org.astrofixxer.update

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Checks that a downloaded APK is the file the release described. */
object Integrity {
    private val HEX64 = Regex("[0-9a-fA-F]{64}")

    fun isSha256Hex(s: String): Boolean = HEX64.matches(s)

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun sha256Hex(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(data))

    /** True when both are valid 64-digit hex SHA-256 values that are equal, ignoring letter case. */
    fun sha256Matches(expected: String, actual: String): Boolean =
        isSha256Hex(expected) && isSha256Hex(actual) && MessageDigest.isEqual(expected.lowercase().toByteArray(), actual.lowercase().toByteArray())

    class Copied(val bytes: Long, val sha256: String)

    /**
     * Copies [input] to [output] and hashes exactly what was written, so the hash is of the copy that gets installed.
     * More than [maxBytes] throws [TooLargeException] (the caller then deletes the partial output).
     */
    fun copyAndHash(input: InputStream, output: OutputStream, maxBytes: Long): Copied {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw TooLargeException()
            digest.update(buf, 0, n)
            output.write(buf, 0, n)
        }
        return Copied(total, hex(digest.digest()))
    }

    /** null when [copied] has the size and SHA-256 the release promised; otherwise why not. */
    fun verify(copied: Copied, info: UpdateInfo): UpdateProblem? = when {
        copied.bytes != info.size -> UpdateProblem.SizeMismatch
        !sha256Matches(info.sha256, copied.sha256) -> UpdateProblem.ChecksumMismatch
        else -> null
    }
}
