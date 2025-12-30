# Google Play Store Publishing Guide

## Prerequisites Checklist

### 1. Google Play Developer Account
- [ ] Register at [Google Play Console](https://play.google.com/console)
- [ ] Pay $25 one-time registration fee
- [ ] Complete identity verification

### 2. Generate Release Keystore

Run this command once:
```bash
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias textredirect-key
```

You'll be asked for:
- Keystore password (remember this!)
- Key password (remember this!)
- Your name, organization, location details

**CRITICAL**: Back up `release-key.jks` and passwords securely! If you lose them, you can never update your app on Play Store.

### 3. Configure Keystore Properties

Edit `keystore.properties` (already created, git-ignored):
```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=textredirect-key
storeFile=release-key.jks
```

### 4. Build Release APK/AAB

For **AAB (Android App Bundle)** - preferred by Google Play:
```bash
./gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

For **APK** (alternative):
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

## Google Play Console Setup

### 1. Create App in Play Console

1. Go to [Play Console](https://play.google.com/console)
2. Click **Create app**
3. Fill in:
   - **App name**: TextRedirect
   - **Default language**: English (United States)
   - **App or game**: App
   - **Free or paid**: Free

### 2. Store Listing

Required information:

**App details**:
- **Short description** (80 chars):
  ```
  Forward SMS/RCS messages to Gmail automatically with beautiful email formatting
  ```

- **Full description** (4000 chars):
  ```
  TextRedirect automatically forwards your SMS, MMS, and RCS messages to your Gmail account with beautiful HTML email formatting.

  ✨ KEY FEATURES
  • 📨 Forward all message types (SMS, MMS, RCS)
  • 👤 Shows contact names when available
  • 🎨 Beautiful HTML email templates
  • 📊 Activity logs to track forwarded messages
  • 🔒 Secure OAuth 2.0 authentication
  • 🚫 No ads, no tracking
  • 📱 Works in background

  🔐 PRIVACY & SECURITY
  • Only forwards to YOUR Gmail account
  • Uses secure Google OAuth 2.0
  • No data stored on external servers
  • Open source on GitHub
  • Minimal permissions required

  📋 PERMISSIONS
  • Notification Access - To detect incoming messages
  • Contacts - To show sender names
  • Internet - To send emails via Gmail API
  • Accounts - For Google Sign-In

  💡 HOW IT WORKS
  1. Sign in with your Google account
  2. Grant notification access
  3. Enable SMS forwarding with toggle
  4. Done! Messages automatically forward to Gmail

  🛠️ SETUP
  After installation:
  1. Sign in with Google
  2. Grant notification access (Settings)
  3. Grant contacts permission
  4. Toggle "SMS Forwarding" ON

  📱 REQUIREMENTS
  • Android 7.0 (API 24) or higher
  • Google account
  • Active internet connection

  ℹ️ ABOUT
  Developed by Noe Rodriguez
  Open source: github.com/noahrod/TextRedirect
  Support development: buymeacoffee.com/noahrod
  ```

**Graphics** (required):
- **App icon**: 512x512 PNG (use your app icon)
- **Feature graphic**: 1024x500 PNG
- **Phone screenshots**: At least 2 (up to 8)
  - 16:9 or 9:16 aspect ratio
  - Minimum 320px on short edge

**Categorization**:
- **App category**: Communication
- **Tags**: messaging, email, forward, SMS, RCS

**Contact details**:
- **Email**: Your email address
- **Website**: https://github.com/noahrod/TextRedirect (optional)
- **Privacy policy URL**: Required (see below)

### 3. Privacy Policy

Google requires a privacy policy. Create one and host it on GitHub:

Create `PRIVACY_POLICY.md` in your repo:
```markdown
# Privacy Policy for TextRedirect

Last updated: December 29, 2025

## Data Collection
TextRedirect does not collect, store, or transmit any user data to external servers.

## Data Usage
- SMS/RCS messages are read only to forward them to YOUR Gmail account
- Contact names are read only to display in forwarded emails
- All data processing happens locally on your device
- No analytics or tracking

## Third-Party Services
- Google Sign-In: For authentication only
- Gmail API: To send emails to your account
- Governed by Google's privacy policy

## Permissions
- Notification Access: To detect incoming messages
- Contacts: To show contact names
- Internet: To send emails
- Accounts: For Google Sign-In

## Data Retention
No data is retained or stored by this application.

## Contact
For questions: [your-email@example.com]
GitHub: https://github.com/noahrod/TextRedirect
```

Then use the GitHub raw URL as your privacy policy URL:
```
https://raw.githubusercontent.com/noahrod/TextRedirect/main/PRIVACY_POLICY.md
```

### 4. Content Rating

1. Complete the questionnaire
2. For a messaging app, answer honestly about:
   - No violence
   - No sexual content
   - No controlled substances
   - User-generated content (messages)
3. You'll likely get: **Everyone** or **Teen** rating

### 5. Target Audience

- **Target age**: 18+
- **Appeals to children**: No

### 6. Data Safety

Answer questions about data collection:
- **Does your app collect data?**: Yes (email address for authentication)
- **Data types collected**: Email address
- **Is data shared?**: No
- **Can users request deletion?**: Yes (sign out)
- **Is data encrypted?**: Yes (OAuth 2.0)

### 7. App Access

- If your app needs special permissions testing, explain here
- For notification access, explain it's core functionality

### 8. Upload Release

1. Go to **Production** > **Create new release**
2. Upload your AAB file
3. Add **Release name**: `0.80` or `Version 0.80`
4. Add **Release notes**:
   ```
   Initial release
   
   Features:
   • Forward SMS/MMS/RCS to Gmail
   • Contact name display
   • HTML email formatting
   • Activity logs
   • Secure OAuth 2.0
   ```
5. Click **Save** then **Review release**
6. Fix any warnings/errors
7. Click **Start rollout to Production**

## Important Notes

### Version Management
For each new version:
1. Update `versionCode` (increment by 1)
2. Update `versionName` (e.g., "0.81", "1.0.0")
3. Build new AAB/APK
4. Upload in Play Console

### OAuth Configuration
Update your Google Cloud Console OAuth client:
- **Package name**: `com.thenoahnoah.textredirect`
- **SHA-1**: From your RELEASE keystore (not debug!)
  ```bash
  keytool -list -v -keystore release-key.jks -alias textredirect-key
  ```

### Review Process
- First review: 1-7 days typically
- Updates: Usually 1-2 days
- Be patient and check email for updates

### Testing
Before submitting:
1. Test on real device with release APK
2. Verify OAuth works with release keystore
3. Check all features work
4. Test on different Android versions if possible

## Post-Launch

- Monitor crash reports in Play Console
- Respond to user reviews
- Update regularly
- Track installs and ratings

## Useful Links

- [Play Console](https://play.google.com/console)
- [Launch checklist](https://developer.android.com/distribute/best-practices/launch/launch-checklist)
- [Content policies](https://play.google.com/about/developer-content-policy/)
- [Design guidelines](https://developer.android.com/distribute/best-practices/develop/quality-guidelines)
