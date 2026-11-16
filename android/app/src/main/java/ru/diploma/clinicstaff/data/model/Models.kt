package ru.diploma.clinicstaff.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Employee(
    val id: Long,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String = "",
    val type: String = "employee",
    val phone: String? = null,
    val mobile: String? = null,
    val picture: String? = null,
    val specialist: String? = null,
    @SerialName("medical_degree") val medicalDegree: String? = null,
    val biography: String? = null,
    val departments: List<Department> = emptyList()
) {
    val fullName get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Сотрудник #$id" }
}

@Serializable
data class Department(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerialName("employees_count") val employeesCount: Int? = null
)

@Serializable
data class Schedule(
    val id: Long,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("week_day") val dayOfWeek: String = "",
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
    val date: String? = null
)

@Serializable
data class DayOff(
    val id: Long,
    @SerialName("user_id") val userId: Long? = null,
    val date: String = "",
    val reason: String? = null
)

@Serializable
data class ClinicNotification(
    val id: String,
    val title: String = "Уведомление",
    val message: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    val type: String? = null
)

@Serializable
data class Dashboard(
    @SerialName("employees_count") val employeesCount: Int = 0,
    @SerialName("departments_count") val departmentsCount: Int = 0,
    @SerialName("unread_notifications_count") val unreadNotificationsCount: Int = 0
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val user: Employee)

@Serializable
data class ApiList<T>(val data: List<T> = emptyList())


@Serializable
data class StaffAbsence(
    val id: Long,
    @SerialName("user_id") val userId: Long,
    val date: String,
    @SerialName("end_date") val endDate: String? = null,
    val type: String = "vacation",
    val reason: String? = null,
    val status: String = "approved"
)

@Serializable
data class ShiftReplacement(
    val id: Long,
    @SerialName("schedule_id") val scheduleId: Long? = null,
    @SerialName("employee_id") val employeeId: Long,
    @SerialName("replacement_employee_id") val replacementEmployeeId: Long,
    val date: String,
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
    val note: String? = null
)


@Serializable
data class ClinicTask(
    val id: Long, val title: String, val description: String = "",
    @SerialName("assignee_id") val assigneeId: Long? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val priority: String = "normal", val status: String = "new"
)

@Serializable
data class ServiceRequest(
    val id: Long, val title: String, val category: String,
    val description: String = "", val location: String = "",
    val status: String = "new", @SerialName("created_at") val createdAt: String? = null
)


@Serializable
data class ApiResponse<T>(val success: Boolean = true, val message: String = "OK", val data: T)


@Serializable
data class NotificationRead(val id: String, @SerialName("read_at") val readAt: String? = null)


@Serializable
data class CreateTaskRequest(
    val title:String, val description:String,
    @SerialName("assignee_id") val assigneeId:Long? = null,
    @SerialName("due_date") val dueDate:String? = null,
    val priority:String = "normal"
)
