# SQLCipher uses JNI; keep its classes.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
# JavaScript bridge used by the study pages.
-keepclassmembers class com.anoop.financetracker.study.StudyBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
