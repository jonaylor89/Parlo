# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.parlo.app.**$$serializer { *; }
-keepclassmembers class com.parlo.app.** { *** Companion; }
-keepclasseswithmembers class com.parlo.app.** { kotlinx.serialization.KSerializer serializer(...); }
