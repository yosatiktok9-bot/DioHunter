package com.paneer.diohunter

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

object CaptureStore {
    private val records = CopyOnWriteArrayList<HttpRecord>()
    private val _flow = MutableStateFlow<List<HttpRecord>>(emptyList())
    val flow: StateFlow<List<HttpRecord>> = _flow.asStateFlow()

    @Volatile var targetPackage: String? = null
    @Volatile var capturing: Boolean = false

    private lateinit var appCtx: Context
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    fun add(record: HttpRecord) {
        records.add(0, record)
        if (records.size > 2000) {
            while (records.size > 1500) records.removeAt(records.lastIndex)
        }
        _flow.value = records.toList()
        persistAsync()
    }

    fun clear() {
        records.clear()
        _flow.value = emptyList()
        persistAsync()
    }

    fun all(): List<HttpRecord> = records.toList()

    fun filtered(q: String): List<HttpRecord> =
        if (q.isBlank()) all() else all().filter { it.matches(q) }

    fun exportJson(): String = gson.toJson(all())

    fun exportFile(): File {
        val dir = File(appCtx.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val f = File(dir, "diohunter_${System.currentTimeMillis()}.json")
        f.writeText(exportJson())
        return f
    }

    private fun persistAsync() {
        try {
            val f = File(appCtx.filesDir, "captures.json")
            f.writeText(gson.toJson(all()))
        } catch (_: Exception) {
        }
    }

    fun loadPersisted() {
        try {
            val f = File(appCtx.filesDir, "captures.json")
            if (!f.exists()) return
            val arr = gson.fromJson(f.readText(), Array<HttpRecord>::class.java) ?: return
            records.clear()
            records.addAll(arr.toList())
            _flow.value = records.toList()
        } catch (_: Exception) {
        }
    }
}
