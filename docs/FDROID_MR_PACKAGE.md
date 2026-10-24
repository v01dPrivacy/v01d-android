# F-Droid Submission Package — v01d v1.1.0

Everything needed to open the merge request on https://gitlab.com/fdroid/fdroiddata

## Prerequisites
- A **gitlab.com account** (F-Droid runs on GitLab — the GitHub repo is fine as source).
- `git` configured on the machine you push from.

## One-time setup
```bash
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata
git remote add fork git@gitlab.com:<YOUR_GITLAB_USER>/fdroiddata.git
git checkout -b add-v01d
```

## Metadata file
Create `metadata/com.voidprivacy.app.yml` with EXACTLY the content of
`metadata/com.voidprivacy.app.yml` from this repo (same file, already prepared):

```yaml
Categories:
  - Security
  - Internet
License: GPL-3.0-only
AuthorName: v01d Privacy
SourceCode: https://github.com/v01dPrivacy/v01d-android
IssueTracker: https://github.com/v01dPrivacy/v01d-android/issues

AutoName: v01d

RepoType: git
Repo: https://github.com/v01dPrivacy/v01d-android

Builds:
  - versionName: 1.1.0
    versionCode: 8
    commit: v1.1.0
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.1.0
CurrentVersionCode: 8
```

## Commit + push + MR
```bash
git add metadata/com.voidprivacy.app.yml
git commit -m "Add com.voidprivacy.app (v01d)"
git push fork add-v01d
# then open the MR on gitlab.com/fdroid/fdroiddata (button appears in the push output)
```

MR title: `Add com.voidprivacy.app (v01d — privacy toolbox)`

## What to expect in review (honest notes)
1. **libsignal prebuilt**: `org.signal:libsignal-android:0.99.3` is pulled from
   Signal's Maven (`https://build-artifacts.signal.org/libraries/maven`, declared in
   settings.gradle.kts). It is AGPL-3.0 (FOSS, GPL-compatible) but the native .so is
   a prebuilt binary. F-Droid reviewers may ask to build it from source or accept it
   as an approved prebuilt — several published apps use it the same way. If asked,
   point to https://github.com/signalapp/libsignal (AGPL-3.0).
2. **ndkVersion pinned (r27c)**: fdroidserver accepts it; the NDK is downloaded by
   Gradle from Google's repository (FOSS toolchain).
3. **Reproducible build**: our tag builds with `./gradlew assembleRelease`; F-Droid
   builds debug via the recipe above — if they request a signed-tag verification
   flow, our Dockerfile (Dockerfile + scripts/verify-reproducible.sh) helps.
4. Review usually takes days-to-weeks; respond to `fdroid-bot`/maintainer comments
   on the MR.

## Fastlane
The `fastlane/metadata/android/en-US/` directory in this repo (title, descriptions,
6 screenshots, icon) is what F-Droid will display — it is already complete and
committed. F-Droid reads fastlane metadata straight from the source repo.
