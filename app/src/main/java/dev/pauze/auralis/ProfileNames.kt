package dev.pauze.auralis

internal fun normalizedProfileName(name: String): String =
    name.filterNot { Character.isISOControl(it) }.trim().take(24)

internal fun canAddProfile(name: String, profiles: List<String>): Boolean =
    name.isNotBlank() && profiles.none { it.equals(name, ignoreCase = true) }
