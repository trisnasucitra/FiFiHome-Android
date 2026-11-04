package id.fifihome.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { FiFiHomeApp() } }
}

@Composable
private fun FiFiHomeApp() {
    val api = remember { FifiApi() }
    val mqtt = remember { FifiMqtt() }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Hubungkan ke jaringan Wi-Fi yang sama dengan server FiFiHome.") }
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Beranda") }
    var serverTime by remember { mutableStateOf("Belum tersedia") }
    var eventRows by remember { mutableStateOf(listOf<String>()) }
    var eventPage by remember { mutableIntStateOf(1) }
    var settingsZone by remember { mutableStateOf("kamar") }
    var settingsText by remember { mutableStateOf("") }
    var mqttStatus by remember { mutableStateOf("Belum terhubung") }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087F6A), secondary = androidx.compose.ui.graphics.Color(0xFF4B8176))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!loggedIn) {
                Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(48.dp)); Text("FiFiHome", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Sistem kendali rumah dan taman")
                    OutlinedTextField(username, { username = it }, label = { Text("Nama pengguna") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Kata sandi") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        busy = true; message = "Sedang masuk..."
                        scope.launch { val result = withContext(Dispatchers.IO) { api.login(username, password) }; busy = false; result.onSuccess { loggedIn = true; message = "Login berhasil" }.onFailure { message = it.message ?: "Login gagal" } }
                    }, enabled = !busy && username.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Memproses..." else "Masuk") }
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    Text("Server: 192.168.0.50/FiFiHome/", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("FiFiHome", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Waktu server: $serverTime", style = MaterialTheme.typography.bodyMedium)
                        Text("MQTT: $mqttStatus", style = MaterialTheme.typography.labelMedium)
                    }
                    ScrollableTabRow(selectedTabIndex = listOf("Beranda", "Kamar", "Taman", "Sensor", "Riwayat").indexOf(tab).coerceAtLeast(0)) {
                        listOf("Beranda", "Kamar", "Taman", "Sensor", "Riwayat").forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t) }) }
                    }
                    when (tab) {
                        "Beranda" -> HomePanel(serverTime, mqttStatus, message,
                            onRefresh = { busy = true; scope.launch { val result = withContext(Dispatchers.IO) { runCatching { api.serverTime() } }; result.onSuccess { serverTime = it.optString("time", it.optString("server_time", "Endpoint waktu server belum dipasang")) }.onFailure { serverTime = "Endpoint waktu server belum tersedia" }; busy = false } },
                            onMqtt = { busy = true; scope.launch { val result = withContext(Dispatchers.IO) { runCatching { mqtt.connect() } }; result.onSuccess { mqttStatus = "Terhubung"; message = "MQTT berhasil terhubung" }.onFailure { mqttStatus = "Gagal terhubung"; message = it.message ?: "Koneksi MQTT gagal" }; busy = false } },
                            onSync = { send(mqtt, "esp1/kamar/kontrol", "4a", scope) { message = it }; send(mqtt, "esp1/taman/kontrol", "4b", scope) { message = it }; send(mqtt, "esp1/kebun/kontrol", "4c", scope) { message = it } })
                        "Kamar" -> RoomPanel(mqtt, scope) { message = it }
                        "Taman" -> GardenPanel(mqtt, scope) { message = it }
                        "Sensor" -> SensorPanel(mqtt, scope) { message = it }
                        "Riwayat" -> EventsPanel(eventRows, eventPage, onLoad = { page -> scope.launch { val r = withContext(Dispatchers.IO) { runCatching { api.events(page) } }; r.onSuccess { obj -> val a = obj.optJSONArray("data"); eventRows = (0 until (a?.length() ?: 0)).map { i -> a!!.getJSONObject(i).let { "No. ${it.optString("no")} • ${it.optString("namaClient")} • ${it.optString("status")} • ${it.optString("waktu")}" } }; eventPage = page }.onFailure { message = it.message ?: "Gagal memuat riwayat" } } })
                    }
                    if (message.isNotBlank()) Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable private fun HomePanel(serverTime: String, mqttStatus: String, message: String, onRefresh: () -> Unit, onMqtt: () -> Unit, onSync: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ringkasan sistem", style = MaterialTheme.typography.titleLarge)
        InfoCard("Waktu server", serverTime)
        InfoCard("Koneksi MQTT", mqttStatus)
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Perbarui waktu server") }
        Button(onClick = onMqtt, modifier = Modifier.fillMaxWidth()) { Text("Hubungkan MQTT") }
        OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) { Text("Sinkronisasi perangkat") }
        Text("Catatan: jadwal AUTO tetap mengikuti jam komputer server/dashboard. Aplikasi ini tidak menjalankan jadwal secara lokal.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun RoomPanel(mqtt: FifiMqtt, scope: kotlinx.coroutines.CoroutineScope, report: (String) -> Unit) {
    var pwm by remember { mutableFloatStateOf(60f) }
    var mode by remember { mutableStateOf("AUTO") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Kontrol Lampu Kamar", style = MaterialTheme.typography.titleLarge)
        Text("Mode: $mode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("AUTO", "MANUAL", "MAINTENANCE").forEach { OutlinedButton(onClick = { mode = it; send(mqtt, "home/Lampu Kamar/mode", it, scope, report) }) { Text(it) } } }
        Text("Kecerahan: ${pwm.toInt()}%")
        Slider(value = pwm, onValueChange = { pwm = it }, valueRange = 0f..100f)
        Button(onClick = { send(mqtt, "home/Lampu Kamar/power", "ON", scope, report); send(mqtt, "home/Lampu Kamar/pwm", pwm.toInt().toString(), scope, report) }, modifier = Modifier.fillMaxWidth()) { Text("Lampu menyala") }
        OutlinedButton(onClick = { send(mqtt, "home/Lampu Kamar/power", "OFF", scope, report); send(mqtt, "home/Lampu Kamar/pwm", "0", scope, report) }, modifier = Modifier.fillMaxWidth()) { Text("Lampu mati") }
        Text("Pengaturan jadwal Kamar dan Taman dapat disunting pada tab Pengaturan setelah antarmuka API waktu server disiapkan.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun GardenPanel(mqtt: FifiMqtt, scope: kotlinx.coroutines.CoroutineScope, report: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Kontrol Taman", style = MaterialTheme.typography.titleLarge)
        Text("Penyiraman dan pengkabutan")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { send(mqtt, "esp1/taman/kontrol", "3a2", scope, report) }, modifier = Modifier.weight(1f)) { Text("Penyiraman ON") }; OutlinedButton(onClick = { send(mqtt, "esp1/taman/kontrol", "3a21", scope, report) }, modifier = Modifier.weight(1f)) { Text("Penyiraman OFF") } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { send(mqtt, "esp1/taman/kontrol", "3a3", scope, report) }, modifier = Modifier.weight(1f)) { Text("Kabut ON") }; OutlinedButton(onClick = { send(mqtt, "esp1/taman/kontrol", "3a31", scope, report) }, modifier = Modifier.weight(1f)) { Text("Kabut OFF") } }
        Divider()
        Text("Perawatan perangkat")
        listOf(Triple("Relay air", "3b1", "3b1a"), Triple("Pompa", "3b2", "3b2a"), Triple("Relay taman", "3b3", "3b3a"), Triple("Relay kabut", "3b4", "3b4a")).forEach { (label, on, off) ->
            Text(label, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { send(mqtt, "esp1/taman/kontrol", on, scope, report) }, modifier = Modifier.weight(1f)) { Text("NYALA") }; OutlinedButton(onClick = { send(mqtt, "esp1/taman/kontrol", off, scope, report) }, modifier = Modifier.weight(1f)) { Text("MATI") } }
        }
    }
}

