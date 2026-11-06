# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK proguard/proguard-android.txt

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep data classes used with Firebase
-keep class com.albert.familycontrol.firebase.FirebaseManager$ChildConfig { *; }
