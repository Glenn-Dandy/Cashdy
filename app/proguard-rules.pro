# kotlinx.serialization: die erzeugten Serializer müssen erhalten bleiben.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class de.kewl.cashdy.**$$serializer { *; }
-keepclassmembers class de.kewl.cashdy.** { *** Companion; }
-keepclasseswithmembers class de.kewl.cashdy.** { kotlinx.serialization.KSerializer serializer(...); }
