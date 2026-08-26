package com.caddie.study.runtime.model

/** Parses and formats stable participant IDs without a study-specific upper bound. */
object ParticipantId {
    private val pattern = Regex("^(?:P)?0*([1-9][0-9]*)$", RegexOption.IGNORE_CASE)

    fun number(value: String): Int? = pattern.matchEntire(value)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

    fun requireNumber(value: String): Int = requireNotNull(number(value)) {
        "participant id must be a positive number, optionally prefixed by P"
    }

    fun canonical(value: String): String = format(requireNumber(value))

    fun format(number: Int): String {
        require(number > 0) { "participant number must be positive" }
        return "P${number.toString().padStart(2, '0')}"
    }
}
