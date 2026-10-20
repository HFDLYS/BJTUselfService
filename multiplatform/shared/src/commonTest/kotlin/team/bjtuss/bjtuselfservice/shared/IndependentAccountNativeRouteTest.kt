package team.bjtuss.bjtuselfservice.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.WindowClass
import team.bjtuss.bjtuselfservice.shared.feature.shell.*

class IndependentAccountNativeRouteTest {
    @Test
    fun allFourAccountSettingsCanOpenInAnAndroidActivity() {
        val routes = mapOf(
            PHYVLAB_SETTINGS_ROUTE_ID to PhyVlabSettingsRoute,
            PHYSICS_LAB_SETTINGS_ROUTE_ID to PhysicsLabSettingsRoute,
            PHYSICS_DEMO_SETTINGS_ROUTE_ID to PhysicsDemoSettingsRoute,
            CITEL_SETTINGS_ROUTE_ID to CitelSettingsRoute,
        )
        routes.forEach { (id, route) ->
            assertEquals(route, id.toAppRoute())
            assertTrue(isNativeDetailRoute(id), "Android rejected account settings: $id")
            assertTrue(nativeRouteTitle(id).isNotBlank())
        }
    }

    @Test
    fun physicsDemoQueryCanOpenInAnAndroidActivity() {
        assertEquals(PhysicsDemoRoute, PHYSICS_DEMO_ROUTE_ID.toAppRoute())
        assertTrue(isNativeDetailRoute(PHYSICS_DEMO_ROUTE_ID))
    }

    @Test
    fun smallWindowsUseActivitiesWhileExpandedRootUsesInlineNavigation() {
        assertTrue(usesNativeSecondaryRoutes(true, WindowClass.Compact, false))
        assertFalse(usesNativeSecondaryRoutes(true, WindowClass.Expanded, false))
        assertTrue(usesNativeSecondaryRoutes(true, WindowClass.Expanded, true))
    }
}
