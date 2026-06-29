# AppRemote — R8 obfuscation rules for release build

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Android components declared in manifest
-keep class com.appremote.remotecontrol.MainActivity { *; }
-keep class com.appremote.remotecontrol.ServerActivity { *; }
-keep class com.appremote.remotecontrol.ClientActivity { *; }
-keep class com.appremote.remotecontrol.service.RemoteServerService { *; }
-keep class com.appremote.remotecontrol.service.RemoteAccessibilityService { *; }

# ViewBinding
-keep class com.appremote.remotecontrol.databinding.** { *; }

# Network / relay (keep entry points, obfuscate internals where safe)
-keep class com.appremote.remotecontrol.network.RelayConnection { *; }
-keep class com.appremote.remotecontrol.network.RelayConnection$Listener { *; }
-keep class com.appremote.remotecontrol.network.MultiDeviceManager { *; }
-keep class com.appremote.remotecontrol.network.MultiDeviceManager$Listener { *; }

# Java-WebSocket server (LAN mode)
-keep class org.java_websocket.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

# org.json (Android SDK)
-keepclassmembers class org.json.** { *; }
