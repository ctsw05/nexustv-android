# How to Install Nexus TV on Google TV / Android TV

Nexus TV is a free IPTV player. Since it's not on the Google Play Store, you install it in a few easy steps. Takes about 2 minutes.

---

## Step 1 — Enable Sideloading on Your TV

You only need to do this once.

1. On your Google TV home screen go to **Settings** (top right gear icon)
2. Scroll down to **System** → **About**
3. Scroll to **Android TV OS build** and click it **7 times** until you see "You are now a developer"
4. Go back to **Settings** → **System** → **Developer options**
5. Turn on **Allow apps from unknown sources** (or find it under Settings → Privacy → Security & Restrictions on some TVs)

---

## Step 2 — Install the Downloader App

Downloader is a free app on the Play Store that lets you install APKs from a URL.

1. On your Google TV press the **Home** button
2. Go to the **Apps** section and search for **Downloader**
3. It's made by **AFTVnews** — install it
4. Open Downloader and click **Allow** when it asks for storage permission

---

## Step 3 — Install Nexus TV

1. Open the **Downloader** app
2. Click the URL bar and type this address exactly:

```
https://github.com/ctsw05/nexustv-android/releases/latest/download/nexustv.apk
```

> **Tip:** If typing on a TV remote is painful, use the short code instead (see below)

3. Click **Go** — it will download the APK
4. When it finishes click **Install**
5. Click **Done** (not Open) when it finishes
6. Downloader will ask if you want to delete the APK file — click **Delete** to save space

---

## Short Code (Easier Than Typing the URL)

Instead of typing the full URL in Step 3, you can just type this short code in Downloader:

> ### 🔢 Code: `COMING SOON`
> *(Short code registration in progress — check back soon)*

---

## Step 4 — Find and Open the App

1. Press the **Home** button
2. Scroll to **Your Apps** section
3. Find **Nexus TV** — you may need to scroll right or click "See All"
4. Click it to launch

> **Tip:** Long-press Nexus TV and select **Move to front** to pin it to your home screen

---

## Adding Your Playlist

When you first open the app:

1. Press the **Settings** button in the top right
2. Enter your **playlist name** (anything you like)
3. Enter your **M3U URL** (from your IPTV provider)
4. Optionally enter your **EPG URL** for TV guide data
5. Press **Save**
6. Press **Back** to return to the main screen — your channels will load automatically

---

## Automatic Updates

Nexus TV checks for updates automatically every time you launch it. When a new version is available you'll see a popup asking if you want to update. Just click **Update** and it handles the rest — no need to come back to this page.

---

## Troubleshooting

**App won't install — "Install blocked"**
→ Go back to Step 1 and make sure unknown sources is enabled for the Downloader app specifically

**Channels won't load**
→ Check your M3U URL is correct in Settings. Try opening it in a browser on your phone to verify it works

**No TV guide data**
→ Make sure your EPG URL is entered in Settings. Your IPTV provider should give you this alongside the M3U URL

**Stream keeps buffering**
→ This is usually your internet connection or the IPTV server. Try a wired ethernet connection on your TV if possible

---

## Need Help?

Open an issue at: https://github.com/ctsw05/nexustv-android/issues
