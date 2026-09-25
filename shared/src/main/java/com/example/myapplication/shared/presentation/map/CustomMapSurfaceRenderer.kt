package com.example.myapplication.shared.presentation.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Surface
import com.example.myapplication.shared.domain.model.RoutePlan
import kotlin.math.cos
import kotlin.math.max

/**
 * Draws the real route (the plan's OSRM geometry + stop coordinates) onto the car's surface —
 * projected from lat/lng into screen space, not fake zig-zag positions. No map-tile/basemap
 * rendering (no Maps SDK dependency); just the road-following polyline and numbered stop pins,
 * matching the README's "custom vector map" design.
 */
class CustomMapSurfaceRenderer {

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#F1F8E9")
        style = Paint.Style.FILL
    }

    private val routePolylinePaint = Paint().apply {
        color = Color.parseColor("#FF9800") // Vivid Orange
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val stopMarkerCurrentPaint = Paint().apply {
        color = Color.parseColor("#81C784") // Light Green — current/next destination
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val stopMarkerPendingPaint = Paint().apply {
        color = Color.parseColor("#607D8B") // Slate — remaining stops
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val depotMarkerPaint = Paint().apply {
        color = Color.parseColor("#3F51B5") // Indigo — distribution center
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pinBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun renderRouteOnSurface(surface: Surface, routePlan: RoutePlan?) {
        if (!surface.isValid) return
        val canvas: Canvas = try {
            surface.lockCanvas(null) ?: return
        } catch (e: Exception) {
            return
        }

        try {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            canvas.drawRect(0f, 0f, width, height, backgroundPaint)

            if (routePlan == null) return
            val projection = Projection.build(routePlan, width, height) ?: return

            if (routePlan.geometry.isNotEmpty()) {
                val path = Path()
                routePlan.geometry.forEachIndexed { i, pt ->
                    if (pt.size < 2) return@forEachIndexed
                    val (x, y) = projection.project(pt[0], pt[1])
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, routePolylinePaint)
            }

            if (routePlan.centerLat != 0.0 || routePlan.centerLng != 0.0) {
                val (x, y) = projection.project(routePlan.centerLat, routePlan.centerLng)
                canvas.drawCircle(x, y, 26f, depotMarkerPaint)
                canvas.drawCircle(x, y, 26f, pinBorderPaint)
            }

            routePlan.stops.forEachIndexed { idx, stop ->
                val (x, y) = projection.project(stop.lat, stop.lng)
                val markerPaint = if (idx == 0) stopMarkerCurrentPaint else stopMarkerPendingPaint
                canvas.drawCircle(x, y, 34f, markerPaint)
                canvas.drawCircle(x, y, 34f, pinBorderPaint)
                canvas.drawText("${idx + 1}", x, y + 11f, textPaint)
            }
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    /** Equirectangular projection with a per-latitude longitude correction — plenty accurate at city scale. */
    private class Projection(
        private val minLat: Double,
        private val maxLat: Double,
        private val minLng: Double,
        private val maxLng: Double,
        private val width: Float,
        private val height: Float,
    ) {
        private val padding = 90f
        private val lngScale = cos(Math.toRadians((minLat + maxLat) / 2.0))
        private val spanLat = max(maxLat - minLat, 0.0005)
        private val spanLng = max((maxLng - minLng) * lngScale, 0.0005)

        fun project(lat: Double, lng: Double): Pair<Float, Float> {
            val nx = ((lng - minLng) * lngScale) / spanLng
            val ny = (lat - minLat) / spanLat
            val x = padding + nx.toFloat() * (width - 2 * padding)
            // Screen y grows downward; latitude grows upward (north), so invert.
            val y = height - padding - ny.toFloat() * (height - 2 * padding)
            return x to y
        }

        companion object {
            fun build(plan: RoutePlan, width: Float, height: Float): Projection? {
                val lats = mutableListOf<Double>()
                val lngs = mutableListOf<Double>()
                if (plan.centerLat != 0.0 || plan.centerLng != 0.0) {
                    lats.add(plan.centerLat)
                    lngs.add(plan.centerLng)
                }
                plan.stops.forEach { lats.add(it.lat); lngs.add(it.lng) }
                plan.geometry.forEach { pt -> if (pt.size >= 2) { lats.add(pt[0]); lngs.add(pt[1]) } }
                if (lats.isEmpty() || lngs.isEmpty()) return null
                return Projection(lats.min(), lats.max(), lngs.min(), lngs.max(), width, height)
            }
        }
    }
}
