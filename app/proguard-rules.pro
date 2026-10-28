# kotlinx.serialization: die erzeugten Serializer müssen erhalten bleiben.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class de.kewl.cashdy.**$$serializer { *; }
-keepclassmembers class de.kewl.cashdy.** { *** Companion; }
-keepclasseswithmembers class de.kewl.cashdy.** { kotlinx.serialization.KSerializer serializer(...); }

# Tesseract4Android: der native Teil greift per JNI auf Felder und Konstruktoren dieser
# Klassen zu, die Bibliothek bringt selbst keine Regeln mit.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
