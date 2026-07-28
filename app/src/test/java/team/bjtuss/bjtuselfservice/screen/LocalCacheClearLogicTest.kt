package team.bjtuss.bjtuselfservice.screen

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCacheClearLogicTest {
    @Test
    fun selectionClearFailureDoesNotSkipEligibilityClearOrRefresh() {
        var eligibilityCleared = false
        var eligibilityRefreshed = false

        val failure = runCatching {
            runBlocking {
                clearGradeCachesIndependently(
                    clearGradeSelections = {
                        throw IllegalStateException("selection clear failed")
                    },
                    clearGradeEligibility = {
                        eligibilityCleared = true
                    },
                    refreshGradeEligibility = {
                        eligibilityRefreshed = true
                    },
                )
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(eligibilityCleared)
        assertTrue(eligibilityRefreshed)
    }

    @Test
    fun eligibilityClearFailureStillTriggersEligibilityRefresh() {
        var eligibilityRefreshed = false

        val failure = runCatching {
            runBlocking {
                clearGradeCachesIndependently(
                    clearGradeSelections = {},
                    clearGradeEligibility = {
                        throw IllegalStateException("eligibility clear failed")
                    },
                    refreshGradeEligibility = {
                        eligibilityRefreshed = true
                    },
                )
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(eligibilityRefreshed)
    }
}
