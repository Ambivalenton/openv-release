# Kotlin Serialization generates serializers; the plugin supplies their keep rules.
# Retain file/line information for meaningful release stack traces.
-keepattributes SourceFile,LineNumberTable

# Glance persists widget-class identities between processes/releases. Do not let R8 merge
# the two renderers (both classes previously became one runtime class) or rename them.
-keep class dev.ambivalenton.openv.widget.AppQuickWidget { *; }
-keep class dev.ambivalenton.openv.widget.DepartureBoardWidget { *; }
-keep class dev.ambivalenton.openv.widget.AppQuickWidgetReceiver { *; }
-keep class dev.ambivalenton.openv.widget.DepartureBoardWidgetReceiver { *; }

# Persisted widget-side design action must also survive application updates.
-keep class dev.ambivalenton.openv.widget.CycleBoardDesignAction { *; }
