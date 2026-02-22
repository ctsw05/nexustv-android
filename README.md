# Nexus TV — Android APK

📺 **[Install Guide for Google TV / Android TV](INSTALL.md)** &nbsp;|&nbsp; 🌐 **[Website](https://ctsw05.github.io/nexustv-android)**

An Android TV IPTV player with M3U playlist support, EPG guide, and automatic updates via GitHub Releases.

---

## First-Time GitHub Setup

### 1. Install Git (if you haven't)
Download from https://git-scm.com and install it.

### 2. Create the repo on GitHub
1. Go to https://github.com/new
2. Name it `nexustv-android`
3. Set to **Public**
4. Do NOT check "Initialize with README" (you already have one)
5. Click **Create repository**

### 3. Push this project to GitHub
Open a terminal in the project folder and run:

```bash
git init
git add .
git commit -m "Initial release v2.0.0"
git branch -M main
git remote add origin https://github.com/ctsw05/nexustv-android.git
git push -u origin main
```

---

## How to Release an Update

Every time you want to push a new version to users:

### Step 1 — Bump the version in `app/build.gradle`
```gradle
versionCode 20001       # increment by 1 each release
versionName "2.0.1"     # human-readable version
```

### Step 2 — Build the release APK in Android Studio
- Build → Generate Signed Bundle / APK → APK
- Use your keystore (or create one the first time)
- Output will be in `app/release/app-release.apk`

### Step 3 — Commit and push
```bash
git add .
git commit -m "Release v2.0.1"
git push
```

### Step 4 — Create a GitHub Release
1. Go to https://github.com/ctsw05/nexustv-android/releases/new
2. Tag: `v2.0.1` (must match versionName with a `v` prefix)
3. Title: `Nexus TV v2.0.1`
4. Drag and drop your `app-release.apk` into the assets section
5. Click **Publish release**

That's it! The next time a user launches the app it will detect the new version and prompt them to update.

---

## Version Code Format

| versionName | versionCode |
|-------------|-------------|
| 2.0.0       | 20000       |
| 2.0.1       | 20001       |
| 2.1.0       | 21000       |
| 3.0.0       | 30000       |

Always increment `versionCode` — the app uses this number to detect if an update is newer.

---

## How the Auto-Update Works

On launch, the app silently calls:
```
https://api.github.com/repos/ctsw05/nexustv-android/releases/latest
```
It compares the tag version to the installed `versionCode`. If the remote version is higher, it shows a dialog asking the user to update. If they accept, it downloads the APK and launches the Android installer automatically.
