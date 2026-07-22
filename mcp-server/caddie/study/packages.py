"""Package identifiers reserved for deterministic study trials."""

import re

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
    package, _, _ = package_name.strip().partition("/")
    return package in STUDY_PACKAGES


def resolves_to_study_package(
    package_name: str,
    installed_packages: list[str],
) -> bool:
    """Mirror the ADB backend's fail-closed alias resolution for study apps."""
    package = package_name.strip()
    if is_study_package(package):
        return True
    if not package or "/" in package or package in installed_packages:
        return False

    token = re.sub(r"\d+$", "", package.lower().rsplit(".", 1)[-1])
    if not token:
        return False

    def app_token(candidate: str) -> str:
        return re.sub(r"\d+$", "", candidate.lower().rsplit(".", 1)[-1])

    for candidates in (
        [candidate for candidate in installed_packages if app_token(candidate) == token],
        [candidate for candidate in installed_packages if token in candidate.lower()],
    ):
        if len(candidates) == 1:
            return candidates[0] in STUDY_PACKAGES
        if len(candidates) > 1:
            return False
    return False
