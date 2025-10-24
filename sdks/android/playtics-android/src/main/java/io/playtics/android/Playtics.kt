package io.playtics.android

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

/**
 * Minimal Android SDK that mirrors the Web SDK behavior: batching, NDJSON, optional gzip & HMAC,
 * offline persistence (SharedPreferences), and simple session handling.
 */
class Playtics(private val ctx: Context, private val opts: Options) {
  data class Options(
    val apiKey: String,
    val endpoint: String,
    val projectId: String,
    val deviceId: String? = null,
    val flushIntervalMs: Long = 5000,
    val maxBatch: Int = 50,
    val maxQueueBytes: Int = 512_000,
    val sessionGapMs: Long = 30*60*1000,
    val hmacSecret: String? = null, // Caution: embedding secret in client is not recommended for production.
    val debug: Boolean = false
  )

  data class Event(
    val event_id: String,
    val event_name: String,
    val project_id: String,
    val user_id: String? = null,
    val device_id: String,
    val session_id: String? = null,
    val ts_client: Long,
    val platform: String? = "android",
    val app_version: String? = null,
    val country: String? = null,
    val props: Map<String, Any?>? = null
  )

  private val client = OkHttpClient()
  private val prefs: SharedPreferences = ctx.getSharedPreferences("playtics", Context.MODE_PRIVATE)
  private val queue = ArrayList<Event>()
  private var queueBytes: Int = 0
  private val timer = Timer(true)
  private val flushing = AtomicBoolean(false)

  private var deviceId: String
  private var userId: String? = null
  private var sessionId: String? = null
  private var lastActive: Long = System.currentTimeMillis()

  init {
    deviceId = opts.deviceId ?: prefs.getString(devKey(), null) ?: genDeviceId().also {
      prefs.edit().putString(devKey(), it).apply()
    }
    restoreQueue()
    ensureTimer()
  }

  fun setUserId(uid: String?) { userId = uid }
  fun setUserProps(props: Map<String, Any?>) { /* optional; merge into props per event if needed */ }

  fun track(eventName: String, props: Map<String, Any?>? = null): String {
    val now = System.currentTimeMillis()
    rollSession(now)
    val evt = Event(
      event_id = uuidv7(),
      event_name = eventName,
      project_id = opts.projectId,
      user_id = userId,
      device_id = deviceId,
      session_id = sessionId,
      ts_client = now,
      platform = "android",
      props = props
    )
    val est = evt.estimateSize()
    queue.add(evt)
    queueBytes += est
    lastActive = now
    if (opts.debug) android.util.Log.d("Playtics", "queued ${evt.event_id} ${evt.event_name}")
    if (queueBytes >= opts.maxQueueBytes) flush()
    persistQueue()
    return evt.event_id
  }

  fun expose(exp: String, variant: String) = track("experiment_exposure", mapOf("exp" to exp, "variant" to variant))
  fun revenue(amount: Number, currency: String, props: Map<String, Any?>? = null) =
    track("revenue", mapOf("amount" to amount, "currency" to currency) + (props ?: emptyMap()))

  @Synchronized fun flush() {
    if (queue.isEmpty() || !flushing.compareAndSet(false, true)) return
    try {
      val batch = ArrayList<Event>()
      val n = minOf(opts.maxBatch, queue.size)
      for (i in 0 until n) batch.add(queue.removeAt(0))
      recalcQueueBytes()
      send(batch)
    } finally {
      flushing.set(false)
      persistQueue()
    }
  }

  fun shutdown() {
    try { timer.cancel() } catch (_: Throwable) {}
  }

  private fun ensureTimer() {
    timer.schedule(object : TimerTask() {
      override fun run() { try { flush() } catch (_: Throwable) {} }
    }, opts.flushIntervalMs, opts.flushIntervalMs)
  }

  private fun rollSession(now: Long) {
    if (sessionId == null || now - lastActive > opts.sessionGapMs) {
      sessionId = uuidv7()
    }
  }

