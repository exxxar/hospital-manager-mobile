package ru.diploma.clinicstaff.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val type: String,
    val phone: String?,
    val specialist: String?
)

@Entity(tableName = "departments")
data class DepartmentEntity(@PrimaryKey val id: Long, val name: String, val description: String?)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: Long, val userId: Long?, val dayOfWeek: String,
    val startTime: String, val endTime: String, val date: String?
)

@Entity(tableName = "day_offs")
data class DayOffEntity(@PrimaryKey val id: Long, val userId: Long?, val date: String, val reason: String?)

@Entity(tableName = "staff_absences")
data class StaffAbsenceEntity(
    @PrimaryKey val id: Long, val userId: Long, val date: String, val endDate: String?,
    val type: String, val reason: String?, val status: String
)

@Entity(tableName = "shift_replacements")
data class ShiftReplacementEntity(
    @PrimaryKey val id: Long, val scheduleId: Long?, val employeeId: Long,
    val replacementEmployeeId: Long, val date: String, val startTime: String,
    val endTime: String, val note: String?
)

@Entity(tableName = "clinic_tasks")
data class TaskEntity(
    @PrimaryKey val id: Long, val title: String, val description: String,
    val assigneeId: Long?, val dueDate: String?, val priority: String, val status: String
)

@Entity(tableName = "service_requests")
data class ServiceRequestEntity(
    @PrimaryKey val id: Long, val title: String, val category: String,
    val description: String, val location: String, val status: String, val createdAt: String?
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String, val title: String, val message: String,
    val createdAt: String?, val isRead: Boolean
)

@Dao
interface ClinicDao {
    @Query("SELECT * FROM employees ORDER BY lastName, firstName")
    fun employees(): Flow<List<EmployeeEntity>>
    @Query("SELECT * FROM employees WHERE (:query = '' OR firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%') AND (:type = '' OR type = :type) ORDER BY lastName, firstName")
    fun searchEmployees(query: String, type: String): Flow<List<EmployeeEntity>>
    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun employee(id: Long): EmployeeEntity?
    @Query("SELECT * FROM departments ORDER BY name")
    fun departments(): Flow<List<DepartmentEntity>>
    @Query("SELECT * FROM schedules ORDER BY date, dayOfWeek")
    fun schedules(): Flow<List<ScheduleEntity>>
    @Query("SELECT * FROM day_offs ORDER BY date")
    fun dayOffs(): Flow<List<DayOffEntity>>
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun notifications(): Flow<List<NotificationEntity>>
    @Query("SELECT * FROM clinic_tasks ORDER BY dueDate")
    fun tasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM service_requests ORDER BY id DESC")
    fun serviceRequests(): Flow<List<ServiceRequestEntity>>
    @Query("SELECT * FROM staff_absences ORDER BY date")
    fun absences(): Flow<List<StaffAbsenceEntity>>
    @Query("SELECT * FROM shift_replacements ORDER BY date")
    fun replacements(): Flow<List<ShiftReplacementEntity>>
    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEmployees(items: List<EmployeeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDepartments(items: List<DepartmentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSchedules(items: List<ScheduleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDayOffs(items: List<DayOffEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putNotifications(items: List<NotificationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTasks(items: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putServiceRequests(items: List<ServiceRequestEntity>)
    @Query("UPDATE clinic_tasks SET status = :status WHERE id = :id") suspend fun updateTaskStatus(id: Long, status: String)
    @Query("UPDATE service_requests SET status = :status WHERE id = :id") suspend fun updateServiceRequestStatus(id: Long, status: String)
    @Query("SELECT MAX(id) FROM service_requests") suspend fun maxRequestId(): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAbsences(items: List<StaffAbsenceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReplacements(items: List<ShiftReplacementEntity>)
    @Query("DELETE FROM employees") suspend fun clearEmployees()
    @Query("DELETE FROM departments") suspend fun clearDepartments()
    @Query("DELETE FROM schedules") suspend fun clearSchedules()
    @Query("DELETE FROM day_offs") suspend fun clearDayOffs()
    @Query("DELETE FROM notifications") suspend fun clearNotifications()
    @Query("DELETE FROM clinic_tasks") suspend fun clearTasks()
    @Query("DELETE FROM service_requests") suspend fun clearServiceRequests()
    @Query("DELETE FROM staff_absences") suspend fun clearAbsences()
    @Query("DELETE FROM shift_replacements") suspend fun clearReplacements()
}

@Database(
    entities = [EmployeeEntity::class, DepartmentEntity::class, ScheduleEntity::class, DayOffEntity::class, NotificationEntity::class, StaffAbsenceEntity::class, ShiftReplacementEntity::class, TaskEntity::class, ServiceRequestEntity::class],
    version = 4, exportSchema = false
)
abstract class ClinicDatabase : RoomDatabase() {
    abstract fun dao(): ClinicDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS staff_absences (id INTEGER NOT NULL PRIMARY KEY, userId INTEGER NOT NULL, date TEXT NOT NULL, endDate TEXT, type TEXT NOT NULL, reason TEXT, status TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS shift_replacements (id INTEGER NOT NULL PRIMARY KEY, scheduleId INTEGER, employeeId INTEGER NOT NULL, replacementEmployeeId INTEGER NOT NULL, date TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, note TEXT)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS clinic_tasks (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, assigneeId INTEGER, dueDate TEXT, priority TEXT NOT NULL, status TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS service_requests (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, location TEXT NOT NULL, status TEXT NOT NULL, createdAt TEXT)")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun columns(table: String): Set<String> {
                    val result = mutableSetOf<String>()
                    db.query("PRAGMA table_info(`$table`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) if (nameIndex >= 0) result += cursor.getString(nameIndex)
                    }
                    return result
                }
                val oldColumns = columns("notifications")
                db.execSQL("CREATE TABLE IF NOT EXISTS notifications_new (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, message TEXT NOT NULL, createdAt TEXT, isRead INTEGER NOT NULL)")
                if (oldColumns.isNotEmpty() && oldColumns.contains("id")) {
                    val titleExpr = if (oldColumns.contains("title")) "title" else "''"
                    val messageExpr = if (oldColumns.contains("message")) "message" else "''"
                    val dateExpr = when {
                        oldColumns.contains("createdAt") -> "createdAt"
                        oldColumns.contains("created_at") -> "created_at"
                        else -> "NULL"
                    }
                    val readExpr = when {
                        oldColumns.contains("isRead") -> "isRead"
                        oldColumns.contains("readAt") -> "CASE WHEN readAt IS NULL THEN 0 ELSE 1 END"
                        else -> "0"
                    }
                    db.execSQL("INSERT OR REPLACE INTO notifications_new (id,title,message,createdAt,isRead) SELECT CAST(id AS TEXT),$titleExpr,$messageExpr,$dateExpr,$readExpr FROM notifications")
                    db.execSQL("DROP TABLE notifications")
                }
                db.execSQL("ALTER TABLE notifications_new RENAME TO notifications")
            }
        }
        @Volatile private var INSTANCE: ClinicDatabase? = null
        fun get(context: Context): ClinicDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, ClinicDatabase::class.java, "clinic_demo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
        }
    }
}
