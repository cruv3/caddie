"""Package identifiers reserved for deterministic study trials."""

STUDY_PACKAGES = frozenset(
    {
        "com.caddie.studytelegram",
        "com.caddie.studymail",
        "com.caddie.studygallery",
        "com.caddie.studynotes",
        "com.caddie.studycalendar",
        "com.caddie.studybank",
    }
)


def is_study_package(package_name: str) -> bool:
    """Return whether a package or explicit component is study-only."""
    package, _, _ = package_name.partition("/")
    return package in STUDY_PACKAGES
