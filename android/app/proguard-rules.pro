# Room
-keep class androidx.room.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }

# Google Generative AI
-keep class com.google.ai.client.generativeai.** { *; }

# Firebase
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Model classes
-keep class com.docusnap.ai.data.model.** { *; }
-keepclassmembers class com.docusnap.ai.data.model.** { *; }

