package app.homecontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.homecontrol.data.Brand
import app.homecontrol.data.Device

@Composable
fun DevicesScreen(vm: HomeViewModel, modifier: Modifier) {
    var renaming by remember { mutableStateOf<Device?>(null) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("My home", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton({ vm.refreshStates() }) { Icon(Icons.Filled.Refresh, "Refresh") }
            FilledTonalButton({ vm.allOff() }) { Text("All off") }
        }
        if (vm.devices.isEmpty()) {
            Text(
                "No devices yet. Open Setup to connect your Hue Bridge, Tapo and Govee devices.",
                Modifier.padding(16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Brand.entries.forEach { brand ->
                val group = vm.devices.filter { it.brand == brand }
                if (group.isNotEmpty()) {
                    item { Text(brand.label, style = MaterialTheme.typography.titleSmall) }
                    items(group, key = { it.id }) { d -> DeviceCard(vm, d, onRename = { renaming = d }) }
                }
            }
        }
    }
    renaming?.let { d ->
        var name by remember(d.id) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.renameDevice(d, name.trim()); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton({ vm.removeDevice(d); renaming = null }) { Text("Remove device") } },
        )
    }
}

@Composable
private fun DeviceCard(vm: HomeViewModel, d: Device, onRename: () -> Unit) {
    val state = vm.states[d.id]
    val on = state?.first ?: false
    var brightness by remember(d.id, state?.second) { mutableFloatStateOf((state?.second ?: 100).toFloat()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TextButton(onRename, contentPadding = PaddingValues(0.dp)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        listOf(d.model, d.address).filter { it.isNotBlank() }.joinToString(" · ") +
                            if (state == null && d.brand != Brand.GOVEE) " · status unknown" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = on, onCheckedChange = { vm.toggle(d, it) })
            }
            Spacer(Modifier.padding(4.dp))
            Slider(
                value = brightness,
                onValueChange = { brightness = it },
                onValueChangeFinished = { vm.setBrightness(d, brightness.toInt()) },
                valueRange = 1f..100f,
            )
        }
    }
}
