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
