package com.myra.assistant.features

import android.content.Context
import android.content.SharedPreferences
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SmartHomeController(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("smart_home", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()

    data class Device(
        val id: String,
        val name: String,
        val type: DeviceType,
        val ip: String,
        val apiKey: String = ""
    )

    enum class DeviceType {
        PHILIPS_HUE,
        TP_LINK_KASA,
        WLED,
        MI_HOME
    }

    fun addDevice(device: Device) {
        val devices = getDevices().toMutableList()
        devices.add(device)
        saveDevices(devices)
    }

    fun removeDevice(deviceId: String) {
        val devices = getDevices().toMutableList()
        devices.removeAll { it.id == deviceId }
        saveDevices(devices)
    }

    fun getDevices(): List<Device> {
        val json = prefs.getString("devices", "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Device(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    type = DeviceType.valueOf(obj.getString("type")),
                    ip = obj.getString("ip"),
                    apiKey = obj.optString("apiKey", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveDevices(devices: List<Device>) {
        val array = org.json.JSONArray()
        devices.forEach { device ->
            val obj = JSONObject().apply {
                put("id", device.id)
                put("name", device.name)
                put("type", device.type.name)
                put("ip", device.ip)
                put("apiKey", device.apiKey)
            }
            array.put(obj)
        }
        prefs.edit().putString("devices", array.toString()).apply()
    }

    fun turnOn(deviceName: String, callback: (String) -> Unit) {
        val device = getDevices().find { it.name.contains(deviceName, ignoreCase = true) }
        if (device == null) {
            callback("Device not found")
            return
        }

        when (device.type) {
            DeviceType.TP_LINK_KASA -> toggleKasaDevice(device, true, callback)
            DeviceType.PHILIPS_HUE -> toggleHueLight(device, true, callback)
            DeviceType.WLED -> toggleWled(device, true, callback)
            else -> callback("Device type not supported")
        }
    }

    fun turnOff(deviceName: String, callback: (String) -> Unit) {
        val device = getDevices().find { it.name.contains(deviceName, ignoreCase = true) }
        if (device == null) {
            callback("Device not found")
            return
        }

        when (device.type) {
            DeviceType.TP_LINK_KASA -> toggleKasaDevice(device, false, callback)
            DeviceType.PHILIPS_HUE -> toggleHueLight(device, false, callback)
            DeviceType.WLED -> toggleWled(device, false, callback)
            else -> callback("Device type not supported")
        }
    }

    private fun toggleKasaDevice(device: Device, on: Boolean, callback: (String) -> Unit) {
        val json = JSONObject().apply {
            put("method", "set_power_state")
            put("params", JSONObject().apply {
                put("state", if (on) "on" else "off")
            })
        }

        val request = Request.Builder()
            .url("http://${device.ip}/passthrough")
            .post(RequestBody.create(MediaType.parse("application/json"), json.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Failed to connect to device")
            }

            override fun onResponse(call: Call, response: Response) {
                callback(if (on) "Device turned on" else "Device turned off")
            }
        })
    }

    private fun toggleHueLight(device: Device, on: Boolean, callback: (String) -> Unit) {
        val json = JSONObject().apply {
            put("on", on)
        }

        val request = Request.Builder()
            .url("http://${device.ip}/api/${device.apiKey}/lights/1/state")
            .put(RequestBody.create(MediaType.parse("application/json"), json.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Failed to connect to Hue bridge")
            }

            override fun onResponse(call: Call, response: Response) {
                callback(if (on) "Hue light turned on" else "Hue light turned off")
            }
        })
    }

    private fun toggleWled(device: Device, on: Boolean, callback: (String) -> Unit) {
        val json = JSONObject().apply {
            put("on", on)
        }

        val request = Request.Builder()
            .url("http://${device.ip}/json/state")
            .post(RequestBody.create(MediaType.parse("application/json"), json.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Failed to connect to WLED device")
            }

            override fun onResponse(call: Call, response: Response) {
                callback(if (on) "WLED turned on" else "WLED turned off")
            }
        })
    }

    fun setColor(deviceName: String, color: String, callback: (String) -> Unit) {
        val device = getDevices().find { it.name.contains(deviceName, ignoreCase = true) }
        if (device == null) {
            callback("Device not found")
            return
        }

        when (device.type) {
            DeviceType.PHILIPS_HUE -> setHueColor(device, color, callback)
            DeviceType.WLED -> setWledColor(device, color, callback)
            else -> callback("Color control not supported for this device")
        }
    }

    private fun setHueColor(device: Device, color: String, callback: (String) -> Unit) {
        val hue = when (color.lowercase()) {
            "red" -> 0
            "green" -> 120
            "blue" -> 240
            "yellow" -> 60
            "purple" -> 280
            else -> 0
        }

        val json = JSONObject().apply {
            put("hue", hue)
            put("sat", 254)
            put("on", true)
        }

        val request = Request.Builder()
            .url("http://${device.ip}/api/${device.apiKey}/lights/1/state")
            .put(RequestBody.create(MediaType.parse("application/json"), json.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Failed to set color")
            }

            override fun onResponse(call: Call, response: Response) {
                callback("Color changed to $color")
            }
        })
    }

    private fun setWledColor(device: Device, color: String, callback: (String) -> Unit) {
        val rgb = when (color.lowercase()) {
            "red" -> intArrayOf(255, 0, 0)
            "green" -> intArrayOf(0, 255, 0)
            "blue" -> intArrayOf(0, 0, 255)
            "yellow" -> intArrayOf(255, 255, 0)
            "purple" -> intArrayOf(128, 0, 128)
            else -> intArrayOf(255, 255, 255)
        }

        val json = JSONObject().apply {
            put("on", true)
            put("primary", JSONObject().apply {
                put("r", rgb[0])
                put("g", rgb[1])
                put("b", rgb[2])
            })
        }

        val request = Request.Builder()
            .url("http://${device.ip}/json/state")
            .post(RequestBody.create(MediaType.parse("application/json"), json.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Failed to set color")
            }

            override fun onResponse(call: Call, response: Response) {
                callback("Color changed to $color")
            }
        })
    }
}