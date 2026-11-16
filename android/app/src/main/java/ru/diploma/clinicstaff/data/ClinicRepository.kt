package ru.diploma.clinicstaff.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import ru.diploma.clinicstaff.data.local.*
import ru.diploma.clinicstaff.data.model.*
import ru.diploma.clinicstaff.data.remote.ApiService

class ClinicRepository(
    private val dao: ClinicDao,
    private val api: ApiService,
    private val settings: SettingsStore
) {
    suspend fun login(email: String, password: String): Employee {
        val result = api.login(LoginRequest(email, password)).data
        settings.setToken(result.token)
        return result.user
    }
    suspend fun logout() { runCatching { api.logout() }; settings.setToken(null) }
    suspend fun profile(): Employee? = if (settings.modeValue() == AppMode.SERVER) runCatching { api.me().data }.getOrNull() else null

    fun employees(search: String = "", type: String = ""): Flow<List<Employee>> =
        dao.searchEmployees(search, type).map { list -> list.map { Employee(it.id,it.firstName,it.lastName,it.email,it.type,it.phone,specialist=it.specialist) } }
    suspend fun refreshEmployees(search: String = "", type: String = "") {
        if (settings.modeValue() == AppMode.SERVER) {
            val data = api.employees(search.ifBlank { null }, type.ifBlank { null }).data
            dao.putEmployees(data.map { EmployeeEntity(it.id,it.firstName,it.lastName,it.email,it.type,it.phone,it.specialist) })
        }
    }
    fun departments(): Flow<List<DepartmentEntity>> = dao.departments()
    suspend fun refreshDepartments() {
        if (settings.modeValue() == AppMode.SERVER) dao.putDepartments(api.departments().data.map { DepartmentEntity(it.id,it.name,it.description) })
    }
    fun schedules(): Flow<List<ScheduleEntity>> = dao.schedules()
    suspend fun refreshSchedules() {
        if (settings.modeValue() == AppMode.SERVER) dao.putSchedules(api.schedules().data.map { ScheduleEntity(it.id,it.userId,it.dayOfWeek,it.startTime,it.endTime,it.date) })
    }
    fun dayOffs(): Flow<List<DayOffEntity>> = dao.dayOffs()
    suspend fun refreshDayOffs() {
        if (settings.modeValue() == AppMode.SERVER) dao.putDayOffs(api.dayOffs().data.map { DayOffEntity(it.id,it.userId,it.date,it.reason) })
    }
    fun notifications(): Flow<List<NotificationEntity>> = dao.notifications()
    fun absences(): Flow<List<StaffAbsenceEntity>> = dao.absences()
    suspend fun refreshAbsences() {
        if (settings.modeValue() == AppMode.SERVER) dao.putAbsences(api.absences().data.map { StaffAbsenceEntity(it.id,it.userId,it.date,it.endDate,it.type,it.reason,it.status) })
    }
    fun replacements(): Flow<List<ShiftReplacementEntity>> = dao.replacements()
    suspend fun refreshReplacements() {
        if (settings.modeValue() == AppMode.SERVER) dao.putReplacements(api.replacements().data.map { ShiftReplacementEntity(it.id,it.scheduleId,it.employeeId,it.replacementEmployeeId,it.date,it.startTime,it.endTime,it.note) })
    }
    suspend fun refreshNotifications() {
        if (settings.modeValue() == AppMode.SERVER) dao.putNotifications(api.notifications().data.map { NotificationEntity(it.id,it.title,it.message,it.createdAt,it.readAt != null) })
    }
    fun tasks(): Flow<List<TaskEntity>> = dao.tasks()
    suspend fun refreshTasks() {
        if (settings.modeValue() == AppMode.SERVER) {
            dao.putTasks(api.tasks().data.map { TaskEntity(it.id,it.title,it.description,it.assigneeId,it.dueDate,it.priority,it.status) })
        }
    }
    suspend fun createTask(title:String, description:String, assigneeId:Long?, dueDate:String?, priority:String) {
        if (settings.modeValue() == AppMode.SERVER) {
            val created=api.createTask(CreateTaskRequest(title,description,assigneeId,dueDate,priority)).data
            dao.putTasks(listOf(TaskEntity(created.id,created.title,created.description,created.assigneeId,created.dueDate,created.priority,created.status)))
        } else {
            val id=(dao.tasks().first().maxOfOrNull { it.id } ?: 0L)+1L
            dao.putTasks(listOf(TaskEntity(id,title,description,assigneeId,dueDate,priority,"new")))
        }
    }
    suspend fun updateTaskStatus(id: Long, status: String) {
        if (settings.modeValue() == AppMode.SERVER) {
            val updated = api.updateTaskStatus(id, mapOf("status" to status)).data
            dao.putTasks(listOf(TaskEntity(updated.id,updated.title,updated.description,updated.assigneeId,updated.dueDate,updated.priority,updated.status)))
        } else dao.updateTaskStatus(id,status)
    }
    fun serviceRequests(): Flow<List<ServiceRequestEntity>> = dao.serviceRequests()
    suspend fun refreshServiceRequests() {
        if (settings.modeValue() == AppMode.SERVER) {
            dao.putServiceRequests(api.serviceRequests().data.map { ServiceRequestEntity(it.id,it.title,it.category,it.description,it.location,it.status,it.createdAt) })
        }
    }
    suspend fun createServiceRequest(title: String, category: String, description: String, location: String) {
        if (settings.modeValue() == AppMode.SERVER) {
            val created = api.createServiceRequest(mapOf("title" to title,"category" to category,"description" to description,"location" to location)).data
            dao.putServiceRequests(listOf(ServiceRequestEntity(created.id,created.title,created.category,created.description,created.location,created.status,created.createdAt)))
        } else {
            val nextId = (dao.maxRequestId() ?: 0L) + 1L
            dao.putServiceRequests(listOf(ServiceRequestEntity(nextId,title,category,description,location,"new",java.time.LocalDate.now().toString())))
        }
    }

    suspend fun updateServiceRequestStatus(id:Long,status:String) {
        if(settings.modeValue()==AppMode.SERVER) {
            val updated=api.updateServiceRequestStatus(id,mapOf("status" to status)).data
            dao.putServiceRequests(listOf(ServiceRequestEntity(updated.id,updated.title,updated.category,updated.description,updated.location,updated.status,updated.createdAt)))
        } else dao.updateServiceRequestStatus(id,status)
    }

    suspend fun markNotificationRead(id: String) {
        if (settings.modeValue() == AppMode.SERVER) api.markRead(id)
        dao.markRead(id)
    }
    private suspend fun SettingsStore.modeValue() = mode.first()
}
