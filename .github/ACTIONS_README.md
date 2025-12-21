# GitHub Actions Setup

This repository has two GitHub Actions workflows for building and releasing APKs.

## Workflows

### 1. Build Debug APK (Recommended for Quick Releases)
**File**: `.github/workflows/build-debug.yml`

This workflow builds a debug APK (no signing required) and creates a GitHub release.

**Triggers**:
- Automatically when you push a version tag (e.g., `v0.80`, `v1.0.0`)
- Manually from the GitHub Actions tab

**To create a release**:
```bash
# Create and push a version tag
git tag v0.80
git push origin v0.80
```

The workflow will:
1. Build the debug APK
2. Create a GitHub release with the version tag
3. Attach the APK to the release
4. Generate release notes automatically

### 2. Build Release APK (For Production)
**File**: `.github/workflows/release.yml`

This workflow builds a signed release APK for production use.

**Setup required**:
You need to add these secrets to your GitHub repository (Settings → Secrets and variables → Actions):

1. **SIGNING_KEY**: Base64 encoded keystore file
   ```bash
   # Generate keystore if you don't have one
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
   
   # Encode to base64
   base64 my-release-key.jks | tr -d '\n' > keystore.base64.txt
   # Copy the content of keystore.base64.txt to SIGNING_KEY secret
   ```

2. **ALIAS**: Your keystore alias (e.g., `my-key-alias`)
3. **KEY_STORE_PASSWORD**: Your keystore password
4. **KEY_PASSWORD**: Your key password

**To create a release**:
```bash
git tag v0.80
git push origin v0.80
```

## Manual Trigger

You can also trigger builds manually:
1. Go to your repository on GitHub
2. Click "Actions" tab
3. Select the workflow you want to run
4. Click "Run workflow" button
5. Choose the branch and click "Run workflow"

## Version Numbering

Use semantic versioning for tags:
- `v0.80` - Current version
- `v0.81` - Bug fix release
- `v1.0.0` - Major release
- `v1.1.0` - Minor feature release

## What Gets Created

Each release includes:
- ✅ APK file attached to the release
- ✅ Automatically generated release notes
- ✅ Installation instructions
- ✅ Feature list
- ✅ Changelog comparison link

## First Time Setup

1. **Enable GitHub Actions** (if not already enabled):
   - Go to repository Settings → Actions → General
   - Enable "Allow all actions and reusable workflows"

2. **Verify workflow files**:
   - Check that `.github/workflows/` folder exists
   - Ensure workflow files are committed to the repository

3. **Create your first release**:
   ```bash
   git tag v0.80
   git push origin v0.80
   ```

4. **Check the Actions tab** to see the workflow running

5. **Find your release** in the Releases section once complete

That's it! Your app will now automatically build and release whenever you push a version tag. 🚀
