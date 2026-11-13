package app.homecontrol.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.homecontrol.automation.Scheduler
import app.homecontrol.data.Automation
import java.time.format.DateTimeFormatter

private val DAY_LABELS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
fun AutomationsScreen(vm: HomeViewModel, modifier: Modifier) {
    var editing by remember { mutableStateOf<Automation?>(null) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Automations", style = MaterialTheme.typography.headlineMedium) }
            if (vm.automations.isEmpty()) {
                item { Text("Nothing scheduled yet. Tap \"New\" to add, for example, morning lights.") }
            }
            items(vm.automations, key = { it.id }) { a ->
                AutomationCard(vm, a, onEdit = { editing = a })
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                editing = Automation(
                    name = "Morning lights", hour = 7, minute = 0,
                    deviceIds = vm.devices.map { it.id }, brightness = 80,
                )
            },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    editing?.let { a ->
        AutomationEditor(
            vm, a,
            onDismiss = { editing = null },
            onSave = { vm.saveAutomation(it); editing = null },
            onDelete = { vm.deleteAutomation(a); editing = null },
        )
    }
}

@Composable
private fun AutomationCard(vm: HomeViewModel, a: Automation, onEdit: () -> Unit) {
    val next = Scheduler.nextRun(a)?.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))
    val count = a.deviceIds.count { id -> vm.devices.any { it.id == id } }
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("%02d:%02d  %s".format(a.hour, a.minute, a.name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${daysText(a.days)} · ${if (a.turnOn) "on at ${a.brightness}%" else "off"} · $count device(s)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(a.enabled, { vm.setEnabled(a, it) })
            }
            if (a.enabled && next != null) Text("Next: $next", style = MaterialTheme.typography.bodySmall)
            if (a.lastRun.isNotBlank()) Text(a.lastRun, style = MaterialTheme.typography.bodySmall)
            TextButton({ vm.runNow(a) }, contentPadding = PaddingValues(0.dp)) { Text("Run now") }
        }
    }
}

private fun daysText(days: Set<Int>) = when {
    days.isEmpty() || days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString(" ") { DAY_LABELS[it - 1] }
}

@Composable
private fun AutomationEditor(
    vm: HomeViewModel,
    initial: Automation,
    onDismiss: () -> Unit,
    onSave: (Automation) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val isNew = vm.automations.none { it.id == initial.id }
    var name by remember { mutableStateOf(initial.name) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var days by remember { mutableStateOf(initial.days) }
    var deviceIds by remember { mutableStateOf(initial.deviceIds.toSet()) }
    var turnOn by remember { mutableStateOf(initial.turnOn) }
    var brightness by remember { mutableFloatStateOf(initial.brightness.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New automation" else "Edit automation") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedButton(
                    onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show() },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Time: %02d:%02d".format(hour, minute)) }

                Text("Days", Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        val day = i + 1
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(label.take(1)) },
                        )
                    }
                }

                Text("Action", Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(turnOn, { turnOn = true }, { Text("Turn on") })
                    FilterChip(!turnOn, { turnOn = false }, { Text("Turn off") })
                }
                if (turnOn) {
                    Text("Brightness ${brightness.toInt()}%")
                    Slider(brightness, { brightness = it }, valueRange = 1f..100f)
                }

                Text("Devices", Modifier.padding(top = 8.dp))
                vm.devices.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(d.id in deviceIds, { deviceIds = if (it) deviceIds + d.id else deviceIds - d.id })
                        Text("${d.name} (${d.brand.label})")
                    }
                }
                if (vm.devices.isEmpty()) Text("Add devices in Setup first.", style = MaterialTheme.typography.bodySmall)
                if (!isNew) TextButton(onDelete) { Text("Delete automation") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && deviceIds.isNotEmpty(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(), hour = hour, minute = minute, days = days,
                            deviceIds = vm.devices.map { it.id }.filter { it in deviceIds },
                            turnOn = turnOn, brightness = brightness.toInt(), enabled = true,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
