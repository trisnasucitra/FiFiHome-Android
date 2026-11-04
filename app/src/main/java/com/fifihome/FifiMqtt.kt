package id.fifihome.android

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class FifiMqtt {
    private var client: MqttClient? = null
    @Synchronized fun connect() {
        if (client?.isConnected == true) return
        val c = MqttClient("tcp://192.168.0.50:1883", MqttClient.generateClientId(), MemoryPersistence())
        val options = MqttConnectOptions().apply { isCleanSession = true; connectionTimeout = 10; keepAliveInterval = 30; isAutomaticReconnect = true }
        c.connect(options); client = c
        listOf("esp1/kebun/feedback", "esp1/sensorCahaya1", "esp1/sensorCahaya2", "esp1/Humidity", "esp1/senHujan", "esp1/senSuhu", "esp1/#", "esp1/+/status").forEach { c.subscribe(it, 0) }
    }
    @Synchronized fun publish(topic: String, payload: String) {
        val c = client ?: error("MQTT belum terhubung")
        if (!c.isConnected) error("MQTT terputus")
        c.publish(topic, MqttMessage(payload.toByteArray()).apply { qos = 0; isRetained = false })
    }
    @Synchronized fun disconnect() { runCatching { client?.disconnect() }; client = null }
    fun isConnected() = client?.isConnected == true
}
