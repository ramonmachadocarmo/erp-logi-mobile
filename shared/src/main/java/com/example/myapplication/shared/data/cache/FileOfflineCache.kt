package com.example.myapplication.shared.data.cache

import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Vehicle
import com.example.myapplication.shared.domain.repository.RoutePlanCache
import com.example.myapplication.shared.domain.repository.VehicleCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JSON files under [dir] (app-private storage): one `plan_<vehicleId>.json` per vehicle plus
 * `vehicles.json`. Files are written to a temp name and renamed so a crash mid-write never leaves
 * a truncated cache; an unreadable/corrupt file is treated as a miss and deleted. No Room/SQL —
 * the cache holds at most one small plan per vehicle, so a key-value file is enough and adds no
 * dependency.
 */
class FileOfflineCache(private val dir: File) : RoutePlanCache, VehicleCache {

    init {
        dir.mkdirs()
    }

    @Synchronized
    override fun save(date: String, plan: RoutePlan, savedAtMillis: Long) {
        val entry = JSONObject()
            .put("date", date)
            .put("savedAt", savedAtMillis)
            .put("plan", RoutePlanJson.plan(plan))
        write(planFile(plan.vehicleId), entry)
    }

    @Synchronized
    override fun findByVehicle(vehicleId: String, date: String): RoutePlan? =
        readPlan(planFile(vehicleId), date)

    @Synchronized
    override fun findByPlanId(planId: String, date: String): RoutePlan? =
        planFiles().firstNotNullOfOrNull { f -> readPlan(f, date)?.takeIf { it.id == planId } }

    @Synchronized
    override fun removeVehicle(vehicleId: String) {
        planFile(vehicleId).delete()
    }

    @Synchronized
    override fun removeStop(salesOrderId: String) {
        planFiles().forEach { f ->
            val entry = readEntry(f) ?: return@forEach
            val plan = entry.getJSONObject("plan")
            val stops = plan.getJSONArray("stops")
            val kept = JSONArray()
            for (i in 0 until stops.length()) {
                val s = stops.getJSONObject(i)
                if (s.getString("salesOrderId") != salesOrderId) kept.put(s)
            }
            if (kept.length() != stops.length()) {
                plan.put("stops", kept)
                write(f, entry)
            }
        }
    }

    @Synchronized
    override fun save(vehicles: List<Vehicle>) {
        val arr = JSONArray().also { a -> vehicles.forEach { a.put(RoutePlanJson.vehicle(it)) } }
        write(vehiclesFile(), JSONObject().put("vehicles", arr))
    }

    @Synchronized
    override fun read(): List<Vehicle>? {
        val obj = readEntry(vehiclesFile()) ?: return null
        return runCatching {
            val arr = obj.getJSONArray("vehicles")
            (0 until arr.length()).map { RoutePlanJson.vehicle(arr.getJSONObject(it)) }
        }.getOrElse {
            vehiclesFile().delete()
            null
        }
    }

    @Synchronized
    override fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun readPlan(file: File, date: String): RoutePlan? {
        val entry = readEntry(file) ?: return null
        return runCatching {
            if (entry.getString("date") != date) {
                null
            } else {
                RoutePlanJson.plan(entry.getJSONObject("plan"), cachedAtMillis = entry.getLong("savedAt"))
            }
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun readEntry(file: File): JSONObject? {
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrElse {
            file.delete()
            null
        }
    }

    private fun write(file: File, json: JSONObject) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(json.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun planFiles(): List<File> =
        dir.listFiles { f -> f.name.startsWith("plan_") && f.name.endsWith(".json") }?.toList() ?: emptyList()

    private fun planFile(vehicleId: String) =
        File(dir, "plan_${vehicleId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    private fun vehiclesFile() = File(dir, "vehicles.json")
}
