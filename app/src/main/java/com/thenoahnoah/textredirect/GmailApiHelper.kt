package com.thenoahnoah.textredirect

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class GmailApiHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "GmailApiHelper"
        const val GMAIL_SEND_SCOPE = GmailScopes.GMAIL_SEND

        fun getGoogleSignInOptions(context: Context): GoogleSignInOptions {
            // Remove .requestIdToken() - it requires web client ID which causes Error 10
            // Android OAuth clients work automatically with package name + SHA-1
            return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(GMAIL_SEND_SCOPE))
                .build()
        }
    }

    private fun getCredential(): GoogleAccountCredential? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            Log.e(TAG, "No signed-in account found")
            return null
        }
        
        Log.d(TAG, "Creating credential for account: ${account.email}")
        Log.d(TAG, "Granted scopes: ${account.grantedScopes}")

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(GMAIL_SEND_SCOPE)
        ).apply {
            selectedAccount = account.account
        }
        
        Log.d(TAG, "Credential created successfully")
        return credential
    }

    private fun getGmailService(): Gmail? {
        val credential = getCredential() ?: return null
        
        return Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Text Redirect")
            .build()
    }

    suspend fun sendEmail(
        toEmail: String,
        subject: String,
        bodyText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "sendEmail called for: $toEmail")
            
            val gmailService = getGmailService()
            if (gmailService == null) {
                val error = "Gmail service not initialized. User may not be signed in."
                Log.e(TAG, error)
                return@withContext Result.failure(Exception(error))
            }

            Log.d(TAG, "Creating email message...")
            val email = createEmail(toEmail, toEmail, subject, bodyText)
            val message = createMessageWithEmail(email)
            
            Log.d(TAG, "Sending via Gmail API...")
            val sentMessage = gmailService.users().messages().send("me", message).execute()
            
            Log.d(TAG, "Email sent successfully to $toEmail. Message ID: ${sentMessage.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email", e)
            Result.failure(e)
        }
    }

    private fun createEmail(
        to: String,
        from: String,
        subject: String,
        bodyText: String
    ): MimeMessage {
        val props = Properties()
        val session = Session.getDefaultInstance(props, null)
        
        val email = MimeMessage(session)
        email.setFrom(InternetAddress(from))
        email.addRecipient(
            javax.mail.Message.RecipientType.TO,
            InternetAddress(to)
        )
        email.subject = subject
        email.setText(bodyText)
        
        return email
    }

    private fun createMessageWithEmail(emailContent: MimeMessage): Message {
        val buffer = ByteArrayOutputStream()
        emailContent.writeTo(buffer)
        val bytes = buffer.toByteArray()
        val encodedEmail = android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        
        val message = Message()
        message.raw = encodedEmail
        return message
    }

    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            Log.d(TAG, "No account signed in")
            return false
        }
        
        Log.d(TAG, "Account found: ${account.email}")
        Log.d(TAG, "Granted scopes: ${account.grantedScopes}")
        
        // Return true if account exists (scope will be requested when needed)
        return true
    }

    private fun hasGmailScope(account: GoogleSignInAccount): Boolean {
        val grantedScopes = account.grantedScopes
        return grantedScopes.any { it.scopeUri == GMAIL_SEND_SCOPE }
    }

    fun getUserEmail(): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.email
    }
}
