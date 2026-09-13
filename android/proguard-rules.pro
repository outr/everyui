# R8 rules for everyui's Android build (build.sbt: android/packageApk).
#
# The program is instantiated by name only at one point — the manifest's activity; everything else is
# reached from there, so R8 can remove the rest of the Scala library and reactify's unused surface.
-keep class everyui.activity.MainActivity { *; }

# Scala's runtime reaches for JDK internals the Android platform does not declare; the references are
# guarded at runtime.
-dontwarn scala.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**
-dontwarn org.jetbrains.annotations.**

# Scala 3 reads and writes a `lazy val`'s cell (`<name>$lzy1`) through a VarHandle its class initialiser
# finds BY NAME and stores in `<name>$lzy1$lzyHandle`. When no instance of the class is live — a stdlib
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
# java.util.EnumSet reaches a Java enum's `values()` by reflection (Enum.enumValues); R8 must keep it.
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
