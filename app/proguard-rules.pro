# Keep the zenz JNI bridge and its externally declared native methods.
-keep class com.kazumaproject.zenz.** { *; }

# Keep the native zenz bridge package if present in the application.
-keep class com.aaaaaisss.necokey.zenz.** { *; }

# Keep Android components referenced by the manifest/reflection.
-keep class com.aaaaaisss.necokey.** extends android.inputmethodservice.InputMethodService { *; }
