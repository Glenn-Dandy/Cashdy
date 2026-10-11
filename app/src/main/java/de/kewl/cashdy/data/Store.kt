package de.kewl.cashdy.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Alle Daten liegen in einer JSON-Datei im privaten App-Speicher.
 *
 * Bei ein paar tausend Einträgen sind das wenige hundert KB; das liest und schreibt sich
 * schneller als eine Datenbank aufzusetzen, und das Backup ist dieselbe Datei. Geschrieben
 * wird atomar (temporäre Datei + Umbenennen), damit ein Absturz nichts halb zurücklässt.
 */
object Store {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private lateinit var file: File
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()
    private val listeners = mutableListOf<(AppData) -> Unit>()
    private val lock = Any()

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "cashdy.json")
        _data.value = load(file) ?: AppData()
    }

    /** Für Tests ohne Android. */
    fun initForTest(start: AppData = AppData()) {
        _data.value = start
    }

    fun addListener(l: (AppData) -> Unit) { listeners += l }

    val current: AppData get() = _data.value

    fun update(block: (AppData) -> AppData) {
        val next: AppData
        synchronized(lock) {
            next = block(_data.value)
            _data.value = next
            if (::file.isInitialized) save(file, next)
        }
        listeners.forEach { runCatching { it(next) } }
    }

    fun replaceAll(newData: AppData) = update { newData }

    fun encode(d: AppData = current): String = json.encodeToString(AppData.serializer(), d)
    fun decode(text: String): AppData = migrate(json.decodeFromString(AppData.serializer(), text))

    private fun load(f: File): AppData? {
        if (!f.exists()) return null
        return runCatching { decode(f.readText()) }.getOrNull()
            ?: runCatching { File(f.path + ".bak").takeIf { it.exists() }?.let { decode(it.readText()) } }.getOrNull()
    }

    private fun save(f: File, d: AppData) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(encode(d))
        if (f.exists()) f.copyTo(File(f.path + ".bak"), overwrite = true)
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /**
     * Neue Standard-Kategorien in alten Datenbeständen ergänzen. Den Start des Rücklage-Topfs
     * setzt nur das Onboarding: Bestehende Daten rechnen weiter so, als liefe der Topf schon
     * immer, sonst stünde nach dem Update plötzlich eine volle Jahresrechnung im Monat.
     */
    private fun migrate(d: AppData): AppData {
        val have = d.categories.map { it.id }.toSet()
        val missing = DefaultCategories.all.filter { it.id !in have }
        return if (missing.isEmpty()) d else d.copy(categories = d.categories + missing)
    }

    fun newId(): String = UUID.randomUUID().toString()
}

fun AppData.category(id: String?): Category? = categories.firstOrNull { it.id == id }
