# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# WebView
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-dontnote android.webkit.WebView
-dontnote org.apache.http.**

# Keep data classes
-keepclassmembers class com.technova.browser.data.model.** {
    *;
}

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    *;
}

# Keep Room classes
-keep class * extends androidx.room.RoomDatabase {
    *;
}
-keep @androidx.room.Entity class *
-dontwarn org.jetbrains.kotlinx.**
-dontwarn kotlin.Unit
-dontwarn kotlin.jvm.functions.**

# Keep Compose classes
-keep class androidx.compose.** {
    *;
}

# Keep Koin classes
-keep class org.koin.** {
    *;
}

# General rules
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn kotlin.jvm.functions.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# Gson specific classes are only required for backward compatibility.
-dontwarn com.google.gson.**

# Timber
-dontwarn org.jetbrains.annotations.**

# WebKit
-keep class android.webkit.** {
    *;
}

# Prevent obfuscation of WebView methods
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, int);
    public void *(android.webkit.WebView, java.lang.String);
}
