# SnapCrop ProGuard rules
-keepclassmembers class * extends android.app.Service { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { *; }

# ML Kit core
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ML Kit Play Services internals (vision, subject segmentation, NL, CJK/Devanagari text recognition)
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.gms.internal.mlkit_subject_segmentation_** { *; }
-keep class com.google.android.gms.internal.mlkit_translate_** { *; }
-keep class com.google.android.gms.internal.mlkit_language_id_** { *; }
-keep class com.google.android.gms.internal.mlkit_entity_extraction_** { *; }
-dontwarn com.google.android.gms.internal.mlkit_**

# Play Services Task API (ML Kit callbacks)
-keep class com.google.android.gms.tasks.** { *; }

# Play Services ML Kit dynamic modules
-keep class com.google.android.gms.dynamite.** { *; }
-dontwarn com.google.android.gms.dynamite.**
