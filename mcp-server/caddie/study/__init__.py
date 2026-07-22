# caddie.study — deterministic study runtime for the Shared Autonomy experiment.
# All public types live in caddie.study.model; other modules import from there.

from caddie.study.executor import (
    TrialExecutor,
    ExecutionResult,
    TrialResult,
    ResolvedStep,
)
from caddie.study.logger import StudyLogger, EventType, StudyEvent, MonotonicClock
from caddie.study.matrix import (
    generate_matrix,
    generate_from_specs_dir,
)
from caddie.study.model import (
    # Enums
    StudyCondition,
    StepType,
    CriticalityClass,
    ScreenOffMode,
    InitiationResult,
    TrialOutcome,
    # Data classes
    StudyStep,
    TriggerContract,
    TrialSpec,
    TaskPair,
    ErrorVariant,
    VerificationRule,
    ParticipantConfig,
)

from caddie.study.verification import (
    VerificationOutcome,
    VerificationResult,
    VerificationSummary,
    VerificationManager,
    FakeVerificationBackend,
)

from caddie.study.oversight import (
    OversightDecision,
    OversightProtocol,
    OversightManager,
)
from caddie.study.preflight import (
    default_suite,
    CheckFn,
    CheckResult,
    CheckStatus,
    CheckCategory,
    PreflightCheck,
)
from caddie.study.screen_off import (
    ScreenOffManager,
    ScreenOffConfig,
    ScreenOffResult,
    run_screen_off_block,
)
from caddie.study.session import StudySession, SessionManager, SessionMetrics
from caddie.study.spec_loader import load_trial_spec, list_available_specs, SpecError

__all__ = [
    # Core types
    "StudyCondition",
    "StepType",
    "CriticalityClass",
    "ScreenOffMode",
    "InitiationResult",
    "TrialOutcome",
    "StudyStep",
    "TriggerContract",
    "TrialSpec",
    "TaskPair",
    "ErrorVariant",
    "VerificationRule",
    "ParticipantConfig",
    # Logger
    "StudyLogger",
    "EventType",
    "StudyEvent",
    "MonotonicClock",
    # Executor
    "TrialExecutor",
    "ExecutionResult",
    "TrialResult",
    "ResolvedStep",
    # Verification
    "VerificationOutcome",
    "VerificationResult",
    "VerificationSummary",
    "VerificationManager",
    "FakeVerificationBackend",
    # Oversight
    "OversightDecision",
    "OversightProtocol",
    "OversightManager",
    # Preflight
    "default_suite",
    "CheckFn",
    "CheckResult",
    "CheckStatus",
    "CheckCategory",
    "PreflightCheck",
    # Screen-off
    "ScreenOffManager",
    "ScreenOffConfig",
    "ScreenOffResult",
    "run_screen_off_block",
    # Session
    "StudySession",
    "SessionManager",
    "SessionMetrics",
    # Spec loading
    "load_trial_spec",
    "list_available_specs",
    "SpecError",
    # Matrix
    "generate_matrix",
    "generate_from_specs_dir",
]
