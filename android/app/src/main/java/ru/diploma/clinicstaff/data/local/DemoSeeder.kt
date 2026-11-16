package ru.diploma.clinicstaff.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.diploma.clinicstaff.data.model.*

private val Context.seedStore by preferencesDataStore("demo_seed")
class DemoSeeder(private val context: Context, private val dao: ClinicDao) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun seedIfNeeded() {
        val key = booleanPreferencesKey("seeded")
        if (context.seedStore.data.first()[key] == true) return
        val employees = read("demo/employees.json", ListSerializer(Employee.serializer()))
        val departments = read("demo/departments.json", ListSerializer(Department.serializer()))
        val schedules = read("demo/schedules.json", ListSerializer(Schedule.serializer()))
        val days = read("demo/day_offs.json", ListSerializer(DayOff.serializer()))
        val notifications = read("demo/notifications.json", ListSerializer(ClinicNotification.serializer()))
        val absences = read("demo/absences.json", ListSerializer(StaffAbsence.serializer()))
        val replacements = read("demo/replacements.json", ListSerializer(ShiftReplacement.serializer()))
        val tasks = read("demo/tasks.json", ListSerializer(ClinicTask.serializer()))
        val serviceRequests = read("demo/service_requests.json", ListSerializer(ServiceRequest.serializer()))
        dao.putEmployees(employees.map { EmployeeEntity(it.id,it.firstName,it.lastName,it.email,it.type,it.phone,it.specialist) })
        dao.putDepartments(departments.map { DepartmentEntity(it.id,it.name,it.description) })
        dao.putSchedules(schedules.map { ScheduleEntity(it.id,it.userId,it.dayOfWeek,it.startTime,it.endTime,it.date) })
        dao.putDayOffs(days.map { DayOffEntity(it.id,it.userId,it.date,it.reason) })
        dao.putNotifications(notifications.map { NotificationEntity(it.id,it.title,it.message,it.createdAt,it.readAt != null) })
        dao.putAbsences(absences.map { StaffAbsenceEntity(it.id,it.userId,it.date,it.endDate,it.type,it.reason,it.status) })
        dao.putReplacements(replacements.map { ShiftReplacementEntity(it.id,it.scheduleId,it.employeeId,it.replacementEmployeeId,it.date,it.startTime,it.endTime,it.note) })
        dao.putTasks(tasks.map { TaskEntity(it.id,it.title,it.description,it.assigneeId,it.dueDate,it.priority,it.status) })
        dao.putServiceRequests(serviceRequests.map { ServiceRequestEntity(it.id,it.title,it.category,it.description,it.location,it.status,it.createdAt) })
        context.seedStore.edit { it[key] = true }
    }
    suspend fun reset() {
        dao.clearEmployees(); dao.clearDepartments(); dao.clearSchedules(); dao.clearDayOffs(); dao.clearNotifications()
        dao.clearAbsences(); dao.clearReplacements(); dao.clearTasks(); dao.clearServiceRequests()
        context.seedStore.edit { it[booleanPreferencesKey("seeded")] = false }
        seedIfNeeded()
    }
    private suspend fun <T> read(path: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val source = context.assets.open(path).bufferedReader().use { it.readText() }
        return json.decodeFromString(serializer, source)
    }
}
