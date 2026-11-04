package id.fifihome.android
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class FifiApi(private val baseUrl: String = "http://192.168.0.50/FiFiHome/") {
    private val cookies = object : CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { store[url.host] = cookies.toMutableList() }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
    }
    private val client = OkHttpClient.Builder().cookieJar(cookies).connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun login(username: String, password: String): Result<Unit> = runCatching {
        val form = "username=${enc(username)}&password=${enc(password)}&login_time=${enc(java.time.LocalDateTime.now().toString().replace('T',' '))}"
        val req = Request.Builder().url(baseUrl + "index.php").post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType())).build()
        val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
        if (body.contains("password", true) && (body.contains("salah", true) || body.contains("gagal", true) || body.contains("invalid", true))) error("Nama pengguna atau kata sandi tidak sesuai")
        if (cookies.loadForRequest(baseUrl.toHttpUrl()).isEmpty()) error("Sesi login tidak terdeteksi. Periksa respons index.php dan konfigurasi server.")
    }
    fun getJson(path: String): JSONObject {
        val req = Request.Builder().url(baseUrl + path).get().build()
        val text = client.newCall(req).execute().use { it.body?.string().orEmpty() }
        return JSONObject(text)
    }
    fun postJson(path: String, data: JSONObject): JSONObject {
        val req = Request.Builder().url(baseUrl + path).post(data.toString().toRequestBody(jsonType)).build()
        val text = client.newCall(req).execute().use { it.body?.string().orEmpty() }
        return JSONObject(text)
    }
    fun getSettings(zone: String) = getJson("api.php?zone=${enc(zone)}")
    fun saveSettings(zone: String, configs: JSONObject) = postJson("api.php", JSONObject().put("zone", zone).put("configs", configs))
    fun events(page: Int) = getJson("api.php?action=event_record&page=$page")
    fun serverTime() = getJson("api.php?action=server_time")
    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())
}
