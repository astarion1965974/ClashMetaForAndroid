package com.github.kr328.clash.service

import android.content.Context
import android.util.AtomicFile
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.util.trafficDownloadBytes
import com.github.kr328.clash.core.util.trafficUploadBytes
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** The service process owns both sampling and persistence; the UI reads through Binder. */
object TrafficHistory {
    private var previousUpload = 0L
    private var previousDownload = 0L
    private var sampling = false
    private var data: JSONObject? = null

    private fun file(context: Context) = AtomicFile(context.filesDir.resolve("traffic-history.json"))
    private fun date(calendar: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

    private fun load(context: Context): JSONObject {
        data?.let { return it }
        return (runCatching { JSONObject(file(context).readFully().toString(Charsets.UTF_8)) }
            .getOrElse { JSONObject() }).also { data = it }
    }

    @Synchronized
    fun start(context: Context) {
        previousUpload = 0L
        previousDownload = 0L
        sampling = true
        sample(context)
    }

    @Synchronized
    fun sample(context: Context) {
        if (!sampling) return
        val total = Clash.queryTrafficTotal()
        val upload = total.trafficUploadBytes()
        val download = total.trafficDownloadBytes()
        // A core reload can reset its counters independently of the service.
        val deltaUp = if (upload >= previousUpload) upload - previousUpload else upload
        val deltaDown = if (download >= previousDownload) download - previousDownload else download
        previousUpload = upload
        previousDownload = download
        val history = load(context)
        val today = date(Calendar.getInstance())
        val entry = history.optJSONObject(today) ?: JSONObject()
        entry.put("upload", entry.optLong("upload") + deltaUp)
        entry.put("download", entry.optLong("download") + deltaDown)
        history.put(today, entry)
        val oldest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        val cutoff = date(oldest)
        val keys = history.keys().asSequence().toList()
        keys.filter { it < cutoff }.forEach(history::remove)
        val atomic = file(context)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(history.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (_: Exception) {
            stream?.let(atomic::failWrite)
        }
    }

    @Synchronized
    fun stop(context: Context) {
        sample(context)
        sampling = false
    }

    @Synchronized
    fun snapshot(context: Context): String {
        val history = load(context)
        val result = JSONArray()
        val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        repeat(7) {
            val key = date(day)
            val entry = history.optJSONObject(key)
            result.put(JSONObject().put("date", key).put("recorded", entry != null)
                .put("upload", entry?.optLong("upload") ?: 0L)
                .put("download", entry?.optLong("download") ?: 0L))
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result.toString()
    }
}
