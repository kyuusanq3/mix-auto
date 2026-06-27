package com.kyuusanq3.mixauto.data.navigation

object NavTtsPhrases {

    const val MIN_CONTINUE_SEGMENT_M = 150.0

    fun buildFullInstruction(type: String, modifier: String, name: String): String {
        val action = maneuverAction(type, modifier, capitalize = true)
        return if (name.isNotBlank() && type != "arrive") "$action onto $name" else action
    }

    fun shortManeuver(type: String, modifier: String): String {
        return maneuverAction(type, modifier, capitalize = false)
    }

    fun shortManeuverFromInstruction(instruction: String): String {
        val lower = instruction.trim().lowercase()
        return when {
            lower.startsWith("depart") -> "depart"
            lower.contains("arrive") -> "arrive at destination"
            lower.contains("turn left") -> "turn left"
            lower.contains("turn right") -> "turn right"
            lower.contains("turn sharp left") -> "turn sharp left"
            lower.contains("turn sharp right") -> "turn sharp right"
            lower.contains("turn slight left") -> "turn slight left"
            lower.contains("turn slight right") -> "turn slight right"
            lower.contains("keep left") -> "keep left"
            lower.contains("keep right") -> "keep right"
            lower.contains("take the exit") -> "take the exit"
            lower.contains("take the ramp") -> "take the ramp"
            lower.contains("merge") -> "merge"
            lower.contains("roundabout") -> "enter the roundabout"
            lower.contains("continue") -> "continue"
            else -> lower.trimEnd('.').ifBlank { "continue" }
        }
    }

    fun inferManeuverType(instruction: String): String {
        val lower = instruction.trim().lowercase()
        return when {
            lower.startsWith("depart") -> "depart"
            lower.contains("arrive") -> "arrive"
            lower.contains("roundabout") -> "roundabout"
            lower.contains("ramp") -> "on ramp"
            lower.contains("exit") -> "off ramp"
            lower.contains("merge") -> "merge"
            lower.contains("keep") -> "fork"
            lower.contains("turn") -> "turn"
            lower.contains("continue") -> "new name"
            else -> "turn"
        }
    }

    fun inferManeuverModifier(instruction: String): String {
        val lower = instruction.trim().lowercase()
        return when {
            lower.contains("sharp left") -> "sharp left"
            lower.contains("sharp right") -> "sharp right"
            lower.contains("slight left") -> "slight left"
            lower.contains("slight right") -> "slight right"
            lower.contains("left") -> "left"
            lower.contains("right") -> "right"
            else -> ""
        }
    }

    fun buildContinuePhrase(step: NavStepPhrase): String? {
        if (step.maneuverType == "depart" || step.maneuverType == "arrive") return null
        if (step.distanceMeters < MIN_CONTINUE_SEGMENT_M) return null
        val distance = formatDistance(step.distanceMeters)
        val street = step.streetName.trim()
        return if (street.isNotBlank()) {
            "Continue on $street for $distance."
        } else {
            "Continue for $distance."
        }
    }

    fun buildDistanceWarning(distanceMeters: Int, shortInstruction: String): String {
        return "In ${formatDistance(distanceMeters.toDouble())}, $shortInstruction."
    }

    fun buildCountdownIntro(shortInstruction: String): String {
        val phrase = shortInstruction.trim()
        return if (phrase.endsWith(".")) {
            "${phrase.dropLast(1)} in"
        } else {
            "$phrase in"
        }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun buildImmediate(shortInstruction: String): String {
        val phrase = shortInstruction.trim().trimEnd('.')
        return "${phrase.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} now."
    }

    fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> "%.1f kilometers".format(meters / 1000.0)
        else -> "${meters.toInt()} meters"
    }

    private fun maneuverAction(type: String, modifier: String, capitalize: Boolean): String {
        val action = when (type) {
            "depart" -> "depart"
            "arrive" -> "arrive at destination"
            "turn" -> "turn ${modifier.replace('-', ' ')}".trim()
            "new name" -> "continue"
            "merge" -> "merge ${modifier.replace('-', ' ')}".trim()
            "on ramp" -> "take the ramp"
            "off ramp" -> "take the exit"
            "fork" -> "keep ${modifier.replace('-', ' ')}".trim()
            "end of road" -> "turn ${modifier.replace('-', ' ')}".trim()
            "roundabout" -> "enter the roundabout"
            else -> type.replace('-', ' ').replaceFirstChar { it.lowercase() }
        }
        return if (capitalize) {
            action.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            action
        }
    }
}
