package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Splitwise OAuth authentication and API calls.
 *
 * SETUP REQUIRED:
 * 1. Go to https://secure.splitwise.com/apps
 * 2. Create a new app with these settings:
 *    - Name: Expense Intelligence
 *    - Callback URL: expenseintel://splitwise/callback
 * 3. Add to local.properties:
 *    SPLITWISE_CLIENT_ID=your_client_id
 *    SPLITWISE_CLIENT_SECRET=your_client_secret
 *
 * HOW IT WORKS:
 * 1. User taps "Connect Splitwise" → opens browser for OAuth
 * 2. User authorizes → browser redirects to expenseintel://splitwise/callback?code=XXX
 * 3. We exchange the code for an access token
 * 4. Token is stored locally for future API calls
 */
object SplitwiseManager {

    private val TAG = "SPLITWISE"

    // OAuth endpoints
    private const val AUTH_URL = "https://secure.splitwise.com/oauth/authorize"
    private const val TOKEN_URL = "https://secure.splitwise.com/oauth/token"
    private const val API_BASE = "https://secure.splitwise.com/api/v3.0"

    // Deep link callback
    private const val REDIRECT_URI = "expenseintel://splitwise/callback"

    // Preference keys
    private const val PREFS_NAME = "splitwise_prefs"
    private const val PREF_ACCESS_TOKEN = "access_token"
    private const val PREF_USER_ID = "user_id"
    private const val PREF_USER_NAME = "user_name"

    // ═══════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════

    data class SplitwiseUser(
        val id: Long,
        val firstName: String,
        val lastName: String?,
        val email: String?
    )

    data class SplitwiseGroup(
        val id: Long,
        val name: String,
        val members: List<SplitwiseMember>
    )

    data class SplitwiseMember(
        val id: Long,
        val firstName: String,
        val lastName: String?
    ) {
        val displayName: String
            get() = if (lastName.isNullOrBlank()) firstName else "$firstName ${lastName.first()}."
    }

