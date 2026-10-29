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

# Glance finds the widget ids to update by the GlanceAppWidget's class name, and maps receivers to widgets by name.
# Without this, R8 merges the similar widget classes into one and an update of one widget kind redraws the others.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
