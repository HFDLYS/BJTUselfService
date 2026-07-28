package team.bjtuss.bjtuselfservice.entity

enum class DualGradeEligibility {
    UNKNOWN,
    NOT_ELIGIBLE,
    ELIGIBLE
}

internal fun resolveDualGradeEligibility(
    current: DualGradeEligibility,
    cached: DualGradeEligibility?,
    queried: DualGradeEligibility,
): DualGradeEligibility {
    if (queried != DualGradeEligibility.UNKNOWN) {
        return queried
    }
    if (current != DualGradeEligibility.UNKNOWN) {
        return current
    }
    return cached?.takeIf { it != DualGradeEligibility.UNKNOWN }
        ?: DualGradeEligibility.UNKNOWN
}
