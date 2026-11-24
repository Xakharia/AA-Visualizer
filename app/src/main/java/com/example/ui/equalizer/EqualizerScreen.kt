package com.example.ui.equalizer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppRouting
import com.example.model.DeviceRouting
import com.example.model.EQ_BANDS_10
import com.example.model.EqProfile
import com.example.model.OutputDeviceType
import com.example.model.ProfileCategory
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun EqualizerScreen(
    activeProfile: EqProfile,
    profiles: List<EqProfile>,
    eqEnabled: Boolean,
    dspStatus: String,
    currentDevice: OutputDeviceType,
    deviceDetailName: String,
    deviceRoutings: List<DeviceRouting>,
    appRoutings: List<AppRouting>,
    autoEqSwitchEnabled: Boolean,
    onToggleEq: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onMasterGainChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit,
    onToggleAgc: () -> Unit,
    onToggleSmartBass: () -> Unit,
    onToggleLimiter: () -> Unit,
    onCreateCustom: (String) -> Unit,
    onDuplicate: (EqProfile) -> Unit,
    onRename: (EqProfile, String) -> Unit,
    onMoveOrder: (Long, Int) -> Unit,
    onDeleteCustom: (Long) -> Unit,
    onSetAutoEqSwitch: (Boolean) -> Unit,
    onSetDeviceRouting: (OutputDeviceType, Long, Boolean) -> Unit,
    onSetAppRouting: (String, String, Long, Boolean) -> Unit,
    onDeleteAppRouting: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<ProfileCategory?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<EqProfile?>(null) }
    var showAddAppRuleDialog by remember { mutableStateOf(false) }

    val displayedProfiles = remember(profiles, selectedCategory) {
        if (selectedCategory == null) profiles else profiles.filter { it.category == selectedCategory }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Master Toggle
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "10-Band EQ • ${activeProfile.name}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$dspStatus • $deviceDetailName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = { onToggleEq() },
                    modifier = Modifier.testTag("master_eq_switch")
                )
            }
        }

        // 10-Band Graphic EQ Rack + Real-Time Spline Curve
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    val primary = MaterialTheme.colorScheme.primary
                    val secondary = MaterialTheme.colorScheme.secondary
                    val outline = MaterialTheme.colorScheme.outline

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Horizontal dB gridlines (-12, -6, 0, +6, +12)
                        for (step in 0..4) {
                            val y = size.height * (0.1f + step * 0.2f)
                            drawLine(
                                color = outline.copy(alpha = if (step == 2) 0.6f else 0.22f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = if (step == 2) 2f else 1f
                            )
                        }
                        // Continuous spline curve through 10 bands
                        val path = Path()
                        val bands = activeProfile.bandsDb
                        val pts = bands.mapIndexed { idx, db ->
                            val x = size.width * ((idx + 0.5f) / 10f)
                            val norm = ((12f - db) / 24f).coerceIn(0f, 1f)
                            val y = size.height * (0.1f + norm * 0.8f)
                            Offset(x, y)
                        }
                        if (pts.isNotEmpty()) {
                            path.moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) {
                                val prev = pts[i - 1]
                                val cur = pts[i]
                                val cx = (prev.x + cur.x) * 0.5f
                                path.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
                            }
                            drawPath(
                                path = path,
                                brush = Brush.horizontalGradient(listOf(primary, secondary)),
                                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EQ_BANDS_10.forEach { band ->
                            val dbVal = activeProfile.bandsDb.getOrElse(band.index) { 0f }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${if (dbVal >= 0) "+" else ""}${dbVal.toInt()}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = if (eqEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = dbVal,
                                    onValueChange = { onBandChange(band.index, it) },
                                    valueRange = -12f..12f,
                                    enabled = eqEnabled,
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalSliderLayout()
                                        .testTag("eq_band_${band.index}")
                                )
                                Text(
                                    text = band.shortLabel,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Additional DSP Controls
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Audio Dynamics & Tone", style = MaterialTheme.typography.titleMedium)

                DspSliderRow("Bass Boost", "${activeProfile.bassBoost / 10}%", activeProfile.bassBoost.toFloat(), 0f..1000f) {
                    onBassBoostChange(it.toInt())
                }
                DspSliderRow(
                    "Treble Boost",
                    "%+.1f dB".format(Locale.US, activeProfile.trebleBoostDb),
                    activeProfile.trebleBoostDb,
                    -12f..12f
                ) { onTrebleChange(it) }
                DspSliderRow(
                    "Master Gain",
                    "%+.1f dB".format(Locale.US, activeProfile.masterGainDb),
                    activeProfile.masterGainDb,
                    -12f..12f
                ) { onMasterGainChange(it) }
                DspSliderRow(
                    "L / R Balance",
                    when {
                        activeProfile.balance < -0.05f -> "L ${(-activeProfile.balance * 100).toInt()}%"
                        activeProfile.balance > 0.05f -> "R ${(activeProfile.balance * 100).toInt()}%"
                        else -> "Center"
                    },
                    activeProfile.balance,
                    -1f..1f
                ) { onBalanceChange(it) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeProfile.agcEnabled,
                        onClick = onToggleAgc,
                        label = { Text("AGC") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = activeProfile.smartBassEnabled,
                        onClick = onToggleSmartBass,
                        label = { Text("Smart Bass") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = activeProfile.limiterEnabled,
                        onClick = onToggleLimiter,
                        label = { Text("Clip Limit") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 34 Audio Profiles + Custom Profile Manager
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EQ Profiles (${profiles.size})", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onDuplicate(activeProfile) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Duplicate")
                        }
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.testTag("create_profile_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New")
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All (${profiles.size})") }
                        )
                    }
                    items(ProfileCategory.entries) { cat ->
                        val count = profiles.count { it.category == cat }
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.label} ($count)") }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    displayedProfiles.forEach { profile ->
                        val selected = profile.id == activeProfile.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProfile(profile.id) }
                                .testTag("profile_row_${profile.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = "${profile.category.label} • Gain ${profile.masterGainDb.toInt()} dB • Bass ${profile.bassBoost / 10}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onMoveOrder(profile.id, -1) }) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onMoveOrder(profile.id, 1) }) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                    }
                                    if (!profile.isBuiltIn) {
                                        IconButton(onClick = { renameTarget = profile }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { onDeleteCustom(profile.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Device-Specific & Per-App Auto-Routing
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Profile Routing", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Active output: ${currentDevice.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(checked = autoEqSwitchEnabled, onCheckedChange = onSetAutoEqSwitch)
                }

                Text("Output Device Profiles", style = MaterialTheme.typography.labelLarge)
                deviceRoutings.forEach { rule ->
                    RoutingRow(
                        title = rule.deviceType.label,
                        subtitle = if (rule.deviceType == currentDevice) "Connected Now" else "Standby",
                        assignedProfileName = profiles.firstOrNull { it.id == rule.profileId }?.name ?: "Default",
                        enabled = rule.autoSwitchEnabled,
                        profiles = profiles,
                        onSelectProfile = { pid -> onSetDeviceRouting(rule.deviceType, pid, rule.autoSwitchEnabled) },
                        onToggleEnabled = { en -> onSetDeviceRouting(rule.deviceType, rule.profileId, en) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Per-App Profiles", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { showAddAppRuleDialog = true }) {
                        Text("+ Add App Rule")
                    }
                }
                appRoutings.forEach { rule ->
                    RoutingRow(
                        title = rule.appLabel,
                        subtitle = rule.packageName,
                        assignedProfileName = profiles.firstOrNull { it.id == rule.profileId }?.name ?: "Default",
                        enabled = rule.autoSwitchEnabled,
                        profiles = profiles,
                        onSelectProfile = { pid -> onSetAppRouting(rule.packageName, rule.appLabel, pid, rule.autoSwitchEnabled) },
                        onToggleEnabled = { en -> onSetAppRouting(rule.packageName, rule.appLabel, rule.profileId, en) },
                        onDelete = { onDeleteAppRouting(rule.packageName) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showCreateDialog) {
        ProfileNameDialog(
            title = "New Custom EQ Profile",
            initialName = "My Studio EQ",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateCustom(name)
                showCreateDialog = false
            }
        )
    }

    renameTarget?.let { target ->
        ProfileNameDialog(
            title = "Rename Profile",
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRename(target, name)
                renameTarget = null
            }
        )
    }

    if (showAddAppRuleDialog) {
        AddAppRuleDialog(
            defaultProfileId = activeProfile.id,
            onDismiss = { showAddAppRuleDialog = false },
            onConfirm = { pkg, label ->
                onSetAppRouting(pkg, label, activeProfile.id, true)
                showAddAppRuleDialog = false
            }
        )
    }
}

@Composable
private fun DspSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun RoutingRow(
    title: String,
    subtitle: String,
    assignedProfileName: String,
    enabled: Boolean,
    profiles: List<EqProfile>,
    onSelectProfile: (Long) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(assignedProfileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    profiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name} (${p.category.label})") },
                            onClick = {
                                onSelectProfile(p.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggleEnabled, modifier = Modifier.padding(start = 6.dp))
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove rule", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Profile name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddAppRuleDialog(
    defaultProfileId: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Per-App EQ Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("App Name (e.g. Tidal)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Package ID (e.g. com.aspiro.tidal)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (pkg.isNotBlank() && label.isNotBlank()) onConfirm(pkg.trim(), label.trim())
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun Modifier.verticalSliderLayout(): Modifier = this
    .graphicsLayer {
        rotationZ = 270f
        transformOrigin = TransformOrigin(0f, 0f)
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxHeight
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(-placeable.width, 0)
        }
    }
