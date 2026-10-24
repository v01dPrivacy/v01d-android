# Building v01d with Monero Native Library

The XMR wallet requires the `monero-android` native library (C++ bindings).
This document describes how to build it and integrate it into v01d.

## Prerequisites

- Android NDK r25+
- CMake 3.22+
- Boost, OpenSSL, libsodium, ZMQ (built for Android)

## Steps

### 1. Clone monero-android

```bash
git clone https://github.com/m2049r/monero-android.git
cd monero-android
```

### 2. Build the native library

```bash
# Build for arm64-v8a (primary target for GrapheneOS/Pixel)
./build.sh arm64-v8a

# Output: libmonero-wallet.so in build/arm64-v8a/
```

### 3. Integrate into v01d

1. Copy `libmonero-wallet.so` to `void-app/app/src/main/jniLibs/arm64-v8a/`
2. Add the monero Java bindings JAR to `void-app/app/libs/`
3. Uncomment the monero dependency in `app/build.gradle.kts`:

```kotlin
// implementation(files("libs/monero-android.aar"))
```

### 4. Enable XMR features

Once the library is available, update `XmrWalletScreen.kt` to:
- Generate Monero addresses from the seed
- Show balance via a Monero daemon (remote or local)
- Send/receive XMR transactions

## References

- [Monerujo (xmrwallet)](https://github.com/m2049r/xmrwallet) — reference Android Monero wallet
- [monero-android](https://github.com/m2049r/monero-android) — native library build scripts
- [Monero project](https://github.com/monero-project/monero) — core C++ codebase

## Why this is separate

The monero C++ library is ~200MB of compiled native code. Including it in the
main build would:
- Increase APK size by ~50MB
- Require NDK setup for all contributors
- Slow CI builds significantly

By keeping it as an optional integration, the base app stays lightweight and
the XMR wallet can be added by power users who need it.
