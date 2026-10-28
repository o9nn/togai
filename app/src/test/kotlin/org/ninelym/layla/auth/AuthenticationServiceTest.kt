package org.ninelym.layla.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AuthenticationServiceTest {

    private val dir = Files.createTempDirectory("auth-test").toFile()

    @Test
    fun `sign in succeeds only with the registered password`() = runBlocking {
        val auth = AuthenticationService(dir)
        assertTrue(auth.signIn("sam@example.com", "whatever1").isFailure)

        val user = auth.signUp("sam@example.com", "correct-horse", "Sam").getOrThrow()
        auth.signOut()

        assertTrue(auth.signIn("sam@example.com", "wrong-password").isFailure)
        assertEquals(user.id, auth.signIn("sam@example.com", "correct-horse").getOrThrow().id)
        assertTrue(auth.signUp("sam@example.com", "another-pass", "Sam").isFailure)
    }

    @Test
    fun `session survives a restart and OAuth or biometrics never fake success`() = runBlocking {
        AuthenticationService(dir).signUp("kai@example.com", "long-password", "Kai").getOrThrow()

        val restarted = AuthenticationService(dir)
        restarted.initialize().getOrThrow()
        assertTrue(restarted.isAuthenticated())
        assertTrue(restarted.signInWithOAuth(OAuthProvider.GITHUB).isFailure)
        assertTrue(restarted.authenticateWithBiometrics().isFailure)
    }
}
