package team.bjtuss.bjtuselfservice.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class DualGradeEligibilityTest {
    @Test
    fun explicitQueryResultOverridesCurrentAndCachedResults() {
        assertEquals(
            DualGradeEligibility.NOT_ELIGIBLE,
            resolveDualGradeEligibility(
                current = DualGradeEligibility.ELIGIBLE,
                cached = DualGradeEligibility.ELIGIBLE,
                queried = DualGradeEligibility.NOT_ELIGIBLE,
            ),
        )
    }

    @Test
    fun failedRefreshKeepsCurrentEligibility() {
        assertEquals(
            DualGradeEligibility.ELIGIBLE,
            resolveDualGradeEligibility(
                current = DualGradeEligibility.ELIGIBLE,
                cached = DualGradeEligibility.NOT_ELIGIBLE,
                queried = DualGradeEligibility.UNKNOWN,
            ),
        )
    }

    @Test
    fun cachedEligibilityIsUsedWhenCurrentResultIsUnknown() {
        assertEquals(
            DualGradeEligibility.ELIGIBLE,
            resolveDualGradeEligibility(
                current = DualGradeEligibility.UNKNOWN,
                cached = DualGradeEligibility.ELIGIBLE,
                queried = DualGradeEligibility.UNKNOWN,
            ),
        )
    }

    @Test
    fun cachedIneligibilityIsUsedWhenCurrentResultIsUnknown() {
        assertEquals(
            DualGradeEligibility.NOT_ELIGIBLE,
            resolveDualGradeEligibility(
                current = DualGradeEligibility.UNKNOWN,
                cached = DualGradeEligibility.NOT_ELIGIBLE,
                queried = DualGradeEligibility.UNKNOWN,
            ),
        )
    }

    @Test
    fun missingCacheAndFailedQueryRemainUnknown() {
        assertEquals(
            DualGradeEligibility.UNKNOWN,
            resolveDualGradeEligibility(
                current = DualGradeEligibility.UNKNOWN,
                cached = null,
                queried = DualGradeEligibility.UNKNOWN,
            ),
        )
    }
}
