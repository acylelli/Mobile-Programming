# kotlinx.serialization: @Serializable 클래스(네트워크 DTO, Navigation Route) 유지
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.routealarm.**$$serializer { *; }
