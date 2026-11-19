package com.jefz.eq

import android.content.Context
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock. No password or recovery code is stored in clear text: only salted PBKDF2 hashes (masked).
 * Unlock state is kept in app-private storage (backup disabled), so reinstalling asks again.
 */
object LockStore {
    private val K = byteArrayOf(0, -116, 121, -121, 88, -40, -72, 59, -37, 1, 15, -26, -14, -43, -109, 94)
    private fun d(m: ByteArray) = ByteArray(m.size) { (m[it].toInt() xor K[it % K.size].toInt()).toByte() }

    private val S3 get() = d(byteArrayOf(100, -83, -58, 90, 77, 68, 91, 47, -38, 39, -57, 125, -74, -123, -31, 24))
    private val H3 get() = d(byteArrayOf(-11, 12, 108, 72, -33, 78, 83, -109, 80, 90, -20, -31, 79, 105, 17, 67, 65, 66, -96, -64, 45, -50, 64, -9, -122, -60, 85, -122, 103, 34, 29, 36))
    private val S2 get() = d(byteArrayOf(-36, -108, 77, -103, -29, -42, -11, -42, 0, 81, 13, 11, 14, -10, 33, 60))
    private val H2 get() = d(byteArrayOf(-77, -112, 91, 110, 9, -42, -5, 9, -90, -51, -81, 68, 20, -108, -17, -45, -69, -51, -92, -11, -77, -111, -3, -9, 3, 110, 111, -14, 90, -104, -48, 17))

    private fun derive(secret: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iter, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun checkPassword(pw: String): Boolean {
        if (pw.isEmpty() || pw.length > 64) return false
        return MessageDigest.isEqual(derive(pw, S3, 300_000), H3)
    }

    fun checkRecovery(input: String): Boolean {
        val n = input.uppercase().filter { it.isLetterOrDigit() }
        if (n.length != 16) return false
        return MessageDigest.isEqual(derive(n, S2, 200_000), H2)
    }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("jz_cfg", Context.MODE_PRIVATE)

    // New key "u2": after the password change the app asks once more, even on an in-place update.
    fun isUnlocked(ctx: Context): Boolean = sp(ctx).getBoolean("u2", false)

    fun unlock(ctx: Context) {
        sp(ctx).edit().putBoolean("u2", true).remove("b").remove("c").apply()
    }

    fun lockedUntil(ctx: Context): Long = sp(ctx).getLong("c", 0L)

    /** Every 5th wrong attempt locks input for a growing time (30 s, 60 s, ...). */
    fun registerFail(ctx: Context) {
        val s = sp(ctx)
        val n = s.getInt("b", 0) + 1
        val e = s.edit().putInt("b", n)
        if (n % 5 == 0) {
            val mult = 1L shl minOf(n / 5 - 1, 6)
            e.putLong("c", System.currentTimeMillis() + 30_000L * mult)
        }
        e.apply()
    }
}
