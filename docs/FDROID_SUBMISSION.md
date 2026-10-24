# F-Droid Submission Guide

## Prerequisites

- A public Git repository (GitHub/GitLab) with the app source code
- A signed release APK or reproducible build setup
- All dependencies must be F-Droid compliant (no proprietary blobs)

## Step 1: Prepare the repository

1. Ensure the repo has a git tag matching the version: `git tag v0.5.0`
2. Ensure `metadata/com.voidprivacy.app.yml` is in the repo
3. Ensure `fastlane/metadata/android/en-US/` has:
   - `title.txt` (max 50 chars)
   - `short_description.txt` (max 80 chars)
   - `full_description.txt` (max 4000 chars)
   - `images/phoneScreenshots/` (at least 3 PNG screenshots)
   - `images/icon.png` (512x512 app icon)

## Step 2: Fork fdroiddata

```bash
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata
```

## Step 3: Create the metadata file

Copy `metadata/com.voidprivacy.app.yml` to `fdroiddata/metadata/com.voidprivacy.app.yml`.

Verify the build recipe:
```yaml
Builds:
  - versionName: 0.5.0
    versionCode: 5
    commit: v0.5.0
    subdir: void-app
    gradle:
      - yes
```

## Step 4: Test the build locally

```bash
# Install fdroidserver
pip install fdroidserver

# Test the build
fdroid build com.voidprivacy.app:5

# Verify the APK
fdroid verify com.voidprivacy.app:5
```

## Step 5: Submit the merge request

1. Create a new branch: `git checkout -b add-void`
2. Commit the metadata file
3. Push to your fork
4. Create a merge request on GitLab: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new

## Step 6: Review process

- F-Droid maintainers will review the metadata and build recipe
- They will verify the build is reproducible
- They may request changes to the metadata or build configuration
- Once approved, the app will be available in the F-Droid client

## Dependency Compliance

All v01d dependencies are F-Droid compliant:
- ✅ bitcoinj (Apache 2.0)
- ✅ ZXing (Apache 2.0)
- ✅ zxing-android-embedded (Apache 2.0)
- ✅ tor-android (BSD)
- ✅ AndroidX libraries (Apache 2.0)
- ✅ Room (Apache 2.0)
- ✅ No Google Play Services
- ✅ No proprietary blobs

## Reproducible Builds

v01d includes a Dockerfile for reproducible builds:
```bash
docker build -t void-build .
docker run --rm -v $(pwd)/output:/output void-build
```

Verify with:
```bash
./scripts/verify-reproducible.sh
```
