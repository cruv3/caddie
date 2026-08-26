package com.caddie.context.skill

/** Provides validated, deterministic lookup over the installed skill corpus. */
class SkillCatalog(skills: List<Skill>) {
    val all: List<Skill> = skills
        .onEach(::validate)
        .sortedBy(Skill::id)
        .also { ordered ->
            require(ordered.map(Skill::id).distinct().size == ordered.size) {
                "Skill IDs must be unique"
            }
        }
        .toList()

    private val byId = all.associateBy(Skill::id)

    fun get(id: String): Skill? = byId[id]

    private fun validate(skill: Skill) {
        require(skill.id.isNotBlank()) { "Skill ID must not be blank" }
        require(skill.title.isNotBlank()) { "Skill title must not be blank" }
        require(skill.description.isNotBlank()) { "Skill description must not be blank" }
        require(skill.body.isNotBlank()) { "Skill body must not be blank" }
        require(skill.sourcePath.isNotBlank()) { "Skill source path must not be blank" }
        require(skill.sourceSha256.matches(SHA256)) { "Skill source hash must be SHA-256" }
    }

    private companion object {
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}
