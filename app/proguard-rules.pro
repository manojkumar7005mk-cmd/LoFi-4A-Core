# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.manoj.lofi4a.** {
    *** Companion;
}
-keepclasseswithmembers class com.manoj.lofi4a.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Native (JNI) code calls these by name, so R8 must keep them
-keep interface com.manoj.lofi4a.core.TokenCallback { *; }
-keep class com.manoj.lofi4a.core.NativeBridge { *; }
