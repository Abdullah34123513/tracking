# Firebase / Google Play Services - keep only what we need
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }

# Keep our FCM service
-keep class com.tracker.app.services.TrackerFirebaseMessagingService { *; }

# Strip verbose logging
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Aggressive optimization
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Remove unused Google Play Services components
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
