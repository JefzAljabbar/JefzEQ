package com.jefz.eq

import android.content.Context
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock. No PIN or recovery code is stored in clear text: only salted PBKDF2 hashes (masked).
 * Unlock state is kept in app-private storage (backup disabled), so reinstalling asks again.
 */
object LockStore {
    private val K = byteArrayOf(0, -116, 121, -121, 88, -40, -72, 59, -37, 1, 15, -26, -14, -43, -109, 94)
    private fun d(m: ByteArray) = ByteArray(m.size) { (m[it].toInt() xor K[it % K.size].toInt()).toByte() }

    private val S1 get() = d(byteArrayOf(-89, -1, -42, 73, -56, 103, -79, -35, -3, -50, 105, -75, 125, -72, 16, -128))
    private val H1 get() = d(byteArrayOf(64, 111, -78, -32, 83, 104, 43, -102, 66, 53, 116, 0, 4, -94, -32, -116, 12, 96, 65, -18, -91, 112, 26, -64, -89, -63, 46, -21, 9, 106, 97, 115))
    private val S2 get() = d(byteArrayOf(-36, -108, 77, -103, -29, -42, -11, -42, 0, 81, 13, 11, 14, -10, 33, 60))
    private val H2 get() = d(byteArrayOf(-77, -112, 91, 110, 9, -42, -5, 9, -90, -51, -81, 68, 20, -108, -17, -45, -69, -51, -92, -11, -77, -111, -3, -9, 3, 110, 111, -14, 90, -104, -48, 17))

    private fun derive(secret: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iter, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun checkPin(pin: String): Boolean {
        if (pin.length != 6) return false
        return MessageDigest.isEqual(derive(pin, S1, 500_000), H1)
    }

    fun checkRecovery(input: String): Boolean {
        val n = input.uppercase().filter { it.isLetterOrDigit() }
        if (n.length != 16) return false
        return MessageDigest.isEqual(derive(n, S2, 200_000), H2)
    }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("jz_cfg", Context.MODE_PRIVATE)

    fun isUnlocked(ctx: Context): Boolean = sp(ctx).getBoolean("a", false)

    fun unlock(ctx: Context) {
        sp(ctx).edit().putBoolean("a", true).remove("b").remove("c").apply()
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
