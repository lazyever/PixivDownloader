# Pixiv Downloader Proguard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.pixiv.downloader.data.api.models.** { *; }
-keep class com.pixiv.downloader.data.db.** { *; }
