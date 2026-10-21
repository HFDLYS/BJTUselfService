# Moshi's Kotlin adapter needs constructor/property metadata. Gson also uses
# these field names for courseware caches written by previous app versions.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-keep class team.bjtuss.bjtuselfservice.jsonclass.** { *; }

# Update responses are parsed reflectively by Moshi outside the DTO package.
-keep class team.bjtuss.bjtuselfservice.repository.GitHubRelease { *; }
-keep class team.bjtuss.bjtuselfservice.repository.Asset { *; }

# Preserve the existing Gson format for saved grade selections across upgrades.
-keep class team.bjtuss.bjtuselfservice.entity.GradeSelectionRecord { *; }