    // ═══════════════════════════════════════════════════════════════
    // AUTH METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if user is logged into Splitwise.
     */
    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_ACCESS_TOKEN, null) != null
    }

    /**
     * Get the current user's ID (needed for creating expenses).
     */
    fun getCurrentUserId(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(PREF_USER_ID, 0L)
    }

    /**
     * Start OAuth login flow — opens Splitwise in browser.
     */
    fun startLogin(context: Context) {
        val clientId = BuildConfig.SPLITWISE_CLIENT_ID
        if (clientId.isBlank()) {
            Log.e(TAG, "❌ SPLITWISE_CLIENT_ID not set in local.properties")
            return
        }

        val authUrl = Uri.parse(AUTH_URL)
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .build()
            .toString()

        Log.d(TAG, "🔐 Opening Splitwise OAuth: $authUrl")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Handle OAuth callback — exchange code for access token.
     * Call this from your Activity that handles the deep link.
     */
    suspend fun handleCallback(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        val clientId = BuildConfig.SPLITWISE_CLIENT_ID
        val clientSecret = BuildConfig.SPLITWISE_CLIENT_SECRET

        if (clientId.isBlank() || clientSecret.isBlank()) {
            Log.e(TAG, "❌ Splitwise credentials not configured")
            return@withContext false
        }

        try {
            Log.d(TAG, "🔄 Exchanging auth code for token...")

            val url = URL(TOKEN_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val body = listOf(
                "grant_type=authorization_code",
                "code=$code",
                "client_id=$clientId",
                "client_secret=$clientSecret",
                "redirect_uri=$REDIRECT_URI"
            ).joinToString("&")

            OutputStreamWriter(connection.outputStream).use { it.write(body) }

            if (connection.responseCode != 200) {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown"
                Log.e(TAG, "❌ Token exchange failed: ${connection.responseCode} — $error")
                return@withContext false
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val accessToken = json.getString("access_token")

            // Save token
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_ACCESS_TOKEN, accessToken)
                .apply()

            Log.d(TAG, "✅ Splitwise login successful!")

            // Fetch and save current user info
            fetchAndSaveCurrentUser(context)

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ OAuth error: ${e.message}")
            false
        }
    }

    /**
     * Fetch current user info and save to prefs.
     */
    private suspend fun fetchAndSaveCurrentUser(context: Context) {
        val user = getCurrentUser(context)
        if (user != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_USER_ID, user.id)
                .putString(PREF_USER_NAME, user.firstName)
                .apply()
            Log.d(TAG, "👤 Saved user: ${user.firstName} (ID: ${user.id})")
        }
    }

    /**
     * Logout — clear stored tokens.
     */
    fun logout(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        Log.d(TAG, "🚪 Logged out of Splitwise")
    }

    // ═══════════════════════════════════════════════════════════════
    // API METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get current user info.
     */
    suspend fun getCurrentUser(context: Context): SplitwiseUser? = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken(context) ?: return@withContext null

        try {
            val url = URL("$API_BASE/get_current_user")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200) {
                Log.e(TAG, "❌ Get user failed: ${connection.responseCode}")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response).getJSONObject("user")

            SplitwiseUser(
                id = json.getLong("id"),
                firstName = json.getString("first_name"),
                lastName = json.optString("last_name", null),
                email = json.optString("email", null)
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching user: ${e.message}")
            null
        }
    }

    /**
     * Fetch user's Splitwise groups.
     */
    suspend fun getGroups(context: Context): List<SplitwiseGroup> = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken(context) ?: return@withContext emptyList()

        try {
            val url = URL("$API_BASE/get_groups")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200) {
                Log.e(TAG, "❌ Get groups failed: ${connection.responseCode}")
                return@withContext emptyList()
            }

            val response = connection.inputStream.bufferedReader().readText()
            val groupsArray = JSONObject(response).getJSONArray("groups")

            (0 until groupsArray.length()).mapNotNull { i ->
                val g = groupsArray.getJSONObject(i)
                val groupId = g.getLong("id")

                // Skip "non-group expenses" (id = 0)
                if (groupId == 0L) return@mapNotNull null

                val membersArray = g.getJSONArray("members")
                val members = (0 until membersArray.length()).map { j ->
                    val m = membersArray.getJSONObject(j)
                    SplitwiseMember(
                        id = m.getLong("id"),
                        firstName = m.getString("first_name"),
                        lastName = m.optString("last_name", null)
                    )
                }

                SplitwiseGroup(
                    id = groupId,
                    name = g.getString("name"),
                    members = members
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching groups: ${e.message}")
            emptyList()
        }
    }

    /**
     * Create an expense in Splitwise.
     *
     * @param description What the expense was for
     * @param amount Total amount in INR
     * @param groupId Which group to add it to
     * @param splitWithIds Member IDs to split with (excluding current user)
     * @param paidByUserId Who paid (usually current user)
     */
    suspend fun createExpense(
        context: Context,
        description: String,
        amount: Double,
        groupId: Long,
        splitWithIds: List<Long>,
        paidByUserId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken(context) ?: return@withContext false

        try {
            val url = URL("$API_BASE/create_expense")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // Calculate equal split
            val totalPeople = splitWithIds.size + 1  // including the payer
            val sharePerPerson = amount / totalPeople

            // Build users array with shares
            val usersArray = JSONArray()

            // Payer's share (paid full amount, owes their portion)
            usersArray.put(JSONObject().apply {
                put("user_id", paidByUserId)
                put("paid_share", String.format("%.2f", amount))
                put("owed_share", String.format("%.2f", sharePerPerson))
            })

            // Others' shares (paid nothing, owe their portion)
            splitWithIds.forEach { memberId ->
                usersArray.put(JSONObject().apply {
                    put("user_id", memberId)
                    put("paid_share", "0.00")
                    put("owed_share", String.format("%.2f", sharePerPerson))
                })
            }

            val body = JSONObject().apply {
                put("cost", String.format("%.2f", amount))
                put("description", description)
                put("group_id", groupId)
                put("currency_code", "INR")
                put("split_equally", false)
                put("users", usersArray)
            }

            Log.d(TAG, "📤 Creating expense: $body")

            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            val responseCode = connection.responseCode
            val success = responseCode in 200..299

            if (success) {
                Log.d(TAG, "✅ Expense created in Splitwise!")
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown"
                Log.e(TAG, "❌ Create expense failed: $responseCode — $error")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating expense: ${e.message}")
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private fun getAccessToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_ACCESS_TOKEN, null)
    }
}

