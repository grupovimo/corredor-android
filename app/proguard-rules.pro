# kotlinx.serialization: conservar los serializadores generados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ar.com.corredor.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ar.com.corredor.app.**$$serializer { *; }
