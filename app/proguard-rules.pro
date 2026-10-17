# Keep serializable API models (kotlinx.serialization uses reflection-free generated
# serializers, but the @Serializable classes' Companion must survive).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class koodipardik.nordprice.** {
    *** Companion;
}
-keepclasseswithmembers class koodipardik.nordprice.** {
    kotlinx.serialization.KSerializer serializer(...);
}
