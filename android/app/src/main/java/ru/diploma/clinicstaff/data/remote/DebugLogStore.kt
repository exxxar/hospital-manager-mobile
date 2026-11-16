package ru.diploma.clinicstaff.data.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DebugEntry(val time:String,val level:String,val tag:String,val message:String) {
    fun line() = "$time [$level] $tag: $message"
}

object DebugLogStore {
    private val _entries = MutableStateFlow<List<DebugEntry>>(emptyList())
    val entries: StateFlow<List<DebugEntry>> = _entries
    @Volatile private var prefs: android.content.SharedPreferences? = null
    private val lock = Any()

    fun init(context:Context) {
        prefs = context.applicationContext.getSharedPreferences("clinic_debug_logs",Context.MODE_PRIVATE)
        val stored = prefs?.getString("entries","").orEmpty()
        if (stored.isNotBlank()) {
            _entries.value = stored.split("\n").takeLast(300).mapNotNull { line ->
                val p=line.split("|",limit=4)
                if(p.size==4) DebugEntry(p[0],p[1],p[2],p[3]) else null
            }
        }
        log("INFO","APP","Журнал отладки запущен")
    }

    fun log(level:String,tag:String,message:String) {
        val time=SimpleDateFormat("HH:mm:ss.SSS",Locale.getDefault()).format(Date())
        val safe=message.replace("\n"," ").replace("|","/")
        synchronized(lock) {
            val next=(_entries.value + DebugEntry(time,level,tag,safe)).takeLast(300)
            _entries.value=next
            prefs?.edit()?.putString("entries",next.joinToString("\n"){"${it.time}|${it.level}|${it.tag}|${it.message}"})?.apply()
        }
    }

    fun clear() { _entries.value=emptyList(); prefs?.edit()?.remove("entries")?.apply(); log("INFO","DEBUG","Журнал очищен") }
    fun exportText():String = _entries.value.joinToString("\n"){it.line()}
}
