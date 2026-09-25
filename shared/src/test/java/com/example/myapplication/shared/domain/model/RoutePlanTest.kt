package com.example.myapplication.shared.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlanTest {

    private fun plan(
        status: String = PlanStatus.PLANNED,
        options: List<RouteOption> = emptyList(),
        distanceM: Double = 15_000.0,
        durationS: Double = 2_700.0,
    ) = RoutePlan(
        id = "p1", vehicleId = "v1", vehicleName = "Van", vehicleCode = "V01",
        centerName = "CD Manaus", centerLat = -3.1, centerLng = -60.0,
        status = status, distanceM = distanceM, durationS = durationS,
        geometry = emptyList(), stops = emptyList(), options = options,
    )

    @Test
    fun `converts distance and duration to km and rounded minutes`() {
        val p = plan(distanceM = 18_500.0, durationS = 2_730.0) // 45.5 min -> rounds to 46 (banker's? no, roundToInt half-up-ish)
        assertEquals(18.5, p.totalDistanceKm, 0.0001)
        assertEquals(46, p.totalTimeMinutes)
    }

    @Test
    fun `hasAlternatives is true only when PLANNED and more than one option`() {
        val opts = listOf(
            RouteOption(label = "Melhor tempo", distanceM = 1.0, durationS = 1.0, geometry = emptyList(), stops = emptyList()),
            RouteOption(label = "Menor distância", distanceM = 1.0, durationS = 1.0, geometry = emptyList(), stops = emptyList()),
        )
        assertTrue(plan(status = PlanStatus.PLANNED, options = opts).hasAlternatives)
    }

    @Test
    fun `a single option is not worth comparing`() {
        val opts = listOf(RouteOption(label = "Melhor tempo", distanceM = 1.0, durationS = 1.0, geometry = emptyList(), stops = emptyList()))
        assertFalse(plan(status = PlanStatus.PLANNED, options = opts).hasAlternatives)
    }

    @Test
    fun `already CONFIRMED plans never show the comparison step again`() {
        val opts = listOf(
            RouteOption(label = "A", distanceM = 1.0, durationS = 1.0, geometry = emptyList(), stops = emptyList()),
            RouteOption(label = "B", distanceM = 1.0, durationS = 1.0, geometry = emptyList(), stops = emptyList()),
        )
        assertFalse(plan(status = PlanStatus.CONFIRMED, options = opts).hasAlternatives)
    }
}
