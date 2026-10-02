# Keep the Quick Settings TileService intact during R8/Proguard minification
-keep class com.dhangofa.networktoggle.NetworkTileService { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Keep the four SIM selectors as real enum objects. custom16's optimized DEX
# replaced the loop selector with null during enum-unboxing/array rewriting.
-keep enum com.dhangofa.networktoggle.model.TargetSim { *; }
# Check this tiny selector method in the final APK, not just unminified unit tests.
-keepclassmembers class com.dhangofa.networktoggle.telephony.NetworkActionExecutor {
    static com.dhangofa.networktoggle.model.TargetSim targetForStep(com.dhangofa.networktoggle.model.TargetSim, int);
}
