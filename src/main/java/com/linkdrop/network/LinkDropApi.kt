package com.linkdrop.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.linkdrop.prefs.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest

// ── Data classes ─────────────────────────────────────────────────────────────

data class PingResponse(
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: String,
    @SerializedName("name") val name: String,
)

data class FileItem(
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("is_dir") val isDir: Boolean,
    @SerializedName("modified") val modified: Double,
)

data class FilesResponse(@SerializedName("files") val files: List<FileItem>)

// ── API Client ────────────────────────────────────────────────────────────────

class LinkDropApi(private val prefs: Prefs) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60,    java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60,   java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private fun authHeader(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(prefs.password.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .addHeader("X-LinkDrop-Auth", authHeader())

    private fun baseUrl(): String {
        val url = prefs.normalizedUrl()
        if (url.isEmpty()) throw Exception("Server address not configured")
        return url
    }

    // ── Ping ─────────────────────────────────────────────────────────────────
    suspend fun ping(): PingResponse = withContext(Dispatchers.IO) {
        val req  = baseRequest("${baseUrl()}/ping").get().build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            if (resp.code == 401 || resp.code == 403) throw Exception("Incorrect password")
            throw Exception("HTTP ${resp.code}")
        }
        val body = resp.body?.string() ?: throw Exception("Empty response")
        gson.fromJson(body, PingResponse::class.java)
    }

    // ── File list ─────────────────────────────────────────────────────────────
    suspend fun listFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val req  = baseRequest("${baseUrl()}/files").get().build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        val body = resp.body?.string() ?: "[]"
        gson.fromJson(body, FilesResponse::class.java).files
    }

    // ── Download file ─────────────────────────────────────────────────────────
    suspend fun downloadFile(name: String, outputStream: java.io.OutputStream,
                             onProgress: (Int) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val enc  = java.net.URLEncoder.encode(name, "UTF-8")
            val req  = baseRequest("${baseUrl()}/files/$enc").get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty body")
            val total = body.contentLength()
            var received = 0L
            body.byteStream().use { input ->
                outputStream.use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        received += n
                        if (total > 0) onProgress((received * 100 / total).toInt())
                    }
                }
            }
        }

    suspend fun downloadFile(name: String, destFile: File,
                             onProgress: (Int) -> Unit = {}): File {
        downloadFile(name, destFile.outputStream(), onProgress)
        return destFile
    }

    // ── Upload file ───────────────────────────────────────────────────────────
    suspend fun uploadFile(file: File, onProgress: (Int) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val body = file.asRequestBody("application/octet-stream".toMediaType())
            val req  = baseRequest("${baseUrl()}/upload")
                .post(body)
                .addHeader("X-Filename", file.name)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("Upload failed: HTTP ${resp.code}")
        }

    // ── Send text ─────────────────────────────────────────────────────────────
    suspend fun sendText(text: String) = withContext(Dispatchers.IO) {
        val payload = gson.toJson(mapOf("text" to text))
        val body    = payload.toRequestBody("application/json".toMediaType())
        val req     = baseRequest("${baseUrl()}/text").post(body).build()
        val resp    = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
    }

    // ── Get clipboard from PC ──────────────────────────────────────────────────
    suspend fun getClipboard(): String = withContext(Dispatchers.IO) {
        val req  = baseRequest("${baseUrl()}/clipboard").get().build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        val body = resp.body?.string() ?: "{}"
        val map  = gson.fromJson(body, Map::class.java)
        (map["text"] as? String) ?: ""
    }

    // ── Push clipboard to PC ──────────────────────────────────────────────────
    suspend fun pushClipboard(text: String) = withContext(Dispatchers.IO) {
        val payload = gson.toJson(mapOf("text" to text))
        val body    = payload.toRequestBody("application/json".toMediaType())
        val req     = baseRequest("${baseUrl()}/clipboard").post(body).build()
        val resp    = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
    }

    // ── Send notification to PC ───────────────────────────────────────────────
    suspend fun sendNotification(title: String, message: String) =
        withContext(Dispatchers.IO) {
            val payload = gson.toJson(mapOf("title" to title, "message" to message))
            val body    = payload.toRequestBody("application/json".toMediaType())
            val req     = baseRequest("${baseUrl()}/notify").post(body).build()
            val resp    = client.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        }

    // ── Delete file ───────────────────────────────────────────────────────────
    suspend fun deleteFile(name: String) = withContext(Dispatchers.IO) {
        val enc  = java.net.URLEncoder.encode(name, "UTF-8")
        val req  = baseRequest("${baseUrl()}/files/$enc").delete().build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
    }
}
