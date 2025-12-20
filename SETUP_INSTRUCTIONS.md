# Text Redirect - Gmail API Setup Instructions

## Complete Setup Steps

### 1. Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Note your Project ID

### 2. Enable Gmail API

1. In Google Cloud Console, go to **APIs & Services > Library**
2. Search for "Gmail API"
3. Click on it and press **Enable**

### 3. Configure OAuth Consent Screen

1. Go to **APIs & Services > OAuth consent screen**
2. Choose **External** user type
3. Fill in required fields:
   - App name: Text Redirect
   - User support email: Your email
   - Developer contact: Your email
4. Add scopes:
   - Click **Add or Remove Scopes**
   - Search for and add: `https://www.googleapis.com/auth/gmail.send`
5. Add test users (your Gmail account)
6. Save and continue

### 4. Create OAuth 2.0 Credentials

1. Go to **APIs & Services > Credentials**
2. Click **Create Credentials > OAuth 2.0 Client ID**
3. Choose **Android** as application type
4. For the package name, enter: `com.thenoahnoah.textredirect`
5. Get your SHA-1 certificate fingerprint:
   
   **For Debug Build:**
   ```bash
   cd ~/.android
   keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   
   **For Release Build:**
   ```bash
   keytool -list -v -keystore /path/to/your/release.keystore -alias your_alias
   ```

6. Copy the SHA-1 fingerprint and paste it in the Google Cloud Console
7. Click **Create**

### 5. Download google-services.json

1. Go to **Firebase Console** (https://console.firebase.google.com/)
2. Add your app to Firebase or use existing project
3. Register your app with package name: `com.thenoahnoah.textredirect`
4. Download the `google-services.json` file
5. Replace the placeholder file at:
   ```
   app/google-services.json
   ```

### 6. Update build.gradle.kts

The Google Services plugin needs to be applied. Update your `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") // Make sure this is applied
}
```

### 7. Sync and Build

1. Sync Gradle files in Android Studio
2. Build the project
3. Run on a device (not emulator for SMS testing)

## How It Works

1. **Sign In**: User signs in with Google account
2. **Grant Permissions**: App requests SMS and notification permissions
3. **Enable Service**: Toggle switch to enable SMS forwarding
4. **Automatic Forwarding**: When SMS arrives:
   - App intercepts the message
   - Starts foreground service
   - Uses Gmail API with OAuth 2.0 to send email
   - Email sent to the signed-in Gmail account

## Important Notes

- ⚠️ **OAuth 2.0 requires proper setup** - The app will not work without completing all steps above
- 📱 **Test on real device** - SMS features don't work on emulators
- 🔐 **Gmail API Scope** - Only requests `gmail.send` permission (minimal access)
- 🔄 **Token Management** - Google Sign-In handles token refresh automatically
- ⏱️ **Foreground Service** - Shows notification while forwarding messages

## Troubleshooting

### "Sign in failed" error
- Check that SHA-1 fingerprint matches your keystore
- Verify package name is correct
- Ensure Gmail API is enabled
- Check OAuth consent screen is configured

### Messages not forwarding
- Verify user is signed in
- Check all permissions are granted
- Enable service with toggle
- Check logcat for errors

### "Not signed in to Gmail" notification
- User needs to sign in through the app
- Check OAuth 2.0 credentials are correct
- Verify google-services.json is properly configured
