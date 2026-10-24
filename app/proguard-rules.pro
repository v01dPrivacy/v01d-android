# Void - Privacy Toolbox
# Keep bitcoinj classes
-keep class org.bitcoinj.** { *; }
-dontwarn org.bitcoinj.**
-dontwarn com.google.protobuf.**
-dontwarn org.slf4j.**

# Keep ZXing
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# Keep Room entities and DAOs
-keep class com.voidprivacy.app.tools.wallet.btc.TxEntity { *; }
-keep class com.voidprivacy.app.tools.wallet.btc.TxDao { *; }
-keep class com.voidprivacy.app.tools.wallet.btc.TxDatabase { *; }
-keep class com.voidprivacy.app.tools.contacts.ContactEntity { *; }
-keep class com.voidprivacy.app.tools.contacts.ContactDao { *; }
-keep class com.voidprivacy.app.tools.contacts.ContactsDatabase { *; }

# Keep Room-generated implementation classes
-keep class *_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Tor service
-keep class org.torproject.jni.** { *; }
-dontwarn org.torproject.**

# Keep biometric
-keep class androidx.biometric.** { *; }

# Keep EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# Keep Compose
-dontwarn androidx.compose.**

# Keep SQLCipher
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**
