package com.example.myapplication.shared.data.cache

import com.example.myapplication.shared.domain.model.Address
import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Stop
import com.example.myapplication.shared.domain.model.Vehicle
import org.json.JSONArray
import org.json.JSONObject

/** Serialization of the app's own domain models for the offline cache (not the server wire format). */
internal object RoutePlanJson {

    fun plan(p: RoutePlan): JSONObject = JSONObject()
        .put("id", p.id)
        .put("vehicleId", p.vehicleId)
        .put("vehicleName", p.vehicleName)
        .put("vehicleCode", p.vehicleCode)
        .put("centerName", p.centerName)
        .put("centerLat", p.centerLat)
        .put("centerLng", p.centerLng)
        .put("status", p.status)
        .put("distanceM", p.distanceM)
        .put("durationS", p.durationS)
        .put("geometry", geometry(p.geometry))
        .put("stops", JSONArray().also { arr -> p.stops.forEach { arr.put(stop(it)) } })
        .put("options", JSONArray().also { arr -> p.options.forEach { arr.put(option(it)) } })

    fun plan(o: JSONObject, cachedAtMillis: Long?): RoutePlan {
        val options = o.getJSONArray("options")
        return RoutePlan(
            id = o.getString("id"),
            vehicleId = o.getString("vehicleId"),
            vehicleName = o.getString("vehicleName"),
            vehicleCode = o.getString("vehicleCode"),
            centerName = o.getString("centerName"),
            centerLat = o.getDouble("centerLat"),
            centerLng = o.getDouble("centerLng"),
            status = o.getString("status"),
            distanceM = o.getDouble("distanceM"),
            durationS = o.getDouble("durationS"),
            geometry = geometry(o.getJSONArray("geometry")),
            stops = stops(o.getJSONArray("stops")),
            options = (0 until options.length()).map { option(options.getJSONObject(it)) },
            cachedAtMillis = cachedAtMillis,
        )
    }

    fun vehicle(v: Vehicle): JSONObject = JSONObject()
        .put("id", v.id).put("code", v.code).put("name", v.name)
        .put("capacityKg", v.capacityKg).put("capacityM3", v.capacityM3).put("active", v.active)

    fun vehicle(o: JSONObject) = Vehicle(
        id = o.getString("id"),
        code = o.getString("code"),
        name = o.getString("name"),
        capacityKg = o.getDouble("capacityKg"),
        capacityM3 = o.getDouble("capacityM3"),
        active = o.getBoolean("active"),
    )

    private fun option(o: RouteOption): JSONObject = JSONObject()
        .put("label", o.label)
        .put("distanceM", o.distanceM)
        .put("durationS", o.durationS)
        .put("geometry", geometry(o.geometry))
        .put("stops", JSONArray().also { arr -> o.stops.forEach { arr.put(stop(it)) } })
        .put("selected", o.selected)
        .put("rawJson", o.rawJson)

    private fun option(o: JSONObject) = RouteOption(
        label = o.getString("label"),
        distanceM = o.getDouble("distanceM"),
        durationS = o.getDouble("durationS"),
        geometry = geometry(o.getJSONArray("geometry")),
        stops = stops(o.getJSONArray("stops")),
        selected = o.getBoolean("selected"),
        rawJson = o.getString("rawJson"),
    )

    private fun stop(s: Stop): JSONObject = JSONObject()
        .put("salesOrderId", s.salesOrderId)
        .put("seq", s.seq)
        .put("customerName", s.customerName)
        .put(
            "address",
            JSONObject()
                .put("street", s.address.street).put("number", s.address.number)
                .put("complement", s.address.complement).put("district", s.address.district)
                .put("city", s.address.city).put("state", s.address.state).put("zip", s.address.zip),
        )
        .put("lat", s.lat).put("lng", s.lng)
        .put("distanceM", s.distanceM).put("durationS", s.durationS)

    private fun stops(arr: JSONArray): List<Stop> = (0 until arr.length()).map { i ->
        val s = arr.getJSONObject(i)
        val a = s.getJSONObject("address")
        Stop(
            salesOrderId = s.getString("salesOrderId"),
            seq = s.getInt("seq"),
            customerName = s.getString("customerName"),
            address = Address(
                street = a.getString("street"), number = a.getString("number"),
                complement = a.getString("complement"), district = a.getString("district"),
                city = a.getString("city"), state = a.getString("state"), zip = a.getString("zip"),
            ),
            lat = s.getDouble("lat"), lng = s.getDouble("lng"),
            distanceM = s.getDouble("distanceM"), durationS = s.getDouble("durationS"),
        )
    }

    private fun geometry(g: List<List<Double>>): JSONArray =
        JSONArray().also { arr -> g.forEach { pt -> arr.put(JSONArray(pt)) } }

    private fun geometry(arr: JSONArray): List<List<Double>> = (0 until arr.length()).map { i ->
        val pt = arr.getJSONArray(i)
        (0 until pt.length()).map { pt.getDouble(it) }
    }
}
