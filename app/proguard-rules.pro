# Preserva todas las clases de galaxy
-keep class galaxy.** { *; }

# Preserva todas las clases de protobuf
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Preserva miembros estáticos y descriptores en clases generadas
-keepclassmembers class * {
    private static final java.lang.String[] descriptorData;
    private static final com.google.protobuf.Descriptors$FileDescriptor descriptor;
}

# Preserva clases específicas de galaxy
-keep class galaxy.Galaxy$Event { *; }
-keep class galaxy.Galaxy$Event$EventDataCase { *; }
-keep class galaxy.Galaxy$Event$EventDataCase$* { *; }

# Ignora advertencias de gRPC
-dontwarn io.grpc.**

# Preserva clases que extienden GeneratedMessageLite
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}