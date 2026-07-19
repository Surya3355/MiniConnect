# LiveKit + its bundled WebRTC use JNI heavily — keep so R8 doesn't strip bindings.
-keep class org.webrtc.** { *; }
-keep class livekit.org.webrtc.** { *; }
-keep class io.livekit.** { *; }
-dontwarn org.webrtc.**
-dontwarn io.livekit.**

# OkHttp / Okio ship their own consumer rules, but keep these to be safe.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Keep our domain models (used via reflection-free JSON building only, but safe).
-keep class com.surya.miniconnect.domain.model.** { *; }
