# kotlinx.serialization: keep generated serializers for backup models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.riyadmondol2006.taskfern.** {
    *** Companion;
}
-keepclasseswithmembers class com.riyadmondol2006.taskfern.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.riyadmondol2006.taskfern.**$$serializer { *; }

# Glance action callbacks are instantiated reflectively by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
