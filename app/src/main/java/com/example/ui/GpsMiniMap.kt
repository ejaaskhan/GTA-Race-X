package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TelemetrySnapshot
import com.example.engine.CityWorld
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.StatEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Precomputed deterministic realistic satellite map building footprints matching CityWorld blocks
private data class MapBuildingFootprint(
    val x: Float,
    val z: Float,
    val w: Float,
    val d: Float,
    val tone: Int // 0 = dark tower, 1 = mid slate, 2 = glass/light roof, 3 = industrial roof
)

private data class MapParkPond(
    val x: Float,
    val z: Float,
    val radius: Float
)

private object WorldMapCartography {
    val buildings: List<MapBuildingFootprint>
    val parkPonds: List<MapParkPond>

    init {
        val list = ArrayList<MapBuildingFootprint>(320)
        val ponds = ArrayList<MapParkPond>(16)
        val coords = CityWorld.AVENUE_COORDS

        for (xi in 0 until coords.size - 1) {
            for (zi in 0 until coords.size - 1) {
                val xMin = coords[xi] + 15f
                val xMax = coords[xi + 1] - 15f
                val zMin = coords[zi] + 15f
                val zMax = coords[zi + 1] - 15f
                val cx = (xMin + xMax) * 0.5f
                val cz = (zMin + zMax) * 0.5f

                // Skip water bay under suspension bridge
                if (abs(cx) < 130f && cz < -290f && cz > -450f) continue
                // Skip pure ocean / coastal beach strips
                if (cx < -450f) continue
                // Skip mountain ridge zones (drawn with topographic contours instead)
                if (cx > 410f || (cz < -440f && cx > -350f)) continue

                // City Park blocks (south z > 370) get ponds & walkways instead of skyscrapers
                if (cz > 380f && abs(cx) < 420f) {
                    ponds.add(MapParkPond(cx, cz, 18f))
                    continue
                }

                val spanX = xMax - xMin
                val spanZ = zMax - zMin
                if (spanX < 28f || spanZ < 28f) continue

                val isDowntown = abs(cx) < 185f && abs(cz) < 185f
                val isIndustrial = cx >= 120f && cz >= 120f
                val isRacing = cx < -120f && cz < -120f

                // Deterministic hash per city block for realistic varied architectural footprints
                val seed = ((xi * 73856093) xor (zi * 19349663)) and 0x7FFFFFFF

                when {
                    isIndustrial -> {
                        // Wide warehouse & factory footprints
                        list.add(MapBuildingFootprint(xMin + 6f, zMin + 6f, spanX * 0.56f, spanZ * 0.42f, 3))
                        list.add(MapBuildingFootprint(xMin + 8f, cz + 6f, spanX * 0.82f, spanZ * 0.36f, 1))
                        list.add(MapBuildingFootprint(xMin + spanX * 0.68f, zMin + 8f, spanX * 0.24f, spanZ * 0.38f, 0))
                    }
                    isDowntown -> {
                        // Dense urban skyscraper quads with interior alleyways
                        val splitX = spanX * (0.44f + (seed % 12) * 0.01f)
                        val splitZ = spanZ * (0.45f + ((seed / 7) % 10) * 0.01f)
                        list.add(MapBuildingFootprint(xMin + 4f, zMin + 4f, splitX - 8f, splitZ - 8f, 2))
                        list.add(MapBuildingFootprint(xMin + splitX + 4f, zMin + 4f, spanX - splitX - 8f, splitZ - 6f, 0))
                        list.add(MapBuildingFootprint(xMin + 4f, zMin + splitZ + 4f, splitX - 6f, spanZ - splitZ - 8f, 1))
                        list.add(MapBuildingFootprint(xMin + splitX + 4f, zMin + splitZ + 4f, spanX - splitX - 8f, spanZ - splitZ - 8f, 2))
                    }
                    isRacing -> {
                        // Pit grandstands & paddock buildings
                        list.add(MapBuildingFootprint(xMin + 8f, zMin + 6f, spanX - 16f, spanZ * 0.28f, 2))
                        list.add(MapBuildingFootprint(xMin + 10f, cz + 10f, spanX * 0.42f, spanZ * 0.34f, 0))
                        list.add(MapBuildingFootprint(cx + 8f, cz + 10f, spanX * 0.38f, spanZ * 0.34f, 1))
                    }
                    else -> {
                        // Residential / mixed mid-rise blocks with courtyard spacing
                        val w1 = spanX * 0.38f
                        val d1 = spanZ * 0.36f
                        list.add(MapBuildingFootprint(xMin + 6f, zMin + 6f, w1, d1, 1))
                        list.add(MapBuildingFootprint(xMax - w1 - 6f, zMin + 8f, w1, d1 * 0.92f, 0))
                        list.add(MapBuildingFootprint(xMin + 8f, zMax - d1 - 6f, w1 * 1.05f, d1, 1))
                        list.add(MapBuildingFootprint(xMax - w1 - 6f, zMax - d1 - 6f, w1 * 0.9f, d1 * 0.95f, 2))
                    }
                }
            }
        }
        buildings = list
        parkPonds = ponds
    }
}

