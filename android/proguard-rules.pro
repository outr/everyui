# R8 rules for everyui's Android build (build.sbt: android/packageApk), shipped in everyui-android for an app's build
# too (sbt-everyui's everyuiAndroidRules).
#
# The program is instantiated by name only at one point — the manifest's activity; everything else is
# reached from there, so R8 can remove the rest of the Scala library and reactify's unused surface.
-keep class everyui.activity.MainActivity { *; }

# Home-screen widgets: the manifest names their receivers, their configure screen and their lists' tap
# activity, and a receiver loads the app's WidgetApp object by the name the manifest gives it
# (everyui.render.WidgetAccess).
-keep class everyui.render.WidgetReceiver { *; }
-keep class * extends everyui.render.WidgetReceiver { *; }
-keep class everyui.activity.WidgetConfigureActivity { *; }
-keep class everyui.activity.WidgetTapActivity { *; }
-keep class * implements everyui.widget.WidgetApp { *; }

# Scala's runtime reaches for JDK internals the Android platform does not declare; the references are
# guarded at runtime.
-dontwarn scala.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**
-dontwarn org.jetbrains.annotations.**

# Scala 3 reads and writes a `lazy val`'s cell (`<name>$lzy1`) through a VarHandle (an AtomicReferenceFieldUpdater
# once sbt-everyui's AndroidRuntime has rewritten it for old Android) its class initialiser finds BY NAME and
# stores in `<name>$lzy1$lzyHandle`. When no instance of the class is live — a stdlib
# companion that `scala.package$`'s initialiser merely touches — R8 removes the instance cell but keeps the
# initialiser's lookup, and the app dies in `<clinit>` with NoSuchFieldException. So wherever a handle
# survives, its cell survives too, under its own name. (`-keepclassmembers` needs a live instance; an
# unconditional `-keep` pinned every class with a lazy val, 233 KB → 1,045 KB of dex.)
-if class ** { *** *$lzy*$lzyHandle; }
-keep class <1> { *** <3>$lzy<4>; }
-keepclassmembernames class ** { *** *$lzy*; }

# R8 merges a trait into its only implementor when it can (vertical class merging). A Scala trait's
# `super.m()` reaches the parent trait through a super accessor that D8 lowers to `invoke-super` on that
# parent; once the parent is merged into the child, the call targets the child itself and recurses until the
# stack overflows (UIExampleScreen.init → UIScreen.init, first). A kept name pins a class against merging, so
# every surviving interface keeps its name: +61 KB of dex, no recursion.
-keepnames interface **

# Readable stack traces from the device.
-keepattributes SourceFile,LineNumberTable

# The network module (Spice over OkHttp): platform-optional pieces OkHttp and Kotlin probe for at runtime.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlin.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn android.net.http.**
-dontwarn javax.annotation.**
# rapid probes for virtual threads (Java 21); Android has none and rapid is told so (NetworkBackend.init)
-dontwarn java.lang.Thread$Builder$OfVirtual
-dontwarn java.lang.Thread$Builder
# profig's YAML/XML readers (jackson-dataformat-xml) reach javax.xml.stream, which Android does not ship; unused here
-dontwarn javax.xml.stream.**
-dontwarn org.codehaus.stax2.**
-dontwarn com.fasterxml.jackson.**
# and its StAX parser's (Woodstox) OSGi service annotations, which R8 asks for when it builds for before Android 8
-dontwarn aQute.bnd.annotation.**
# java.util.EnumSet reaches a Java enum's `values()` by reflection (Enum.enumValues); R8 must keep it.
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Media3 reaches for two things no player here does. Its image handling reads EXIF orientation, which only
# matters for the picture formats it can show as media; nothing in this backend asks it to decode a bitmap.
# Its download service shows notifications, whose strings live in resources that are not merged (sbt-everyui's Aars),
# so it must be removed rather than kept — which it is, since nothing reaches it.
-dontwarn androidx.exifinterface.**
-dontwarn androidx.media3.exoplayer.offline.**
-dontwarn androidx.media3.exoplayer.scheduler.**

# A page of the web calls back into the app by REFLECTION, on a name the page itself knows: the method has to keep
# its own name or the call finds nothing, and the failure is silent -- the video plays and the app never hears that
# it started, ended, or moved.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# and the web view finds those methods by their annotation, at run time: shrunk away with the other annotations, the
# object reaches the page with no methods at all (an embed's guest could not answer, a page's bridge heard nothing)
-keepattributes RuntimeVisibleAnnotations

# Passkeys ask Credential Manager (androidx.credentials, EveryUILibraries.credentials); an app built without it has no
# passkeys (Passkeys.supported is false), and nothing else reaches for it
-dontwarn androidx.credentials.**

# Push notifications ask Firebase Cloud Messaging (EveryUILibraries.firebase), which an app adds only if it has push
# (Push.supported is false without it); what of it the backend compiles against reaches for the rest of Firebase
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Scala libraries compiled with offset-based lazy vals also look up their storage by name (e.g. fabric.rw.package$).
# These have no $lzyHandle for the rule above to match. Keep the storage of reachable classes, not whole libraries.
-keepclassmembers class ** { *** *$lzy*; }
-keepclassmembers class ** { *** *bitmap*; }
