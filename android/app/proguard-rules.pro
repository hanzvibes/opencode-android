# kotlinx.serialization, OkHttp and Compose ship their own consumer rules, so nothing
# is kept for them here. Add a rule only for a failure seen on a minified build.

# Readable stack traces in bug reports; the mapping file still reverses the names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