// Cartographic palette inspired by realistic dark-mode automotive GPS & GTA V satellite radar
private val MapOceanDeep = Color(0xFF0A243D)
private val MapOceanShallow = Color(0xFF103959)
private val MapCoastFoam = Color(0xFF3E8CB0)
private val MapBeachSand = Color(0xFF8C7B5B)
private val MapUrbanGround = Color(0xFF1A2029)
private val MapParkTurf = Color(0xFF1B3A2A)
private val MapParkLawn = Color(0xFF234D36)
private val MapParkPondColor = Color(0xFF154E6B)
private val MapMountainBase = Color(0xFF1D3125)
private val MapMountainMid = Color(0xFF2B3E2F)
private val MapMountainRidge = Color(0xFF3D4B43)
private val MapContourLine = Color(0x389BB89E)
private val MapBuildingShadow = Color(0xFF0D1117)
private val MapBuildingTone0 = Color(0xFF27303E)
private val MapBuildingTone1 = Color(0xFF2E394A)
private val MapBuildingTone2 = Color(0xFF39475C)
private val MapBuildingTone3 = Color(0xFF343B46)
private val MapRoadCasing = Color(0xFF0B0E14)
private val MapRoadAsphalt = Color(0xFF475266)
private val MapHighwayAsphalt = Color(0xFF56637A)
private val MapCoastalRoad = Color(0xFF4D6478)
private val MapMountainRoad = Color(0xFF5A5652)
private val MapBridgeDeck = Color(0xFF5D7694)
private val MapTunnelDeck = Color(0xFF362E4D)
private val MapLaneMark = Color(0x88CFD8E3)

/**
 * Realistic GTA-style rotating heading-up automotive GPS mini-map:
 * - Centered on the player's car and smoothly rotates with car heading so UP is always straight ahead
 * - Speed-reactive zoom (tight street detail in town, wider view at highway speeds)
 * - Renders actual ocean, sandy beach, bay water, mountain topographic hills, green city parks,
 *   building footprints with shadows, wide cased asphalt avenues with center lane stripes,
 *   live GPS route to the next checkpoint, glowing emerald Repair Bay badges, AI rivals, traffic,
 *   and a live rotating North ('N') compass marker.
 * - Tapping opens the interactive Full Satellite World Map.
 */
