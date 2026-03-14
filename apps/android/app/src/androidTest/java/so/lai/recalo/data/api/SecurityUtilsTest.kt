package so.lai.recalo.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Encryption/decryption tests for SecurityUtils
 *
 * Run as androidTest (physical device/emulator) as it uses Android Keystore
 */
@RunWith(AndroidJUnit4::class)
class SecurityUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun encrypt_and_decrypt_roundtrip_returns_original_text() {
        val original = "sk-test-api-key-12345"
        val encrypted = SecurityUtils.encrypt(context, original)
        val decrypted = SecurityUtils.decrypt(context, encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun encrypt_produces_different_ciphertexts_for_same_plaintext() {
        val original = "same-key"
        val encrypted1 = SecurityUtils.encrypt(context, original)
        val encrypted2 = SecurityUtils.encrypt(context, original)
        assertNotEquals(encrypted1, encrypted2)
    }

    @Test(expected = Exception::class)
    fun decrypt_with_invalid_input_throws_exception() {
        SecurityUtils.decrypt(context, "not-valid-base64!!!")
    }

    @Test
    fun encrypt_and_decrypt_empty_string() {
        val original = ""
        val encrypted = SecurityUtils.encrypt(context, original)
        val decrypted = SecurityUtils.decrypt(context, encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun encrypt_and_decrypt_long_text() {
        val original = "a".repeat(10000)
        val encrypted = SecurityUtils.encrypt(context, original)
        val decrypted = SecurityUtils.decrypt(context, encrypted)
        assertEquals(original, decrypted)
    }
}
