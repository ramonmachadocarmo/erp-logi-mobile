package com.example.myapplication.shared.data.cache

import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.Vehicle
import com.example.myapplication.shared.plan
import com.example.myapplication.shared.stop
import com.example.myapplication.shared.vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOfflineCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var dir: File
    private lateinit var cache: FileOfflineCache

    @Before
    fun setUp() {
        dir = File(tmp.root, "offline")
        cache = FileOfflineCache(dir)
    }

    @Test
    fun `plan round-trips with every field and is flagged as cached`() {
        val option = RouteOption(
            label = "Melhor tempo", distanceM = 10.0, durationS = 20.0,
            geometry = listOf(listOf(-3.1, -60.0)), stops = listOf(stop("o1")), selected = true,
            rawJson = """{"label":"Melhor tempo","weight_kg":5}""",
        )
        val original = plan(options = listOf(option))
        cache.save("2026-09-24", original, savedAtMillis = 1234L)

        val loaded = cache.findByVehicle("veh1", "2026-09-24")!!
        assertEquals(original.copy(cachedAtMillis = 1234L), loaded)
        assertTrue(loaded.isFromCache)
    }

    @Test
    fun `survives a new instance over the same directory`() {
        cache.save("2026-09-24", plan(), 1L)
        assertNotNull(FileOfflineCache(dir).findByVehicle("veh1", "2026-09-24"))
    }

    @Test
    fun `a plan saved for another day is a miss`() {
        cache.save("2026-09-23", plan(), 1L)
        assertNull(cache.findByVehicle("veh1", "2026-09-24"))
        assertNull(cache.findByPlanId("plan1", "2026-09-24"))
    }

    @Test
    fun `plans are kept per vehicle and saving again replaces the previous one`() {
        cache.save("2026-09-24", plan(id = "a", vehicleId = "veh1"), 1L)
        cache.save("2026-09-24", plan(id = "b", vehicleId = "veh2"), 1L)
        cache.save("2026-09-24", plan(id = "c", vehicleId = "veh1"), 2L)

        assertEquals("c", cache.findByVehicle("veh1", "2026-09-24")!!.id)
        assertEquals("b", cache.findByVehicle("veh2", "2026-09-24")!!.id)
        assertEquals("b", cache.findByPlanId("b", "2026-09-24")!!.id)
        assertNull(cache.findByPlanId("a", "2026-09-24"))
    }

    @Test
    fun `vehicle ids with path characters cannot escape the cache directory`() {
        cache.save("2026-09-24", plan(vehicleId = "../../evil"), 1L)
        assertNotNull(cache.findByVehicle("../../evil", "2026-09-24"))
        assertTrue(dir.listFiles()!!.all { it.parentFile == dir })
        assertFalse(File(tmp.root.parentFile, "evil.json").exists())
    }

    @Test
    fun `removeStop drops only that order from pending stops`() {
        cache.save("2026-09-24", plan(), 1L)
        cache.removeStop("o1")
        assertEquals(listOf("o2"), cache.findByVehicle("veh1", "2026-09-24")!!.stops.map { it.salesOrderId })
    }

    @Test
    fun `removeStop for an unknown order leaves the plan untouched`() {
        cache.save("2026-09-24", plan(), 1L)
        cache.removeStop("nope")
        assertEquals(2, cache.findByVehicle("veh1", "2026-09-24")!!.stops.size)
    }

    @Test
    fun `removeVehicle deletes just that vehicle`() {
        cache.save("2026-09-24", plan(vehicleId = "veh1"), 1L)
        cache.save("2026-09-24", plan(id = "p2", vehicleId = "veh2"), 1L)
        cache.removeVehicle("veh1")
        assertNull(cache.findByVehicle("veh1", "2026-09-24"))
        assertNotNull(cache.findByVehicle("veh2", "2026-09-24"))
    }

    @Test
    fun `a corrupt file is a miss and gets deleted`() {
        cache.save("2026-09-24", plan(), 1L)
        val file = dir.listFiles()!!.single()
        file.writeText("{not json")

        assertNull(cache.findByVehicle("veh1", "2026-09-24"))
        assertFalse(file.exists())
    }

    @Test
    fun `a file with missing fields is a miss and gets deleted`() {
        cache.save("2026-09-24", plan(), 1L)
        val file = dir.listFiles()!!.single()
        file.writeText("""{"date":"2026-09-24","savedAt":1,"plan":{"id":"plan1"}}""")

        assertNull(cache.findByVehicle("veh1", "2026-09-24"))
        assertFalse(file.exists())
    }

    @Test
    fun `vehicles round-trip and empty cache reads null`() {
        assertNull(cache.read())
        cache.save(listOf(vehicle("v1"), vehicle("v2")))
        assertEquals(listOf(vehicle("v1"), vehicle("v2")), cache.read())
    }

    @Test
    fun `an empty vehicle list is cached as empty, not as a miss`() {
        cache.save(emptyList<Vehicle>())
        assertEquals(emptyList<Vehicle>(), cache.read())
    }

    @Test
    fun `clear wipes plans and vehicles`() {
        cache.save("2026-09-24", plan(), 1L)
        cache.save(listOf(vehicle()))
        cache.clear()
        assertNull(cache.findByVehicle("veh1", "2026-09-24"))
        assertNull(cache.read())
    }

    @Test
    fun `saving leaves no temp files behind`() {
        cache.save("2026-09-24", plan(), 1L)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
}