@Composable private fun SensorPanel(mqtt: FifiMqtt, scope: kotlinx.coroutines.CoroutineScope, report: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pemantauan sensor", style = MaterialTheme.typography.titleLarge)
        Text("Nilai sensor akan tampil setelah pembacaan MQTT diintegrasikan ke tampilan.")
        Button(onClick = { send(mqtt, "esp1/sensor", "6a", scope, report) }, modifier = Modifier.fillMaxWidth()) { Text("Mulai sinkronisasi sensor") }
        OutlinedButton(onClick = { send(mqtt, "esp1/sensor", "6a1", scope, report) }, modifier = Modifier.fillMaxWidth()) { Text("Hentikan sinkronisasi sensor") }
        listOf("Cahaya luar", "Cahaya dalam", "Kelembapan", "Suhu", "Sensor hujan").forEach { InfoCard(it, "Menunggu data MQTT") }
    }
}

@Composable private fun EventsPanel(rows: List<String>, page: Int, onLoad: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Riwayat aktivitas", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { onLoad((page - 1).coerceAtLeast(1)) }) { Text("Sebelumnya") }; Button(onClick = { onLoad(page + 1) }) { Text("Berikutnya") }; Text("Halaman $page") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(rows) { InfoCard("Catatan", it) } }
        if (rows.isEmpty()) Text("Tekan Berikutnya atau muat ulang untuk mengambil data riwayat.")
    }
}

@Composable private fun InfoCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(value, style = MaterialTheme.typography.bodyMedium) } }
}

private fun send(mqtt: FifiMqtt, topic: String, payload: String, scope: kotlinx.coroutines.CoroutineScope, report: (String) -> Unit) {
    scope.launch { val r = withContext(Dispatchers.IO) { runCatching { mqtt.publish(topic, payload) } }; r.onSuccess { report("Perintah terkirim: $topic → $payload") }.onFailure { report(it.message ?: "Gagal mengirim perintah MQTT") } }
}
