# ShellBox ProGuard Rules

# Keep data models
-keep class com.shellbox.data.model.** { *; }

# Keep sshj classes
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }

# sshj uses GSSAPI/JGSS which is not available on Android - suppress missing class errors
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.**
-dontwarn sun.security.**
-dontwarn java.security.acl.**

# EdDSA (used by sshj for ed25519 keys)
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

# BouncyCastle (used by sshj for crypto)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SLF4J logging facade (sshj depends on it)
-dontwarn org.slf4j.**

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