@Composable
fun GpsMiniMap(
    telemetry: TelemetrySnapshot,
    onExpandMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Speed-reactive zoom: 215m view radius at low speed, smoothly widening up to 315m at 260+ km/h
    val speedFactor = (telemetry.speedKmh / 260f).coerceIn(0f, 1f)
    val viewRadiusWorld = 215f + speedFactor * 100f

    Surface(
        color = MapOceanDeep,
        border = BorderStroke(1.5.dp, Color(0xFF38465E)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .width(146.dp)
            .height(98.dp)
            .clickable(onClick = onExpandMap)
            .testTag("hud_minimap_radar")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val playerScreenX = w * 0.5f
                val playerScreenY = h * 0.64f // GTA-style lower-center player offset for maximum forward visibility
                val pxPerMeter = (minOf(w, h) * 0.56f) / viewRadiusWorld
                // Rotate map around player by +headingRad (headingRad = 0 faces North/-Z)
                val rotationDeg = Math.toDegrees(telemetry.carHeadingRad.toDouble()).toFloat()

                clipRect {
                    // Base deep ocean background
                    drawRect(color = MapOceanDeep)

                    withTransform({
                        translate(left = playerScreenX, top = playerScreenY)
                        rotate(degrees = rotationDeg, pivot = Offset.Zero)
                        translate(
                            left = -telemetry.carX * pxPerMeter,
                            top = -telemetry.carZ * pxPerMeter
                        )
                    }) {
                        drawWorldCartography(
                            pxPerMeter = pxPerMeter,
                            telemetry = telemetry,
                            isFullMap = false
                        )
                    }

                    // Player forward headlight / GPS vision cone (always points UP)
                    val conePath = Path().apply {
                        moveTo(playerScreenX, playerScreenY - 2f)
                        lineTo(playerScreenX - 24f, playerScreenY - 46f)
                        lineTo(playerScreenX + 24f, playerScreenY - 46f)
                        close()
                    }
                    drawPath(
                        path = conePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, NitroCyan.copy(alpha = 0.22f)),
                            startY = playerScreenY - 46f,
                            endY = playerScreenY
                        )
                    )

                    // Crisp GTA-style player GPS directional arrow (white outline + crimson core)
                    drawPlayerArrow(playerScreenX, playerScreenY, scale = 1.0f)

                    // Edge-clamped Waypoint marker if next checkpoint is outside local GPS zoom
                    val cpX = telemetry.nextCheckpointX
                    val cpZ = telemetry.nextCheckpointZ
                    if (cpX != null && cpZ != null) {
                        val dx = cpX - telemetry.carX
                        val dz = cpZ - telemetry.carZ
                        val cosH = cos(telemetry.carHeadingRad)
                        val sinH = sin(telemetry.carHeadingRad)
                        val rotX = (dx * cosH - dz * sinH) * pxPerMeter
                        val rotY = (dx * sinH + dz * cosH) * pxPerMeter
                        val sx = playerScreenX + rotX
                        val sy = playerScreenY + rotY
                        if (sx !in 8f..(w - 8f) || sy !in 8f..(h - 8f)) {
                            val clampedX = sx.coerceIn(10f, w - 10f)
                            val clampedY = sy.coerceIn(10f, h - 10f)
                            drawCircle(color = ObsidianBlack, radius = 6.5f, center = Offset(clampedX, clampedY))
                            drawCircle(color = ChampionshipGold, radius = 4.6f, center = Offset(clampedX, clampedY))
                        }
                    }

                    // Rotating North compass indicator riding the rim of the GPS bezel with crisp 'N' glyph
                    val northAngleRad = -PI.toFloat() * 0.5f + telemetry.carHeadingRad
                    val rimRx = w * 0.43f
                    val rimRy = h * 0.40f
                    val nx = (w * 0.5f + cos(northAngleRad) * rimRx).coerceIn(11f, w - 11f)
                    val ny = (h * 0.5f + sin(northAngleRad) * rimRy).coerceIn(11f, h - 11f)
                    drawCircle(color = ObsidianBlack.copy(alpha = 0.92f), radius = 8.0f, center = Offset(nx, ny))
                    drawCircle(color = RacingCrimson, radius = 8.0f, center = Offset(nx, ny), style = Stroke(width = 1.5f))
                    val nPath = Path().apply {
                        moveTo(nx - 2.6f, ny + 3.2f)
                        lineTo(nx - 2.6f, ny - 3.2f)
                        lineTo(nx + 2.6f, ny + 3.2f)
                        lineTo(nx + 2.6f, ny - 3.2f)
                    }
                    drawPath(nPath, color = TextPrimary, style = Stroke(width = 1.6f, cap = StrokeCap.Round))

                    // Subtle automotive GPS glass inner vignette frame
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBlack.copy(alpha = 0.32f),
                                Color.Transparent,
                                Color.Transparent,
                                ObsidianBlack.copy(alpha = 0.36f)
                            )
                        )
                    )
                }
            }

            // Subtle top-right MAP expand hint pill
            Surface(
                color = ObsidianBlack.copy(alpha = 0.72f),
                border = BorderStroke(0.75.dp, NitroCyan.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = "Expand World Map",
                        tint = NitroCyan,
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text = "GPS",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Full-Screen Interactive Satellite World Map opened by tapping the HUD GPS Mini-Map.
 * Displays the complete 1320m x 1320m GTA Race X open world with district tags,
 * topography, coastlines, all 4 Repair Bays, traffic, rivals, and live player marker.
 */
@Composable
fun FullWorldMapModal(
    telemetry: TelemetrySnapshot,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.86f))
            .clickable(onClick = onDismiss)
            .testTag("hud_full_map_modal"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = CarbonDark,
            border = BorderStroke(1.5.dp, NitroCyan.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .fillMaxHeight(0.86f)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GTA RACE X • SATELLITE GPS MAP",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "CURRENT ZONE: ${telemetry.districtName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NitroCyan
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MapLegendDot(color = RacingCrimson, label = "YOU")
                        MapLegendDot(color = StatEmerald, label = "REPAIR BAY")
                        MapLegendDot(color = NitroCyan, label = "RIVAL")
                        MapLegendDot(color = ChampionshipGold, label = "TARGET")
                        Surface(
                            color = ObsidianBlack.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, CarbonBorder),
                            shape = CircleShape
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("hud_full_map_close_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close World Map", tint = TextPrimary)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MapOceanDeep)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val worldSpan = CityWorld.WORLD_HALF * 2.15f
                        val pxPerMeter = minOf(size.width, size.height) / worldSpan
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f

                        clipRect {
                            drawRect(MapOceanDeep)
                            withTransform({
                                translate(left = cx, top = cy)
                            }) {
                                drawWorldCartography(
                                    pxPerMeter = pxPerMeter,
                                    telemetry = telemetry,
                                    isFullMap = true
                                )
                                // Rotated player chevron at world position on North-Up full map
                                val px = telemetry.carX * pxPerMeter
                                val py = telemetry.carZ * pxPerMeter
                                val headingDeg = -Math.toDegrees(telemetry.carHeadingRad.toDouble()).toFloat()
                                rotate(degrees = headingDeg, pivot = Offset(px, py)) {
                                    drawPlayerArrow(px, py, scale = 1.25f)
                                }
                            }
                        }
                    }

                    // Crisp cartographic zone labels positioned around the full map
                    MapZoneTag("COASTAL HWY", Color(0xFF38BDF8), Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
                    MapZoneTag("MOUNTAIN HILLS", Color(0xFFF59E0B), Modifier.align(Alignment.TopEnd).padding(10.dp))
                    MapZoneTag("SUSPENSION BRIDGE", NitroCyan, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                    MapZoneTag("CITY PARKS", StatEmerald, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                    MapZoneTag("RACING DISTRICT", RacingCrimson, Modifier.align(Alignment.TopStart).padding(10.dp))
                    MapZoneTag("INDUSTRIAL", TextSecondary, Modifier.align(Alignment.BottomEnd).padding(10.dp))
                    MapZoneTag("RESIDENTIAL", TextSecondary, Modifier.align(Alignment.BottomStart).padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun MapZoneTag(text: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        color = ObsidianBlack.copy(alpha = 0.72f),
        border = BorderStroke(0.75.dp, accent.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = accent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun DrawScope.drawWorldCartography(
    pxPerMeter: Float,
    telemetry: TelemetrySnapshot,
    isFullMap: Boolean
) {
    fun wx(x: Float) = x * pxPerMeter
    fun wz(z: Float) = z * pxPerMeter

    // 1. Shallow turquoise coastal water shelf & foam shoreline along x = -645..-612
    drawRect(
        color = MapOceanShallow,
        topLeft = Offset(wx(-690f), wz(-740f)),
        size = Size(wx(80f), wz(1480f))
    )
    drawLine(
        color = MapCoastFoam.copy(alpha = 0.55f),
        start = Offset(wx(-628f), wz(-700f)),
        end = Offset(wx(-628f), wz(700f)),
        strokeWidth = (4.5f * pxPerMeter).coerceAtLeast(1.2f)
    )

    // 2. Mainland landmass + Golden Coastal Beach Strip (x = -628..720)
    drawRect(
        color = MapBeachSand,
        topLeft = Offset(wx(-628f), wz(-660f)),
        size = Size(wx(78f), wz(1320f))
    )
    drawRect(
        color = MapUrbanGround,
        topLeft = Offset(wx(-550f), wz(-660f)),
        size = Size(wx(1260f), wz(1320f))
    )

    // 3. Lush Green City Parks (South district z = 385..640, x = -420..420)
    drawRect(
        color = MapParkTurf,
        topLeft = Offset(wx(-420f), wz(385f)),
        size = Size(wx(840f), wz(250f))
    )
    drawRoundRect(
        color = MapParkLawn,
        topLeft = Offset(wx(-340f), wz(415f)),
        size = Size(wx(680f), wz(110f)),
        cornerRadius = CornerRadius(wx(24f), wx(24f))
    )
    for (pond in WorldMapCartography.parkPonds) {
        drawCircle(
            color = MapParkPondColor,
            radius = pond.radius * pxPerMeter,
            center = Offset(wx(pond.x), wz(pond.z))
        )
        drawCircle(
            color = MapCoastFoam.copy(alpha = 0.45f),
            radius = pond.radius * pxPerMeter,
            center = Offset(wx(pond.x), wz(pond.z)),
            style = Stroke(width = (1.8f * pxPerMeter).coerceAtLeast(0.8f))
        )
    }

    // 4. Mountain Hills Topographic Elevation Layers & Contour Rings (East x > 405 & North z < -440)
    drawRect(
        color = MapMountainBase,
        topLeft = Offset(wx(405f), wz(-660f)),
        size = Size(wx(305f), wz(1320f))
    )
    drawRect(
        color = MapMountainBase,
        topLeft = Offset(wx(-360f), wz(-660f)),
        size = Size(wx(780f), wz(225f))
    )
    // Mid-elevation foothills
    drawRoundRect(
        color = MapMountainMid,
        topLeft = Offset(wx(465f), wz(-620f)),
        size = Size(wx(220f), wz(1200f)),
        cornerRadius = CornerRadius(wx(45f), wx(45f))
    )
    drawRoundRect(
        color = MapMountainMid,
        topLeft = Offset(wx(-280f), wz(-635f)),
        size = Size(wx(720f), wz(155f)),
        cornerRadius = CornerRadius(wx(40f), wx(40f))
    )
    // High ridge alpine terrain + topographic contour loops
    val contourStroke = Stroke(width = (2.2f * pxPerMeter).coerceAtLeast(0.9f))
    val ridgeCenters = arrayOf(
        Offset(585f, -380f),
        Offset(600f, -80f),
        Offset(580f, 240f),
        Offset(220f, -575f),
        Offset(-80f, -565f)
    )
    for (rc in ridgeCenters) {
        drawCircle(
            color = MapMountainRidge,
            radius = 52f * pxPerMeter,
            center = Offset(wx(rc.x), wz(rc.y))
        )
        drawCircle(
            color = MapContourLine,
            radius = 74f * pxPerMeter,
            center = Offset(wx(rc.x), wz(rc.y)),
            style = contourStroke
        )
        drawCircle(
            color = MapContourLine,
            radius = 40f * pxPerMeter,
            center = Offset(wx(rc.x), wz(rc.y)),
            style = contourStroke
        )
    }

    // 5. Harbor Bay Channel under the Suspension Bridge (x = -138..138, z = -448..-292)
    drawRoundRect(
        color = MapOceanShallow,
        topLeft = Offset(wx(-142f), wz(-450f)),
        size = Size(wx(284f), wz(160f)),
        cornerRadius = CornerRadius(wx(16f), wx(16f))
    )
    drawRoundRect(
        color = MapOceanDeep,
        topLeft = Offset(wx(-126f), wz(-436f)),
        size = Size(wx(252f), wz(132f)),
        cornerRadius = CornerRadius(wx(12f), wx(12f))
    )

    // 6. Realistic Architectural Building Footprints (with subtle roof depth shadow)
    val shadowOff = (2.6f * pxPerMeter).coerceAtLeast(0.7f)
    for (b in WorldMapCartography.buildings) {
        val bx = wx(b.x)
        val bz = wz(b.z)
        val bw = b.w * pxPerMeter
        val bd = b.d * pxPerMeter
        drawRect(
            color = MapBuildingShadow,
            topLeft = Offset(bx + shadowOff, bz + shadowOff),
            size = Size(bw, bd)
        )
        val roofColor = when (b.tone) {
            0 -> MapBuildingTone0
            1 -> MapBuildingTone1
            2 -> MapBuildingTone2
            else -> MapBuildingTone3
        }
        drawRect(
            color = roofColor,
            topLeft = Offset(bx, bz),
            size = Size(bw, bd)
        )
    }

    // 7. Multi-layered Realistic Asphalt Roads (Dark Casing -> Wide Asphalt Surface -> Center Lane Dashes)
    val roadWidthPx = (CityWorld.ROAD_WIDTH * pxPerMeter).coerceAtLeast(if (isFullMap) 3.4f else 6.8f)
    val casingWidthPx = roadWidthPx + (if (isFullMap) 1.8f else 3.2f)
    val laneDash = PathEffect.dashPathEffect(
        floatArrayOf(roadWidthPx * 0.85f, roadWidthPx * 0.75f),
        0f
    )

    // Pass A: Dark Road Casings (curbs & sidewalks)
    for (c in CityWorld.AVENUE_COORDS) {
        // N-S avenues (skip bridge bay gap on x = 0 between z = -445..-295)
        if (abs(c) < 120f) {
            drawLine(MapRoadCasing, Offset(wx(c), wz(-545f)), Offset(wx(c), wz(-446f)), strokeWidth = casingWidthPx, cap = StrokeCap.Square)
            drawLine(MapRoadCasing, Offset(wx(c), wz(-294f)), Offset(wx(c), wz(545f)), strokeWidth = casingWidthPx, cap = StrokeCap.Square)
        } else {
            drawLine(MapRoadCasing, Offset(wx(c), wz(-545f)), Offset(wx(c), wz(545f)), strokeWidth = casingWidthPx, cap = StrokeCap.Square)
        }
        // E-W avenues
        drawLine(MapRoadCasing, Offset(wx(-545f), wz(c)), Offset(wx(545f), wz(c)), strokeWidth = casingWidthPx, cap = StrokeCap.Square)
    }

    // Pass B: Wide Asphalt Carriageways with Road-Class Shading
    for (c in CityWorld.AVENUE_COORDS) {
        val nsColor = when (c) {
            CityWorld.COASTAL_X -> MapCoastalRoad
            CityWorld.MOUNTAIN_X -> MapMountainRoad
            CityWorld.SKYWAY_X -> MapHighwayAsphalt
            else -> MapRoadAsphalt
        }
        if (abs(c) < 120f) {
            drawLine(nsColor, Offset(wx(c), wz(-545f)), Offset(wx(c), wz(-446f)), strokeWidth = roadWidthPx, cap = StrokeCap.Square)
            drawLine(nsColor, Offset(wx(c), wz(-294f)), Offset(wx(c), wz(545f)), strokeWidth = roadWidthPx, cap = StrokeCap.Square)
        } else {
            drawLine(nsColor, Offset(wx(c), wz(-545f)), Offset(wx(c), wz(545f)), strokeWidth = roadWidthPx, cap = StrokeCap.Square)
        }

        val ewColor = when (c) {
            CityWorld.MOUNTAIN_Z -> MapMountainRoad
            else -> MapRoadAsphalt
        }
        drawLine(ewColor, Offset(wx(-545f), wz(c)), Offset(wx(545f), wz(c)), strokeWidth = roadWidthPx, cap = StrokeCap.Square)
    }

    // Special Landmark Road Decks:
    // - Elevated East Skyway (crimson-trimmed expressway)
    drawLine(
        color = RacingCrimson.copy(alpha = 0.72f),
        start = Offset(wx(CityWorld.SKYWAY_X), wz(-320f)),
        end = Offset(wx(CityWorld.SKYWAY_X), wz(300f)),
        strokeWidth = roadWidthPx + 1.8f
    )
    drawLine(
        color = MapHighwayAsphalt,
        start = Offset(wx(CityWorld.SKYWAY_X), wz(-320f)),
        end = Offset(wx(CityWorld.SKYWAY_X), wz(300f)),
        strokeWidth = roadWidthPx * 0.76f
    )

    // - Cable-Stayed Suspension Bridge over Northern Bay (cyan-trimmed span)
    drawLine(
        color = NitroCyan.copy(alpha = 0.85f),
        start = Offset(wx(-CityWorld.BRIDGE_HALF_SPAN), wz(CityWorld.BRIDGE_Z)),
        end = Offset(wx(CityWorld.BRIDGE_HALF_SPAN), wz(CityWorld.BRIDGE_Z)),
        strokeWidth = roadWidthPx + 2.0f
    )
    drawLine(
        color = MapBridgeDeck,
        start = Offset(wx(-CityWorld.BRIDGE_HALF_SPAN), wz(CityWorld.BRIDGE_Z)),
        end = Offset(wx(CityWorld.BRIDGE_HALF_SPAN), wz(CityWorld.BRIDGE_Z)),
        strokeWidth = roadWidthPx * 0.78f
    )

    // - West Neon Tunnel Corridor
    drawLine(
        color = MapTunnelDeck,
        start = Offset(wx(CityWorld.TUNNEL_X), wz(-CityWorld.TUNNEL_HALF_LEN)),
        end = Offset(wx(CityWorld.TUNNEL_X), wz(CityWorld.TUNNEL_HALF_LEN)),
        strokeWidth = roadWidthPx * 0.92f
    )

    // Pass C: Subtle Dashed Center Lane Markings (visible on zoomed mini-map for realistic road feel)
    if (!isFullMap) {
        val laneStroke = (roadWidthPx * 0.14f).coerceAtLeast(0.8f)
        for (c in CityWorld.AVENUE_COORDS) {
            if (abs(c) < 120f) {
                drawLine(MapLaneMark, Offset(wx(c), wz(-540f)), Offset(wx(c), wz(-448f)), strokeWidth = laneStroke, pathEffect = laneDash)
                drawLine(MapLaneMark, Offset(wx(c), wz(-292f)), Offset(wx(c), wz(540f)), strokeWidth = laneStroke, pathEffect = laneDash)
            } else {
                drawLine(MapLaneMark, Offset(wx(c), wz(-540f)), Offset(wx(c), wz(540f)), strokeWidth = laneStroke, pathEffect = laneDash)
            }
            drawLine(MapLaneMark, Offset(wx(-540f), wz(c)), Offset(wx(540f), wz(c)), strokeWidth = laneStroke, pathEffect = laneDash)
        }
    }

    // 8. Live GPS Navigation Route Line toward Active Checkpoint
    val cpX = telemetry.nextCheckpointX
    val cpZ = telemetry.nextCheckpointZ
    if (cpX != null && cpZ != null) {
        val gpsWidth = (roadWidthPx * 0.48f).coerceAtLeast(2.4f)
        // L-shaped road-following GPS preview (snaps to nearest axis corner for realistic sat-nav routing)
        val turnX = if (abs(cpX - telemetry.carX) > abs(cpZ - telemetry.carZ)) cpX else telemetry.carX
        val turnZ = if (abs(cpX - telemetry.carX) > abs(cpZ - telemetry.carZ)) telemetry.carZ else cpZ
        val routePath = Path().apply {
            moveTo(wx(telemetry.carX), wz(telemetry.carZ))
            lineTo(wx(turnX), wz(turnZ))
            lineTo(wx(cpX), wz(cpZ))
        }
        drawPath(
            path = routePath,
            color = ChampionshipGold.copy(alpha = 0.28f),
            style = Stroke(width = gpsWidth * 2.1f, cap = StrokeCap.Round)
        )
        drawPath(
            path = routePath,
            color = ChampionshipGold,
            style = Stroke(width = gpsWidth, cap = StrokeCap.Round)
        )
        // Glowing Target Waypoint Ring
        val targetR = if (isFullMap) 6.5f else 6.0f
        drawCircle(color = ObsidianBlack, radius = targetR + 2f, center = Offset(wx(cpX), wz(cpZ)))
        drawCircle(color = ChampionshipGold, radius = targetR, center = Offset(wx(cpX), wz(cpZ)))
        drawCircle(color = TextPrimary, radius = targetR * 0.42f, center = Offset(wx(cpX), wz(cpZ)))
    }

    // 9. World Blips: 4 Glowing Repair Bays, AI Traffic Vehicles & AI Street Rivals
    val bayRadius = if (isFullMap) 6.0f else 6.5f
    for (bay in CityWorld.REPAIR_BAYS) {
        val bx = wx(bay.x)
        val bz = wz(bay.z)
        drawCircle(color = ObsidianBlack.copy(alpha = 0.92f), radius = bayRadius + 1.5f, center = Offset(bx, bz))
        drawCircle(color = StatEmerald, radius = bayRadius, center = Offset(bx, bz), style = Stroke(width = 1.8f))
        val cross = bayRadius * 0.55f
        drawLine(StatEmerald, Offset(bx - cross, bz), Offset(bx + cross, bz), strokeWidth = 2.0f, cap = StrokeCap.Round)
        drawLine(StatEmerald, Offset(bx, bz - cross), Offset(bx, bz + cross), strokeWidth = 2.0f, cap = StrokeCap.Round)
    }

    for (blip in telemetry.radarBlips) {
        if (blip.isRepairBay) continue // Already rendered crisply from CityWorld.REPAIR_BAYS
        val bx = wx(blip.x)
        val bz = wz(blip.z)
        if (blip.isRival) {
            val rSize = if (isFullMap) 5.2f else 5.0f
            drawCircle(color = ObsidianBlack, radius = rSize + 1.4f, center = Offset(bx, bz))
            drawCircle(color = TextPrimary, radius = rSize, center = Offset(bx, bz))
            drawCircle(color = NitroCyan, radius = rSize * 0.68f, center = Offset(bx, bz))
        } else {
            // Subtle realistic traffic vehicle marker
            val tSize = if (isFullMap) 2.2f else 2.6f
            drawCircle(color = ObsidianBlack.copy(alpha = 0.7f), radius = tSize + 0.9f, center = Offset(bx, bz))
            drawCircle(color = Color(0xFFE2E8F0).copy(alpha = 0.72f), radius = tSize, center = Offset(bx, bz))
        }
    }
}

private fun DrawScope.drawPlayerArrow(cx: Float, cy: Float, scale: Float) {
    val tipY = cy - 9.5f * scale
    val tailY = cy + 6.5f * scale
    val notchY = cy + 3.2f * scale
    val wingX = 6.8f * scale

    val arrowPath = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx - wingX, tailY)
        lineTo(cx, notchY)
        lineTo(cx + wingX, tailY)
        close()
    }
    // High-contrast dark drop halo + crisp white outline + racing crimson fill
    drawPath(arrowPath, color = ObsidianBlack, style = Stroke(width = 3.8f * scale))
    drawPath(arrowPath, color = TextPrimary, style = Stroke(width = 2.0f * scale))
    drawPath(arrowPath, color = RacingCrimson)
}
