package org.ninelym.layla.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Authentication Service
 * 
 * Local, on-device accounts: passwords are stored as salted PBKDF2 hashes and sessions are
 * persisted in [storageDir]. External identity providers (OAuth) and biometrics need platform
 * integrations that aren't bundled, so those calls fail rather than pretending to succeed.
 */
class AuthenticationService(
    private val storageDir: File = File(System.getProperty("java.io.tmpdir"), "togai-auth")
) {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private val accountsFile get() = File(storageDir, "accounts.json")
    private val sessionFile get() = File(storageDir, "session.json")
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser
    
    private var sessionToken: String? = null
    private var refreshToken: String? = null
    
    @Serializable
    private data class StoredAccount(
        val id: String,
        val email: String,
        val name: String,
        val createdAt: Long,
        val salt: String,
        val hash: String
    )
    
    @Serializable
    private data class StoredSession(
        val userId: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long
    )
    
    /**
     * Initialize authentication service
     */
    suspend fun initialize(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Restore a saved, unexpired session
                val savedSession = loadSavedSession()
                if (savedSession != null) {
                    if (validateSession(savedSession)) {
                        sessionToken = savedSession.accessToken
                        refreshToken = savedSession.refreshToken
                        _currentUser.value = savedSession.user
                        _authState.value = AuthState.Authenticated(savedSession.user)
                    } else {
                        refreshSession(savedSession)
                    }
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Sign in with email and password against the local account store
     */
    suspend fun signIn(email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                _authState.value = AuthState.Authenticating
                
                val account = loadAccounts()[email.lowercase()]
                    ?: throw SecurityException("No account for $email")
                if (!hash(password, Base64.getDecoder().decode(account.salt)).contentEquals(Base64.getDecoder().decode(account.hash))) {
                    throw SecurityException("Incorrect password")
                }
                
                val user = User(id = account.id, email = account.email, name = account.name, createdAt = account.createdAt)
                startSession(user)
                Result.success(user)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
                Result.failure(e)
            }
        }
    }
    
    /**
     * Sign in with OAuth provider (Google, GitHub, etc.)
     */
    suspend fun signInWithOAuth(provider: OAuthProvider): Result<User> {
        val error = UnsupportedOperationException(
            "Sign-in with ${provider.name} needs an OAuth client (e.g. AppAuth) configured for this app"
        )
        _authState.value = AuthState.Error(error.message!!)
        return Result.failure(error)
    }
    
    /**
     * Sign up new user
     */
    suspend fun signUp(email: String, password: String, name: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                _authState.value = AuthState.Authenticating
                
                // Validate email and password
                if (!isValidEmail(email)) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid email address")
                    )
                }
                
                if (password.length < 8) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Password must be at least 8 characters")
                    )
                }
                
                val accounts = loadAccounts().toMutableMap()
                if (email.lowercase() in accounts) {
                    return@withContext Result.failure(IllegalStateException("An account for $email already exists"))
                }
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val user = User(
                    id = UUID.randomUUID().toString(),
                    email = email,
                    name = name,
                    createdAt = System.currentTimeMillis()
                )
                accounts[email.lowercase()] = StoredAccount(
                    id = user.id, email = email, name = name, createdAt = user.createdAt,
                    salt = Base64.getEncoder().encodeToString(salt),
                    hash = Base64.getEncoder().encodeToString(hash(password, salt))
                )
                saveAccounts(accounts)
                
                startSession(user)
                Result.success(user)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign up failed")
                Result.failure(e)
            }
        }
    }
    
    /**
     * Sign out current user
     */
    suspend fun signOut(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Clear tokens
                sessionToken = null
                refreshToken = null
                
                // Clear saved session
                clearSavedSession()
                
                _currentUser.value = null
                _authState.value = AuthState.Unauthenticated
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get current access token
     */
    fun getAccessToken(): String? {
        return sessionToken
    }
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }
    
    /**
     * Authenticate with biometrics
     */
    suspend fun authenticateWithBiometrics(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Biometric authentication needs an Activity-hosted BiometricPrompt"))
    
    /**
     * Shutdown the service
     */
    fun shutdown() {
        scope.cancel()
    }
    
    // Private helper methods
    
    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
    
    private fun loadAccounts(): Map<String, StoredAccount> =
        if (accountsFile.isFile) json.decodeFromString<Map<String, StoredAccount>>(accountsFile.readText()) else emptyMap()
    
    private fun saveAccounts(accounts: Map<String, StoredAccount>) {
        storageDir.mkdirs()
        accountsFile.writeText(json.encodeToString(accounts))
    }
    
    private fun startSession(user: User) {
        sessionToken = generateToken()
        refreshToken = generateToken()
        saveSession(
            Session(
                user = user,
                accessToken = sessionToken!!,
                refreshToken = refreshToken!!,
                expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours
            )
        )
        _currentUser.value = user
        _authState.value = AuthState.Authenticated(user)
    }
    
    private fun loadSavedSession(): Session? {
        if (!sessionFile.isFile) return null
        val stored = json.decodeFromString<StoredSession>(sessionFile.readText())
        val account = loadAccounts().values.firstOrNull { it.id == stored.userId } ?: return null
        return Session(
            user = User(id = account.id, email = account.email, name = account.name, createdAt = account.createdAt),
            accessToken = stored.accessToken,
            refreshToken = stored.refreshToken,
            expiresAt = stored.expiresAt
        )
    }
    
    private fun saveSession(session: Session) {
        storageDir.mkdirs()
        sessionFile.writeText(json.encodeToString(StoredSession(session.user.id, session.accessToken, session.refreshToken, session.expiresAt)))
    }
    
    private fun clearSavedSession() {
        sessionFile.delete()
    }
    
    private fun validateSession(session: Session): Boolean {
        return session.expiresAt > System.currentTimeMillis()
    }
    
    /** Expired local sessions require signing in again. */
    private fun refreshSession(session: Session) {
        clearSavedSession()
        _authState.value = AuthState.Unauthenticated
    }
    
    private fun generateToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun isValidEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }
}

/**
 * Authentication state
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * User model
 */
data class User(
    val id: String,
    val email: String,
    val name: String,
    val createdAt: Long,
    val profileImageUrl: String? = null,
    val oauthProvider: OAuthProvider? = null
)

/**
 * Session model
 */
data class Session(
    val user: User,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

/**
 * OAuth providers
 */
enum class OAuthProvider {
    GOOGLE,
    GITHUB,
    APPLE,
    MICROSOFT
}
