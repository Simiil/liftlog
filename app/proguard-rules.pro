# LiftLog R8/ProGuard rules (release builds). See 05-roadmap M5.
#
# Most of our stack ships its own consumer R8 rules inside the published
# artifacts and needs nothing here: Room, Koin, Jetpack Compose,
# DataStore, and Vico. The one thing R8 can silently break is
# kotlinx.serialization, which we depend on in four data-critical places —
# so the bulk of this file keeps those serializers intact.

# ── Crash deobfuscation ──────────────────────────────────────────────────────
# Keep line tables so release stack traces map back to source; rename the source
# file attribute itself so original file names aren't leaked.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx.serialization ────────────────────────────────────────────────────
# Serialization is load-bearing in four spots that MUST survive minification
# (a broken serializer corrupts user data — hard constraint "no data loss"):
#   • ui/navigation  — type-safe Navigation routes (@Serializable objects)
#   • data/backup    — the versioned export/import format (02-data-spec §6)
#   • data/seed      — the bundled exercises.v1.json library
#   • domain/model   — persisted plan drafts
# These are the official app-scoped serialization keep rules, narrowed to our
# package so R8 still strips everything else.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep the generated serializers and the classes they describe.
-keep,includedescriptorclasses class de.simiil.liftlog.**$$serializer { *; }
# Keep the Companion of every @Serializable class (carries serializer()).
-keepclassmembers class de.simiil.liftlog.** {
    *** Companion;
}
# Keep serializer() on companions AND on @Serializable objects (INSTANCE.serializer()).
-keepclasseswithmembers class de.simiil.liftlog.** {
    kotlinx.serialization.KSerializer serializer(...);
}
