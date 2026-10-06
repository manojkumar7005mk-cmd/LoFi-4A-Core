# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.manoj.lofi4a.** {
    *** Companion;
}
-keepclasseswithmembers class com.manoj.lofi4a.** {
    kotlinx.serialization.KSerializer serializer(...);
}
