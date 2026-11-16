package ru.diploma.clinicstaff.data.remote

import retrofit2.http.*
import ru.diploma.clinicstaff.data.model.*

interface ApiService {
    @GET("health") suspend fun health(): retrofit2.Response<okhttp3.ResponseBody>
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): ApiResponse<LoginResponse>
    @POST("auth/logout") suspend fun logout(): ApiResponse<String?>
    @GET("auth/me") suspend fun me(): ApiResponse<Employee>
    @GET("dashboard") suspend fun dashboard(): ApiResponse<Dashboard>
    @GET("employees") suspend fun employees(@Query("search") search: String? = null, @Query("type") type: String? = null): ApiResponse<List<Employee>>
    @GET("employees/{id}") suspend fun employee(@Path("id") id: Long): ApiResponse<Employee>
    @GET("departments") suspend fun departments(): ApiResponse<List<Department>>
    @GET("schedules") suspend fun schedules(): ApiResponse<List<Schedule>>
    @GET("day-offs") suspend fun dayOffs(): ApiResponse<List<DayOff>>
    @GET("absences") suspend fun absences(): ApiResponse<List<StaffAbsence>>
    @GET("shift-replacements") suspend fun replacements(): ApiResponse<List<ShiftReplacement>>
    @GET("notifications") suspend fun notifications(): ApiResponse<List<ClinicNotification>>
    @PATCH("notifications/{id}/read") suspend fun markRead(@Path("id") id: String): ApiResponse<NotificationRead>
    @GET("tasks") suspend fun tasks(): ApiResponse<List<ClinicTask>>
    @POST("tasks") suspend fun createTask(@Body body: CreateTaskRequest): ApiResponse<ClinicTask>
    @PATCH("tasks/{id}/status") suspend fun updateTaskStatus(@Path("id") id: Long, @Body body: Map<String, String>): ApiResponse<ClinicTask>
    @GET("service-requests") suspend fun serviceRequests(): ApiResponse<List<ServiceRequest>>
    @POST("service-requests") suspend fun createServiceRequest(@Body body: Map<String, String>): ApiResponse<ServiceRequest>
    @PATCH("service-requests/{id}/status") suspend fun updateServiceRequestStatus(@Path("id") id: Long, @Body body: Map<String,String>): ApiResponse<ServiceRequest>
}
