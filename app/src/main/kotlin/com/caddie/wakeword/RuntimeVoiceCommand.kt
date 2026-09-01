package com.caddie.wakeword

/** Separates global agent controls from ordinary captured task text. */
sealed interface RuntimeVoiceCommand {
    data object Stop : RuntimeVoiceCommand
    data object ForgetContext : RuntimeVoiceCommand
    data class Correction(val text: String) : RuntimeVoiceCommand
    data class Task(val text: String) : RuntimeVoiceCommand
}

/** Recognizes short stop commands and explicit corrections before task routing. */
object RuntimeVoiceCommandParser {
    private val stop = Regex(
        "^(?:(?:bitte|please) )?(?:stopp?|abbrechen|brich ab|hör auf|hoer auf|halt an|stop|cancel|abort)(?: (?:bitte|please|now))?[.!]?$",
        RegexOption.IGNORE_CASE,
    )
    private val correction = Regex(
        "^(?:nein[,:.]?\\s+|no[,:.]?\\s+|nicht das(?:[,:.]?\\s+|$)|not that(?:[,:.]?\\s+|$)|" +
            "mach das so(?:[,:.]?\\s+|$)|do it this way(?:[,:.]?\\s+|$)|" +
            "nicht\\s+.+\\s+sondern\\s+.+|korrigiere(?:[,:.]?\\s+|$)|" +
            "korrektur(?:[,:.]?\\s+|$)|instead\\s+.+|correct(?:ion)?(?:[,:.]?\\s+|$))",
        RegexOption.IGNORE_CASE,
    )
    private val wakePrefix = Regex("^(?:hey\\s+)?jarvis[,.]?\\s*", RegexOption.IGNORE_CASE)
    private val forgetContext = Regex(
        "^(?:(?:vergiss|vergesse|lösche|loesche)\\s+(?:den\\s+)?(?:kontext|verlauf|alles)|" +
            "(?:forget|clear)\\s+(?:the\\s+)?(?:context|history|conversation|everything))[.!]?$",
        RegexOption.IGNORE_CASE,
    )

    fun isExplicitInvocation(transcript: String): Boolean =
        wakePrefix.containsMatchIn(transcript.trim())

    fun parse(transcript: String): RuntimeVoiceCommand {
        val explicitInvocation = isExplicitInvocation(transcript)
        val clean = transcript.trim().replace(wakePrefix, "").trim()
        return when {
            stop.matches(clean) -> RuntimeVoiceCommand.Stop
            explicitInvocation && forgetContext.matches(clean) -> RuntimeVoiceCommand.ForgetContext
            correction.containsMatchIn(clean) -> RuntimeVoiceCommand.Correction(clean)
            else -> RuntimeVoiceCommand.Task(clean)
        }
    }

    /** Treats ordinary speech as a correction when wake-word capture interrupted a run. */
    fun parseForInterruptedRun(transcript: String): RuntimeVoiceCommand =
        when (val command = parse(transcript)) {
            is RuntimeVoiceCommand.Task -> RuntimeVoiceCommand.Correction(command.text)
            else -> command
        }
}
