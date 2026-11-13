package app.homecontrol.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.homecontrol.automation.Scheduler

@Composable
fun SetupScreen(vm: HomeViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val s = vm.settings
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.headlineMedium)
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        // ---- Reliability ----
        if (!Scheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Section("Allow on-time automations") {
                Text("Android needs the \"Alarms & reminders\" permission so your morning lights fire on the minute.")
                Button({
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Allow") }
            }
        }
        Section("Battery") {
            Text("For reliable schedules, set Home Control's battery usage to \"Unrestricted\".")
            OutlinedButton({
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }) { Text("Open app settings") }
        }

        // ---- Hue ----
        Section("Philips Hue") {
            var ip by remember(s.hueBridgeIp) { mutableStateOf(s.hueBridgeIp) }
            OutlinedTextField(
                ip, { ip = it; vm.saveSettings(vm.settings.copy(hueBridgeIp = it.trim())) },
                label = { Text("Bridge IP") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.findHueBridge() }) { Text("Find bridge") }
                Button({ vm.pairHue() }) { Text("Pair") }
            }
            Text(
                if (s.hueUsername.isEmpty()) "Press the round button on the bridge, then tap Pair within 30 seconds."
                else "Paired ✓",
                style = MaterialTheme.typography.bodySmall,
            )
            if (s.hueUsername.isNotEmpty()) OutlinedButton({ vm.importHueLights() }) { Text("Re-import lights") }
        }

        // ---- Tapo ----
        Section("Tapo") {
            var email by remember(s.tapoEmail) { mutableStateOf(s.tapoEmail) }
            var password by remember(s.tapoPassword) { mutableStateOf(s.tapoPassword) }
            var ip by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }
            Text(
                "Uses your Tapo account login, checked by the device itself on your Wi-Fi. " +
                    "In the Tapo app, turn on Me > Third-Party Services > Third-Party Compatibility.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(email, { email = it }, label = { Text("Tapo email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, label = { Text("Tapo password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button({ vm.saveSettings(vm.settings.copy(tapoEmail = email.trim(), tapoPassword = password)) }) { Text("Save login") }
            Text("Add a device: find its IP in the Tapo app > device > settings > Device info.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(ip, { ip = it }, label = { Text("Device IP") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
            Button({ vm.addTapo(ip, name); ip = ""; name = "" }, enabled = ip.isNotBlank()) { Text("Add Tapo device") }
        }

        // ---- Govee ----
        Section("Govee") {
            Text(
                "In the Govee Home app, open each device > settings and switch on \"LAN Control\". Then scan.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button({ vm.scanGovee() }) { Text("Scan for Govee devices") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