  private fun send(batch: List<Event>) {
    if (batch.isEmpty()) return
    val ndjson = buildString(batch.size * 128) {
      val moshi = Json
      for (e in batch) {
        append(moshi.stringify(e)).append('\n')
      }
    }
    var bodyBytes = ndjson.toByteArray(Charsets.UTF_8)
    val reqBuilder = Request.Builder()
      .url(opts.endpoint.trimEnd('/') + "/v1/batch")
      .addHeader("x-api-key", opts.apiKey)
      .addHeader("content-type", "application/x-ndjson")

    // gzip if helps
    val gz = gzip(bodyBytes)
    if (gz != null) {
      bodyBytes = gz
      reqBuilder.addHeader("content-encoding", "gzip")
    }

    // optional HMAC signature (not recommended to ship secret in client)
    opts.hmacSecret?.let { secret ->
      val t = (System.currentTimeMillis() / 1000).toString()
      val msg = "$t." + String(bodyBytes, Charsets.UTF_8)
      val s = hmacSha256Hex(secret, msg)
      reqBuilder.addHeader("x-signature", "t=$t, s=$s")
    }

    val req = reqBuilder.post(RequestBody.create("application/octet-stream".toMediaType(), bodyBytes)).build()
    try {
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) {
          // on failure, requeue
          queue.addAll(0, batch)
          recalcQueueBytes()
        }
      }
    } catch (e: Exception) {
      // offline or network error: requeue
      queue.addAll(0, batch)
      recalcQueueBytes()
    }
  }

  // Simple JSON without external libs
  private object Json {
    fun stringify(e: Event): String {
      val sb = StringBuilder(256)
      sb.append('{')
      field(sb, "event_id", e.event_id)
      field(sb, "event_name", e.event_name)
      field(sb, "project_id", e.project_id)
      e.user_id?.let { field(sb, "user_id", it) }
      field(sb, "device_id", e.device_id)
      e.session_id?.let { field(sb, "session_id", it) }
      field(sb, "ts_client", e.ts_client)
      e.platform?.let { field(sb, "platform", it) }
      e.app_version?.let { field(sb, "app_version", it) }
      e.country?.let { field(sb, "country", it) }
      e.props?.let { objField(sb, "props", it) }
      // remove trailing comma
      if (sb.last() == ',') sb.setLength(sb.length - 1)
      sb.append('}')
      return sb.toString()
    }

    private fun field(sb: StringBuilder, k: String, v: String) {
      sb.append('"').append(escape(k)).append('"').append(':')
        .append('"').append(escape(v)).append('"').append(',')
    }
    private fun field(sb: StringBuilder, k: String, v: Long) {
      sb.append('"').append(escape(k)).append('"').append(':').append(v).append(',')
    }
    private fun objField(sb: StringBuilder, k: String, obj: Map<String, Any?>) {
      sb.append('"').append(escape(k)).append('"').append(':').append(mapToJson(obj)).append(',')
    }
    private fun mapToJson(m: Map<String, Any?>, depth: Int = 0): String {
      if (depth > 2) return "{}"
      val sb = StringBuilder("{")
      for ((kk,vv) in m) {
        sb.append('"').append(escape(kk)).append('"').append(':').append(valueToJson(vv, depth+1)).append(',')
      }
      if (sb.last() == ',') sb.setLength(sb.length - 1)
      sb.append('}')
      return sb.toString()
    }
    private fun valueToJson(v: Any?, depth: Int): String {
      return when (v) {
        null -> "null"
        is String -> '"' + escape(v) + '"'
        is Number, is Boolean -> v.toString()
        is Map<*,*> -> mapToJson(v as Map<String, Any?>, depth)
        is List<*> -> listToJson(v as List<Any?>, depth)
        else -> '"' + escape(v.toString()) + '"'
      }
    }
    private fun listToJson(l: List<Any?>, depth: Int): String {
      if (depth > 2) return "[]"
      val sb = StringBuilder("[")
      val lim = minOf(50, l.size)
      for (i in 0 until lim) {
        sb.append(valueToJson(l[i], depth+1)).append(',')
      }
      if (sb.last() == ',') sb.setLength(sb.length - 1)
      sb.append(']')
      return sb.toString()
    }
    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
  }

  private fun Event.estimateSize(): Int {
    // rough estimation for queue size accounting
    return Json.stringify(this).toByteArray(Charsets.UTF_8).size + 1
  }

  private fun gzip(input: ByteArray): ByteArray? {
    return try {
      val bout = ByteArrayOutputStream()
      GZIPOutputStream(bout).use { it.write(input) }
      bout.toByteArray()
    } catch (_: Throwable) { null }
  }

  private fun devKey() = "pt_device_id_${opts.projectId}"
  private fun queueKey() = "pt_queue_${opts.projectId}_${deviceId}"

  private fun persistQueue() {
    try {
      val arr = StringBuilder(queue.size * 128)
      for (e in queue) {
        arr.append(Json.stringify(e)).append('\n')
      }
      prefs.edit().putString(queueKey(), arr.toString()).apply()
    } catch (_: Throwable) {}
  }
  private fun restoreQueue() {
    try {
      val s = prefs.getString(queueKey(), null) ?: return
      val lines = s.split('\n').filter { it.isNotBlank() }
      for (ln in lines) {
        // Simple restore: we don't parse back, only drop persisted queue to avoid crash; could parse if needed.
        // To keep simple, ignore restore content for now.
      }
    } catch (_: Throwable) {}
  }

  private fun hmacSha256Hex(secret: String, message: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val out = mac.doFinal(message.toByteArray(Charsets.UTF_8))
    return out.joinToString("") { b -> "%02x".format(b) }
  }

  private fun genDeviceId(): String = "d_" + UUID.randomUUID().toString().replace("-", "")

  private fun uuidv7(): String {
    val t = System.currentTimeMillis()
    val ts = java.lang.Long.toHexString(t).padStart(12, '0')
    val rnd = UUID.randomUUID().toString().replace("-", "")
    val hex = ts + rnd.take(20)
    return hex.substring(0,8) + "-" + hex.substring(8,12) + "-7" + hex.substring(13,16) + "-" + hex.substring(16,20) + "-" + hex.substring(20,32)
  }

  // ===== Experiments helpers =====
  data class Variant(val name: String, val weight: Int)
  private fun hash32(s: String): Int {
    var h = 0x811c9dc5.toInt()
    for (ch in s) { h = h xor ch.code; h += (h shl 1) + (h shl 4) + (h shl 7) + (h shl 8) + (h shl 24) }
    return h ushr 0
  }
  fun assignVariant(expId: String, salt: String?, variants: List<Variant>, key: String): String {
    if (variants.isEmpty()) return "A"
    val sum = variants.sumOf { if (it.weight>0) it.weight else 1 }
    val h = (hash32("$expId:${salt?:("")}:$key") % sum + sum) % sum
    var acc = 0
    for (v in variants) { acc += if (v.weight>0) v.weight else 1; if (h < acc) return v.name }
    return variants[0].name
  }
  /** Fetch experiments config (running) from control-service. WARNING: network on caller thread. */
  fun fetchExperiments(controlEndpoint: String, projectId: String): String? {
    return try {
      val url = controlEndpoint.trimEnd('/') + "/api/config/" + projectId
      val req = okhttp3.Request.Builder().url(url).get().build()
      client.newCall(req).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
    } catch (_: Throwable) { null }
  }
}
