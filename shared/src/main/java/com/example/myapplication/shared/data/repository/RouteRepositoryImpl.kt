package com.example.myapplication.shared.data.repository

import com.example.myapplication.shared.data.remote.ApiClient
import com.example.myapplication.shared.domain.model.Address
import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Stop
import com.example.myapplication.shared.domain.repository.ConfigRepository
import com.example.myapplication.shared.domain.repository.RouteRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Backed by sales-service's delivery-plan endpoints (the same ones apps/web/mfes/sales's
 * RouteReport.tsx and RoutesPlanner.tsx consume) instead of a local routes.json file.
 */
class RouteRepositoryImpl(
    private val api: ApiClient,
    private val configRepository: ConfigRepository,
    // Overridable so tests don't depend on the wall clock. YYYY-MM-DD, same shape sales-service
    // stores delivery_date in.
    private val today: () -> String = { LocalDate.now().toString() },
) : RouteRepository {

    override suspend fun loadTodaysPlan(vehicleId: String): Result<RoutePlan?> = runCatching {
        val todayStr = today()
        val plans = api.getList("/api/sales/delivery-plans")
        var match: JSONObject? = null
        for (i in 0 until plans.length()) {
            val p = plans.getJSONObject(i)
            if (p.optString("vehicle_id") != vehicleId) continue
            if (p.optString("delivery_date") != todayStr) continue
            if (p.optString("status") == "CANCELLED") continue
            match = p
            break
        }
        val plan = match ?: return@runCatching null
        buildPlan(plan)
    }

    override suspend fun refreshPlan(planId: String): Result<RoutePlan?> = runCatching {
        val plan = api.get("/api/sales/delivery-plans/$planId")
        if (plan.optString("id").isBlank()) null else buildPlan(plan)
    }

    override suspend fun selectOption(planId: String, option: RouteOption): Result<RoutePlan> = runCatching {
        val body = if (option.rawJson.isNotBlank()) JSONObject(option.rawJson) else optionToJson(option)
        val updated = api.post("/api/sales/delivery-plans/$planId/confirm", body)
        buildPlan(updated)
    }

    override suspend fun confirmArrival(salesOrderId: String): Result<Unit> = runCatching {
        api.postNoContent("/api/sales/sales-orders/$salesOrderId/deliver")
    }

    override suspend fun failDelivery(salesOrderId: String, note: String): Result<Unit> = runCatching {
        api.postNoContent("/api/sales/sales-orders/$salesOrderId/fail", JSONObject().put("note", note))
    }

    /** [rawPlan] is a full sales-service DeliveryPlan JSON (from either list or get). */
    private suspend fun buildPlan(rawPlan: JSONObject): RoutePlan {
        val customerNames = configRepository.customerNames().getOrElse { emptyMap() }
        val deliveredOrderIds = deliveredOrderIds()
        return planFrom(rawPlan, customerNames, deliveredOrderIds)
    }

    private suspend fun deliveredOrderIds(): Set<String> {
        val orders = api.getList("/api/sales/sales-orders")
        val out = mutableSetOf<String>()
        for (i in 0 until orders.length()) {
            val o = orders.getJSONObject(i)
            if (o.optString("status") == "DELIVERED") out.add(o.optString("id"))
        }
        return out
    }

    private fun planFrom(o: JSONObject, customerNames: Map<String, String>, deliveredOrderIds: Set<String>): RoutePlan {
        val allStops = stopsFrom(o.optJSONArray("stops"), customerNames)
        val pendingStops = allStops.filter { it.salesOrderId !in deliveredOrderIds }
        val options = optionsFrom(o.optJSONArray("options"), customerNames)
        return RoutePlan(
            id = o.optString("id"),
            vehicleId = o.optString("vehicle_id"),
            vehicleName = o.optString("vehicle_name"),
            vehicleCode = o.optString("vehicle_code"),
            centerName = o.optString("center_name"),
            centerLat = o.optDouble("center_lat", 0.0),
            centerLng = o.optDouble("center_lng", 0.0),
            status = o.optString("status"),
            distanceM = o.optDouble("distance_m", 0.0),
            durationS = o.optDouble("duration_s", 0.0),
            geometry = geometryFrom(o.optJSONArray("geometry")),
            stops = pendingStops,
            options = options,
        )
    }

    private fun optionsFrom(arr: JSONArray?, customerNames: Map<String, String>): List<RouteOption> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val opt = arr.getJSONObject(i)
            RouteOption(
                label = opt.optString("label"),
                distanceM = opt.optDouble("distance_m", 0.0),
                durationS = opt.optDouble("duration_s", 0.0),
                geometry = geometryFrom(opt.optJSONArray("geometry")),
                stops = stopsFrom(opt.optJSONArray("stops"), customerNames),
                selected = opt.optBoolean("selected", false),
                rawJson = opt.toString(),
            )
        }
    }

    private fun optionToJson(option: RouteOption): JSONObject {
        val stopsArr = JSONArray()
        option.stops.forEach { s ->
            stopsArr.put(
                JSONObject()
                    .put("seq", s.seq)
                    .put("sales_order_id", s.salesOrderId)
                    .put("distance_m", s.distanceM)
                    .put("duration_s", s.durationS)
                    .put("lat", s.lat)
                    .put("lng", s.lng),
            )
        }
        val geomArr = JSONArray()
        option.geometry.forEach { pt -> geomArr.put(JSONArray(pt)) }
        return JSONObject()
            .put("label", option.label)
            .put("distance_m", option.distanceM)
            .put("duration_s", option.durationS)
            .put("geometry", geomArr)
            .put("stops", stopsArr)
    }

    private fun stopsFrom(arr: JSONArray?, customerNames: Map<String, String>): List<Stop> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            val addressObj = s.optJSONObject("address")
            Stop(
                salesOrderId = s.optString("sales_order_id"),
                seq = s.optInt("seq"),
                customerName = customerNames[s.optString("customer_id")] ?: s.optString("customer_id"),
                address = addressFrom(addressObj),
                lat = s.optDouble("lat", 0.0),
                lng = s.optDouble("lng", 0.0),
                distanceM = s.optDouble("distance_m", 0.0),
                durationS = s.optDouble("duration_s", 0.0),
            )
        }
    }

    private fun addressFrom(o: JSONObject?): Address {
        if (o == null) return Address()
        return Address(
            street = o.optString("street"),
            number = o.optString("number"),
            complement = o.optString("complement"),
            district = o.optString("district"),
            city = o.optString("city"),
            state = o.optString("state"),
            zip = o.optString("zip"),
        )
    }

    private fun geometryFrom(arr: JSONArray?): List<List<Double>> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val pt = arr.getJSONArray(i)
            (0 until pt.length()).map { j -> pt.getDouble(j) }
        }
    }
}
