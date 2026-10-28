# Keep serializable API models (kotlinx.serialization uses reflection-free generated
# serializers, but the @Serializable classes' Companion must survive).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.koodipardik.nordprice.** {
    *** Companion;
}
-keepclasseswithmembers class com.koodipardik.nordprice.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Chatty logging is compiled out of the release build. `proguard-android-optimize.txt` does not
# strip android.util.Log on its own, so every v/d/i line in the app would otherwise be readable by
# anyone who connects the phone to adb — including the region after a device registration.
# w and e are deliberately left alone: they are the only diagnostics a field failure leaves behind
# (no Crashlytics in this app), and what they say is either a Firebase exception message or a
# missing-configuration warning, never user data.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
