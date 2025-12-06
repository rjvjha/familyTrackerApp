package com.example.familytracker.location
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * Handles all Telegram logic: Authentication, Persistence, and Sending Location.
 */
object TdLibManager {

    private var client: Client? = null
    private lateinit var prefs: SharedPreferences

    // Authentication States for the UI
    enum class AuthState {
        WAIT_PARAMETERS, WAIT_PHONE_NUMBER, WAIT_CODE, WAIT_PASSWORD, READY, UNKNOWN, LOGGING_OUT
    }

    private val _authState = MutableStateFlow(AuthState.UNKNOWN)
    val authState: StateFlow<AuthState> = _authState

    // The Telegram Chat ID of the person/saved-messages you want to send location to.
    private val _targetChatId = MutableStateFlow(0L)
    val targetChatId: StateFlow<Long> = _targetChatId
    
    // For backward compatibility with non-Compose code
    fun getTargetChatId(): Long = _targetChatId.value
    fun setTargetChatId(chatId: Long) {
        _targetChatId.value = chatId
    }

    // Key for saving data
    const val PREFS_NAME = "tdlib_prefs"
    private const val KEY_TARGET_CHAT_ID = "target_chat_id"

    fun initialize(context: Context) {
        if (client != null) return

        // 1. Setup Persistence
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        setTargetChatId(prefs.getLong(KEY_TARGET_CHAT_ID, 0L))

        // 2. Setup Database Directory
        val filesDir = File(context.filesDir, "tdlib")
        if (!filesDir.exists()) filesDir.mkdirs()

        // 3. Create the Client
        client = Client.create(
            { `object` ->
                if (`object` is TdApi.UpdateAuthorizationState) {
                    onAuthStateUpdated(`object`.authorizationState, filesDir.absolutePath)
                }
            },
            { exception ->
                Log.e("TdLibManager", "Update exception: ${exception.message}")
            },
            { exception ->
                Log.e("TdLibManager", "Default exception: ${exception.message}")
            }
        )

        // 4. Force start
        client?.send(TdApi.GetOption("version")) { }
    }

    private fun onAuthStateUpdated(authorizationState: TdApi.AuthorizationState, path: String) {
        when (authorizationState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val request = TdApi.SetTdlibParameters()
                request.databaseDirectory = path
                request.useMessageDatabase = true
                request.useSecretChats = true
                request.apiId = 39623874
                request.apiHash = "a2e68077f32e028027beb46a76bd4167"
                request.systemLanguageCode = "en"
                request.deviceModel = "Android Location Tracker"
                request.applicationVersion = "1.0"

                client?.send(request) { }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = AuthState.WAIT_PHONE_NUMBER
            is TdApi.AuthorizationStateWaitCode -> _authState.value = AuthState.WAIT_CODE
            is TdApi.AuthorizationStateWaitPassword -> _authState.value = AuthState.WAIT_PASSWORD
            is TdApi.AuthorizationStateReady -> _authState.value = AuthState.READY
            is TdApi.AuthorizationStateLoggingOut -> _authState.value = AuthState.LOGGING_OUT
            is TdApi.AuthorizationStateClosed -> {
                // Close current client and reinitialize for fresh restart
                client = null
                _authState.value = AuthState.WAIT_PARAMETERS
                // Reinitialize the client
                client = Client.create(
                    { `object` ->
                        if (`object` is TdApi.UpdateAuthorizationState) {
                            onAuthStateUpdated(`object`.authorizationState, path)
                        }
                    },
                    { exception ->
                        Log.e("TdLibManager", "Update exception: ${exception.message}")
                    },
                    { exception ->
                        Log.e("TdLibManager", "Default exception: ${exception.message}")
                    }
                )
                client?.send(TdApi.GetOption("version")) { }
            }
            else -> _authState.value = AuthState.UNKNOWN
        }
    }

    // --- Authentication Actions ---

    fun sendPhoneNumber(phone: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { }
    }

    fun sendAuthCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { }
    }

    fun sendPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { }
    }

    fun logOut() {
        client?.send(TdApi.LogOut()) { }
        // Clear saved target on logout
        setTargetChat(0L)
    }

    // --- Target User Management ---

    fun setTargetChat(id: Long) {
        setTargetChatId(id)
        prefs.edit().putLong(KEY_TARGET_CHAT_ID, id).apply()
    }

    fun resolveUsername(username: String, onSuccess: (Long) -> Unit, onError: (String) -> Unit) {
        client?.send(TdApi.SearchPublicChat(username)) { `object` ->
            if (`object` is TdApi.Chat) {
                setTargetChat(`object`.id) // Save it immediately
                onSuccess(`object`.id)
            } else if (`object` is TdApi.Error) {
                onError(`object`.message)
            }
        }
    }

    // --- Core Feature: Send Location ---

    fun sendLocation(latitude: Double, longitude: Double, familyPersonName: String = "Unknown") {
        if (_authState.value != AuthState.READY || getTargetChatId() == 0L) {
            return
        }

        val timestamp = java.text.SimpleDateFormat("EEEE, MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        
        // Send personalized text message with location details
        val textContent = TdApi.InputMessageText(
            TdApi.FormattedText("📍 $familyPersonName's Location\nCoordinates: $latitude, $longitude\nTime: $timestamp", null),
            null,
            true
        )

        val textMessage = TdApi.SendMessage()
        textMessage.chatId = getTargetChatId()
        textMessage.inputMessageContent = textContent

        client?.send(textMessage) { result ->
            if (result is TdApi.Error) {
                Log.e("TdLibManager", "Error sending text message for $familyPersonName: ${result.message}")
            } else {
                // After text is sent, send the location pin
                sendLocationPin(latitude, longitude, familyPersonName)
            }
        }
    }

    private fun sendLocationPin(latitude: Double, longitude: Double, familyPersonName: String) {
        // We use livePeriod = 0 to send "Breadcrumbs" (Static points).
        // This creates a history of movement in the chat.
        val locationContent = TdApi.InputMessageLocation(
            TdApi.Location(latitude, longitude, 0.0),
            0, // Live period (0 = static message)
            0,
            0
        )

        val locationMessage = TdApi.SendMessage()
        locationMessage.chatId = getTargetChatId()
        locationMessage.inputMessageContent = locationContent

        client?.send(locationMessage) { result ->
            if (result is TdApi.Error) {
                Log.e("TdLibManager", "Error sending location pin for $familyPersonName: ${result.message}")
            } else {
                Log.d("TdLibManager", "Location pin sent for $familyPersonName: $latitude, $longitude")
            }
        }
    }
}