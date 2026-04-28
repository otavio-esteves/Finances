# Finances App ProGuard Rules

# Room - Entidades e DAOs precisam ser preservados para o mapeamento do SQLite
-keepclassmembers class br.com.otavioesteves.finances.data.local.entity.** { *; }
-keep class br.com.otavioesteves.finances.data.local.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Compose - Regras gerais do Compose costumam ser inferidas, mas mantemos estabilidade
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Domain Models - Importante para garantir que reflexão (se usada em serialização) não quebre
-keep class br.com.otavioesteves.finances.domain.model.** { *; }

# Kotlin Serialization (se for adicionado futuramente) ou reflexão básica
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses
