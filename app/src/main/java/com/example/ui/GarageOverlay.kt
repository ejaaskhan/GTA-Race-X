package com.example.ui

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BodyKitStyle
import com.example.data.GameCatalog
import com.example.data.HeadlightStyle
import com.example.data.RimStyle
import com.example.data.SpoilerStyle
import com.example.data.WindowTint
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.StatEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun GarageOverlay(
    state: GameUiState,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onPreviewCar: (String) -> Unit,
    onEquipCar: (String) -> Unit,
    onUnlockCar: (String) -> Unit,
    onUpdateVisuals: (
        paintHex: String?,
        rimStyle: RimStyle?,
        underglowHex: String?,
        spoilerStyle: SpoilerStyle?,
        bodyKitStyle: BodyKitStyle?,
        windowTint: WindowTint?,
        headlightStyle: HeadlightStyle?
    ) -> Unit,
    onUpgradeStat: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    var selectedTab by remember { mutableIntStateOf(0) } // 0=VEHICLES, 1=CUSTOMIZE, 2=UPGRADES

    val build = state.activeCarBuild
    val isOwned = state.garageCars.containsKey(build.spec.id)
    val isEquipped = state.profile.selectedCarId == build.spec.id
    val levelEligible = state.profile.level >= build.spec.unlockLevel.toLong()
    val canBuyEarly = state.profile.credits >= build.spec.price

    // Leave left-center unobstructed for 360-degree touch drag over the dedicated Lit Neon Showroom Stage!
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top-Left: Back Button, Vehicle Title, Class Pill & 360 Drag Hint
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = CarbonDark.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, CarbonBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("garage_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Main Menu",
                            tint = TextPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("3D SHOWROOM", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Surface(
                            color = RacingCrimson.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, RacingCrimson),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = build.spec.vehicleClass.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(build.spec.name, style = MaterialTheme.typography.labelLarge, color = RacingCrimson)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = CarbonDark.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, NitroCyan.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Orbit car", tint = NitroCyan, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.garage_drag_hint), style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }

        // Bottom-Left: 6-Stat Live Vehicle Telemetry Card (Top Speed, Accel, Handling, Braking, Weight, Nitro Cap)
        Surface(
            color = CarbonDark.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, CarbonBorder),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(292.dp)
                .testTag("garage_stats_card")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CarStatBar(
                        label = "TOP SPEED",
                        valueText = "${build.effectiveTopSpeedKmh.toInt()} KM/H",
                        progress = (build.effectiveTopSpeedKmh / 430f).coerceIn(0f, 1f),
                        color = RacingCrimson,
                        modifier = Modifier.weight(1f)
                    )
                    CarStatBar(
                        label = "ACCEL",
                        valueText = "${((build.effectiveAcceleration / 82f) * 100f).toInt().coerceIn(10, 100)}",
                        progress = (build.effectiveAcceleration / 82f).coerceIn(0f, 1f),
                        color = NitroCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CarStatBar(
                        label = "HANDLING",
                        valueText = "${((build.effectiveHandling / 2.15f) * 100f).toInt().coerceIn(10, 100)}",
                        progress = (build.effectiveHandling / 2.15f).coerceIn(0f, 1f),
                        color = StatEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    CarStatBar(
                        label = "BRAKING",
                        valueText = "${((build.effectiveBraking / 2.0f) * 100f).toInt().coerceIn(10, 100)}",
                        progress = (build.effectiveBraking / 2.0f).coerceIn(0f, 1f),
                        color = ChampionshipGold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CarStatBar(
                        label = "WEIGHT",
                        valueText = "${String.format(Locale.US, "%,d", build.effectiveWeightKg.toInt())} KG",
                        progress = (1f - ((build.effectiveWeightKg - 1050f) / 750f)).coerceIn(0.15f, 1f),
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    CarStatBar(
                        label = "NITRO CAP",
                        valueText = "${(build.effectiveNitroCapacity * 100f).toInt()}%",
                        progress = (build.effectiveNitroCapacity / 2.3f).coerceIn(0f, 1f),
                        color = NitroCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Right-Side Premium Carbon Glass Workbench Panel
        Surface(
            color = CarbonDark.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, CarbonBorder),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(360.dp)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header: Driver Level Pill (opens Profile) + Collection Count + Cash Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = GraphiteSurface,
                        border = BorderStroke(1.dp, NitroCyan.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clickable(onClick = onOpenProfile)
                            .testTag("garage_profile_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = "Driver Profile", tint = NitroCyan, modifier = Modifier.size(14.dp))
                            Text("LVL ${state.profile.level}", style = MaterialTheme.typography.labelSmall, color = NitroCyan)
                            Text("• ${state.garageCars.size}/${GameCatalog.cars.size}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }

                    Text(
                        text = "${String.format(Locale.US, "%,d", state.profile.credits)} CR",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ChampionshipGold,
                        modifier = Modifier.testTag("garage_credits_text")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3 Tabs: VEHICLES, CUSTOMIZE, UPGRADES
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GarageTabButton("VEHICLES", selectedTab == 0, "garage_tab_vehicles", Modifier.weight(1f)) { selectedTab = 0 }
                    GarageTabButton("CUSTOMIZE", selectedTab == 1, "garage_tab_style", Modifier.weight(1f)) { selectedTab = 1 }
                    GarageTabButton("UPGRADES", selectedTab == 2, "garage_tab_tuning", Modifier.weight(1f)) { selectedTab = 2 }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Content Body
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier.testTag("garage_vehicles_list")
                            ) {
                                items(GameCatalog.cars, key = { it.id }) { car ->
                                    val carOwned = state.garageCars.containsKey(car.id)
                                    val selected = car.id == build.spec.id
                                    val equipped = car.id == state.profile.selectedCarId
                                    val canClaimByLevel = !carOwned && state.profile.level >= car.unlockLevel.toLong()
                                    Surface(
                                        color = if (selected) GraphiteElevated else GraphiteSurface,
                                        border = BorderStroke(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = when {
                                                selected -> RacingCrimson
                                                canClaimByLevel -> StatEmerald
                                                else -> CarbonBorder
                                            }
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("garage_car_${car.id}")
                                            .clickable { onPreviewCar(car.id) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(car.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                                Text(
                                                    "${car.vehicleClass.label} • ${car.baseTopSpeedKmh.toInt()} KM/H • ${car.weightKg.toInt()} KG",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                            when {
                                                equipped -> Text("EQUIPPED", style = MaterialTheme.typography.labelSmall, color = StatEmerald)
                                                carOwned -> Text("OWNED", style = MaterialTheme.typography.labelSmall, color = NitroCyan)
                                                canClaimByLevel -> Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Filled.Star, contentDescription = "Free Level Unlock", tint = StatEmerald, modifier = Modifier.size(14.dp))
                                                    Text("CLAIM FREE", style = MaterialTheme.typography.labelSmall, color = StatEmerald)
                                                }
                                                else -> Column(horizontalAlignment = Alignment.End) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = ChampionshipGold, modifier = Modifier.size(13.dp))
                                                        Text("${String.format(Locale.US, "%,d", car.price)} CR", style = MaterialTheme.typography.labelSmall, color = ChampionshipGold)
                                                    }
                                                    Text("OR LVL ${car.unlockLevel}", style = MaterialTheme.typography.labelSmall, color = NitroCyan)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (!isOwned) {
                                LockedCarNotice("UNLOCK THIS VEHICLE TO CUSTOMIZE PAINT, WHEELS, SPOILERS, BODY KITS, TINT & HEADLIGHTS")
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.testTag("garage_customize_list")
                                ) {
                                    // 1. Metallic Body Paint
                                    item {
                                        Text("METALLIC CAR PAINT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            GameCatalog.paints.forEach { swatch ->
                                                val chosen = build.paintHex.equals(swatch.hex, ignoreCase = true)
                                                val swColor = parseComposeColor(swatch.hex)
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(swColor)
                                                        .clickable {
                                                            onUpdateVisuals(swatch.hex, null, null, null, null, null, null)
                                                        }
                                                        .testTag("paint_${swatch.name.lowercase().replace(" ", "_")}")
                                                ) {
                                                    if (chosen) {
                                                        Icon(Icons.Filled.Check, contentDescription = swatch.name, tint = ObsidianBlack, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2. Alloy Wheels / Rims
                                    item {
                                        Text("WHEELS & ALLOY FINISH", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            RimStyle.entries.forEach { rim ->
                                                CustomOptionChip(
                                                    label = rim.label,
                                                    selected = build.rimStyle == rim,
                                                    tag = "rim_${rim.name.lowercase()}",
                                                    modifier = Modifier.weight(1f)
                                                ) { onUpdateVisuals(null, rim, null, null, null, null, null) }
                                            }
                                        }
                                    }

                                    // 3. 3D Spoilers
                                    item {
                                        Text("REAR AERO SPOILER", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            SpoilerStyle.entries.forEach { sp ->
                                                CustomOptionChip(
                                                    label = sp.label,
                                                    selected = build.spoilerStyle == sp,
                                                    tag = "spoiler_${sp.name.lowercase()}",
                                                    modifier = Modifier.weight(1f)
                                                ) { onUpdateVisuals(null, null, null, sp, null, null, null) }
                                            }
                                        }
                                    }

                                    // 4. 3D Body Kits
                                    item {
                                        Text("BODY KIT & AERO PACKAGE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            BodyKitStyle.entries.forEach { kit ->
                                                CustomOptionChip(
                                                    label = kit.label,
                                                    selected = build.bodyKitStyle == kit,
                                                    tag = "bodykit_${kit.name.lowercase()}",
                                                    modifier = Modifier.weight(1f)
                                                ) { onUpdateVisuals(null, null, null, null, kit, null, null) }
                                            }
                                        }
                                    }

                                    // 5. Window Tint
                                    item {
                                        Text("WINDOW TINT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            WindowTint.entries.forEach { tint ->
                                                CustomOptionChip(
                                                    label = tint.label,
                                                    selected = build.windowTint == tint,
                                                    tag = "tint_${tint.name.lowercase()}",
                                                    modifier = Modifier.weight(1f)
                                                ) { onUpdateVisuals(null, null, null, null, null, tint, null) }
                                            }
                                        }
                                    }

                                    // 6. Headlights Color
                                    item {
                                        Text("LED HEADLIGHTS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            HeadlightStyle.entries.forEach { hl ->
                                                CustomOptionChip(
                                                    label = hl.label,
                                                    selected = build.headlightStyle == hl,
                                                    tag = "headlight_${hl.name.lowercase()}",
                                                    modifier = Modifier.weight(1f)
                                                ) { onUpdateVisuals(null, null, null, null, null, null, hl) }
                                            }
                                        }
                                    }

                                    // 7. Neon Underglow (preserved)
                                    item {
                                        Text("NEON UNDERGLOW", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GameCatalog.underglows.forEach { glow ->
                                                val chosen = build.underglowHex.equals(glow.hex, ignoreCase = true)
                                                val glowCol = if (glow.hex == "NONE") GraphiteElevated else parseComposeColor(glow.hex)
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(glowCol)
                                                        .clickable {
                                                            onUpdateVisuals(null, null, glow.hex, null, null, null, null)
                                                        }
                                                        .testTag("underglow_${glow.name.lowercase().replace(" ", "_")}")
                                                ) {
                                                    if (glow.hex == "NONE") {
                                                        Text("X", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                                    } else if (chosen) {
                                                        Icon(Icons.Filled.Check, contentDescription = glow.name, tint = ObsidianBlack, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            if (!isOwned) {
                                LockedCarNotice("UNLOCK THIS VEHICLE TO INSTALL PERFORMANCE UPGRADES")
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                    modifier = Modifier.testTag("garage_upgrades_list")
                                ) {
                                    item {
                                        UpgradeRow("ENGINE", "Top speed & torque", build.engineLevel, state.profile.credits) { onUpgradeStat("ENGINE") }
                                    }
                                    item {
                                        UpgradeRow("TURBO", "High-RPM boost & surge", build.turboLevel, state.profile.credits) { onUpgradeStat("TURBO") }
                                    }
                                    item {
                                        UpgradeRow("BRAKES", "Shorter stopping distance", build.brakesLevel, state.profile.credits) { onUpgradeStat("BRAKES") }
                                    }
                                    item {
                                        UpgradeRow("TIRES", "Dry & wet Pacejka grip", build.tiresLevel, state.profile.credits) { onUpgradeStat("TIRES") }
                                    }
                                    item {
                                        UpgradeRow("SUSPENSION", "Anti-roll & sharp turn-in", build.suspensionLevel, state.profile.credits) { onUpgradeStat("SUSPENSION") }
                                    }
                                    item {
                                        UpgradeRow("NITRO", "Boost thrust & tank cap", build.nitroLevel, state.profile.credits) { onUpgradeStat("NITRO") }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Bottom Action: Equip, Claim Free (Level Unlock), or Early Purchase with Cash
                Button(
                    onClick = {
                        if (isOwned) onEquipCar(build.spec.id) else onUnlockCar(build.spec.id)
                    },
                    enabled = !isEquipped && (isOwned || levelEligible || canBuyEarly),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isOwned -> RacingCrimson
                            levelEligible -> StatEmerald
                            else -> ChampionshipGold
                        },
                        contentColor = if (isOwned) TextPrimary else ObsidianBlack
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("garage_equip_unlock_button")
                ) {
                    val label = when {
                        isEquipped -> "CURRENTLY EQUIPPED"
                        isOwned -> "EQUIP ${build.spec.name}"
                        levelEligible -> "CLAIM FREE (LVL ${build.spec.unlockLevel} UNLOCKED)"
                        else -> "BUY FOR ${String.format(Locale.US, "%,d", build.spec.price)} CR"
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun GarageTabButton(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) RacingCrimson else GraphiteSurface,
        border = BorderStroke(1.dp, if (selected) RacingCrimson else CarbonBorder),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .height(36.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun CustomOptionChip(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) RacingCrimson else GraphiteSurface,
        border = BorderStroke(1.dp, if (selected) RacingCrimson else CarbonBorder),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .height(36.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun CarStatBar(
    label: String,
    valueText: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = GraphiteElevated,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun UpgradeRow(
    label: String,
    subtitle: String,
    level: Int,
    credits: Long,
    onUpgrade: () -> Unit
) {
    val cost = GameCatalog.upgradeCost(level)
    val isMax = level >= 5
    Surface(
        color = GraphiteSurface,
        border = BorderStroke(1.dp, CarbonBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$label • LVL $level/5", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    for (i in 1..5) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(4.dp)
                                .background(
                                    if (i <= level) StatEmerald else GraphiteElevated,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            Button(
                onClick = onUpgrade,
                enabled = !isMax && credits >= cost,
                colors = ButtonDefaults.buttonColors(containerColor = RacingCrimson, contentColor = TextPrimary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("upgrade_${label.lowercase()}")
            ) {
                Text(
                    text = if (isMax) "MAX" else "${String.format(Locale.US, "%,d", cost)} CR",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun LockedCarNotice(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun parseComposeColor(hex: String): Color {
    return try {
        Color(AndroidColor.parseColor(hex))
    } catch (_: Exception) {
        RacingCrimson
    }
}
