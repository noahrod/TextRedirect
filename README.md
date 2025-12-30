# TextRedirect

<p align="center">
  <img src="docs/logo.png" alt="TextRedirect logo" width="120" height="120" style="border-radius: 20%;" />
</p>

<p align="center">
  Forward your SMS, MMS, and RCS messages straight to your Gmail inbox.
</p>

<p align="center">
  <a href="https://noahrod.github.io/TextRedirect/">Website</a>
  ·
  <a href="https://github.com/noahrod/TextRedirect/releases">Downloads</a>
  ·
  <a href="https://github.com/noahrod/TextRedirect/issues">Issue Tracker</a>
</p>

---

## Overview

TextRedirect is an Android app that automatically forwards SMS, MMS, and RCS messages from your device to your Gmail account using the official Gmail API and OAuth&nbsp;2.0.

- 📨 Forwards SMS, MMS, and RCS
- 👤 Shows contact names when available
- 🎨 Beautiful HTML email layouts
- 📊 In-app activity logs
- 🔒 No external servers, uses your own Gmail account

The app uses a `NotificationListenerService` to detect new message notifications and a foreground service to format and send emails via the Gmail API.

## Getting Started

### Requirements

- Android 7.0 (API 24) or higher
- A Google account
- Internet connection

### Install

1. Download the latest APK from the [Releases](https://github.com/noahrod/TextRedirect/releases) page.
2. Enable "Install from Unknown Sources" on your Android device, if needed.
3. Install the APK.

### Setup in the App

1. Open TextRedirect.
2. Sign in with your Google account (Gmail).
3. Grant **notification access** so the app can detect incoming messages.
4. Grant **contacts permission** so the app can resolve sender names.
5. Toggle **SMS/RCS Forwarding** to **ON**.

New messages will now be forwarded to your Gmail inbox with a clean HTML layout.

> This app is especially useful for forwarding OTP (one‑time passcode) messages when you don’t have your phone nearby but still need to access codes from your email.

## Building from Source

1. Clone the repository:

```bash
git clone https://github.com/noahrod/TextRedirect.git
cd TextRedirect
```

2. Open the project in Android Studio (Giraffe or newer recommended).
3. Configure the Gmail API & OAuth 2.0 as described in `SETUP_INSTRUCTIONS.md`.
4. Build and run on a real device.

For release builds and Play Store publishing, see `GOOGLE_PLAY_GUIDE.md`.

## GitHub Pages Site

A simple marketing site for TextRedirect is hosted with GitHub Pages:

- Source: `docs/index.html`
- Live: https://noahrod.github.io/TextRedirect/

Any changes pushed to the `docs/` folder on `main` are automatically deployed via the GitHub Actions workflow in `.github/workflows/deploy-pages.yml`.

## Contributing

Contributions, bug reports, and feature requests are very welcome!

If you’d like to contribute:

1. **Fork** the repository.
2. Create a new branch for your feature or fix:

   ```bash
   git checkout -b feature/my-improvement
   ```

3. Make your changes and add tests or documentation where appropriate.
4. Run the app and/or tests to ensure everything works.
5. Open a **pull request** against the `main` branch with a clear description.

You can also support the project by:

- ⭐ Starring the repository
- 🐞 Reporting bugs
- ☕ Supporting via [Buy Me a Coffee](https://buymeacoffee.com/noahrod)

## Code of Conduct

To keep this project welcoming and respectful, all contributors are expected to follow these basic guidelines:

- Be **respectful** and **inclusive**. Treat everyone with kindness regardless of experience level, background, or identity.
- Focus on **constructive feedback**. Critique code and ideas, not people.
- Assume good intentions. Mistakes happen; help others learn instead of blaming.
- Avoid harassment, hate speech, or any kind of discriminatory language or behavior.
- Respect project maintainers’ decisions and guidelines.

If you experience or witness unacceptable behavior within the project community (issues, discussions, pull requests), please contact the repository owner privately via the email listed in their GitHub profile.

Repeated or serious violations may result in comments being hidden, issues or PRs being closed, or contributors being blocked from participating in the project.

## License

This project is free software licensed under the **GNU General Public License v3.0 (GPLv3)**.

You can find the full license text in the [`LICENSE`](LICENSE) file in this repository.
