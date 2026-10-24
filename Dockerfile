# Reproducible build environment for Void
# Usage: docker build -t void-build . && docker run --rm -v $(pwd)/output:/output void-build
FROM eclipse-temurin:17-jdk-jammy

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=$ANDROID_SDK_ROOT

# Install Android SDK command-line tools
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    cd /tmp && \
    curl -sL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o tools.zip && \
    unzip -q tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm tools.zip

# Accept licenses and install SDK components
RUN yes | $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 && \
    $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" \
    "build-tools;35.0.0" \
    "platforms;android-35" > /dev/null 2>&1

WORKDIR /build
COPY . .

# Deterministic build: no daemon, no build cache, no parallel
RUN ./gradlew assembleRelease \
    --no-daemon \
    --no-build-cache \
    --no-parallel \
    -Dorg.gradle.jvmargs="-Xmx2048m" && \
    cp app/build/outputs/apk/release/app-release.apk /output/void-release.apk && \
    sha256sum /output/void-release.apk > /output/void-release.apk.sha256
