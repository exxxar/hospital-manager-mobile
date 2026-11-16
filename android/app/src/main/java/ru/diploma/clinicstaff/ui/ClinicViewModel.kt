package ru.diploma.clinicstaff.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.diploma.clinicstaff.ClinicApp
import ru.diploma.clinicstaff.data.AppMode
import ru.diploma.clinicstaff.data.model.Employee
import ru.diploma.clinicstaff.data.remote.DebugLogStore

data class UiState(
    val mode: AppMode = AppMode.DEMO,
    val loggedIn: Boolean = false,
    val profile: Employee? = null,
    val employees: List<ru.diploma.clinicstaff.data.local.EmployeeEntity> = emptyList(),
    val departments: List<ru.diploma.clinicstaff.data.local.DepartmentEntity> = emptyList(),
    val schedules: List<ru.diploma.clinicstaff.data.local.ScheduleEntity> = emptyList(),
    val dayOffs: List<ru.diploma.clinicstaff.data.local.DayOffEntity> = emptyList(),
    val absences: List<ru.diploma.clinicstaff.data.local.StaffAbsenceEntity> = emptyList(),
    val replacements: List<ru.diploma.clinicstaff.data.local.ShiftReplacementEntity> = emptyList(),
    val tasks: List<ru.diploma.clinicstaff.data.local.TaskEntity> = emptyList(),
    val serviceRequests: List<ru.diploma.clinicstaff.data.local.ServiceRequestEntity> = emptyList(),
    val notifications: List<ru.diploma.clinicstaff.data.local.NotificationEntity> = emptyList(),
    val search: String = "",
    val type: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val diagnosticsRunning: Boolean = false,
    val apiStatus: String = "Не проверено",
    val authStatus: String = "Не проверено",
    val serverDbStatus: String = "Не проверено",
    val diagnosticsCheckedAt: String = "—",
    val lastSyncAt: String = "—"
)

class ClinicViewModel(private val app: ClinicApp) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val diagnosticPrefs = app.getSharedPreferences("clinic_diagnostics", android.content.Context.MODE_PRIVATE)

    init {
        _state.update { it.copy(lastSyncAt = diagnosticPrefs.getString("last_sync_at", "—") ?: "—") }
        viewModelScope.launch {
            app.settings.mode.collect { mode -> _state.update { it.copy(mode = mode) } }
        }
        viewModelScope.launch {
            app.settings.token.collect { token ->
                app.tokenHolder.token = token
                _state.update { it.copy(loggedIn = !token.isNullOrBlank()) }
                if (!token.isNullOrBlank() && _state.value.mode == AppMode.SERVER && app.apiConfigured) loadServerProfile()
            }
        }
        viewModelScope.launch {
            try {
                val storedMode = app.settings.mode.first()
            // Не допускаем восстановления SERVER из старых настроек, если адрес API
            // не был передан в сборку GitHub Actions.
            val savedMode = if (storedMode == AppMode.SERVER && !app.apiConfigured) {
                app.settings.setMode(AppMode.DEMO)
                app.settings.setToken(null)
                app.tokenHolder.token = null
                DebugLogStore.log("WARN", "CONFIG", "Сохранённый SERVER сброшен в DEMO: API_BASE_URL не настроен")
                AppMode.DEMO
            } else storedMode
            _state.update { it.copy(mode = savedMode, error = null) }
            app.seeder.seedIfNeeded()
            if (savedMode == AppMode.DEMO && _state.value.profile == null) {
                val demoUser = app.database.dao().employee(1)
                if (demoUser != null) {
                    _state.update { it.copy(profile = Employee(
                        id=demoUser.id, firstName=demoUser.firstName, lastName=demoUser.lastName,
                        email=demoUser.email, type=demoUser.type, phone=demoUser.phone,
                        specialist=demoUser.specialist
                    )) }
                }
            }
            combine(
                combine(
                    app.repository.employees(),
                    app.repository.departments(),
                    app.repository.schedules(),
                    app.repository.dayOffs(),
                    app.repository.notifications()
                ) { e, d, schedules, days, notifications -> listOf(e, d, schedules, days, notifications) },
                app.repository.absences(),
                app.repository.replacements(),
                app.database.dao().tasks(),
                app.database.dao().serviceRequests()
            ) { core, absences, replacements, tasks, serviceRequests ->
                @Suppress("UNCHECKED_CAST")
                _state.update {
                    it.copy(
                        employees = core[0] as List<ru.diploma.clinicstaff.data.local.EmployeeEntity>,
                        departments = core[1] as List<ru.diploma.clinicstaff.data.local.DepartmentEntity>,
                        schedules = core[2] as List<ru.diploma.clinicstaff.data.local.ScheduleEntity>,
                        dayOffs = core[3] as List<ru.diploma.clinicstaff.data.local.DayOffEntity>,
                        notifications = core[4] as List<ru.diploma.clinicstaff.data.local.NotificationEntity>,
                        absences = absences,
                        replacements = replacements,
                        tasks = tasks,
                        serviceRequests = serviceRequests
                    )
                }
                }.collect()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                DebugLogStore.logException("VIEWMODEL_INIT", failure)
                _state.update { it.copy(error = "Ошибка загрузки данных: ${failure.message ?: failure.javaClass.simpleName}") }
            }
        }
    }

    fun chooseMode(mode: AppMode) = viewModelScope.launch {
        // Сначала проверяем конфигурацию. Нельзя сохранять SERVER при пустом URL.
        if (mode == AppMode.SERVER && !app.apiConfigured) {
            val message = "Серверный режим не настроен. Добавьте API_BASE_URL в GitHub Actions и пересоберите APK. Пока доступен DEMO."
            _state.update { it.copy(mode = AppMode.DEMO, error=message, loading=false) }
            app.settings.setMode(AppMode.DEMO)
            DebugLogStore.log("WARN", "CONFIG", "Переход в SERVER заблокирован: API_BASE_URL отсутствует или некорректен")
            return@launch
        }
        app.settings.setMode(mode)
        if (mode == AppMode.DEMO) {
            _state.update { it.copy(error=null, loading=false) }
            DebugLogStore.log("INFO", "MODE", "Выбран автономный DEMO-режим")
        } else {
            _state.update { it.copy(error=null) }
            refresh()
        }
    }
    fun login(email: String, password: String) = viewModelScope.launch {
        if (!app.apiConfigured) {
            val message = "Не настроен адрес API-сервера. Добавьте API_BASE_URL в GitHub Actions и пересоберите приложение."
            _state.update { it.copy(error=message, loading=false) }
            DebugLogStore.log("ERROR", "CONFIG", message)
            return@launch
        }
        _state.update { it.copy(loading=true,error=null) }
        runCatching { app.repository.login(email,password) }
            .onSuccess { user -> _state.update { it.copy(profile=user,loading=false,loggedIn=true) }; DebugLogStore.log("INFO","AUTH","Вход выполнен: ${user.email}") }
            .onFailure { DebugLogStore.log("ERROR","AUTH",it.message ?: "Ошибка входа"); _state.update { s -> s.copy(loading=false,error=it.message ?: "Не удалось войти") } }
    }
    private suspend fun loadServerProfile() {
        if (!app.apiConfigured || _state.value.mode != AppMode.SERVER) return
        runCatching { app.api.me().data }.onSuccess { user -> _state.update { it.copy(profile=user) } }
            .onFailure { DebugLogStore.log("ERROR", "AUTH", "Не удалось загрузить профиль: ${it.message ?: it.javaClass.simpleName}") }
    }
    fun refresh() = viewModelScope.launch {
        if (_state.value.mode == AppMode.SERVER && !app.apiConfigured) {
            val message = "Сервер не настроен: API_BASE_URL отсутствует. Синхронизация не выполнялась; DEMO-режим остаётся доступен."
            _state.update { it.copy(loading=false,error=message) }
            DebugLogStore.log("WARN", "CONFIG", "Синхронизация отменена: API_BASE_URL не настроен")
            return@launch
        }
        if (_state.value.mode == AppMode.DEMO) {
            DebugLogStore.log("INFO", "SYNC", "Синхронизация пропущена: DEMO работает локально")
            return@launch
        }
        _state.update { it.copy(loading=true,error=null) }
        DebugLogStore.log("INFO","SYNC","Начало синхронизации")
        runCatching {
            app.repository.refreshEmployees(_state.value.search,_state.value.type)
            app.repository.refreshDepartments()
            app.repository.refreshSchedules()
            app.repository.refreshDayOffs()
            app.repository.refreshAbsences()
            app.repository.refreshReplacements()
            app.repository.refreshTasks()
            app.repository.refreshServiceRequests()
            app.repository.refreshNotifications()
            loadServerProfile()
        }.onSuccess {
            val stamp = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            diagnosticPrefs.edit().putString("last_sync_at", stamp).apply()
            _state.update { it.copy(lastSyncAt=stamp) }
            DebugLogStore.log("INFO","SYNC","Синхронизация завершена")
        }.onFailure { failure ->
            _state.update { state -> state.copy(error=failure.message ?: "Ошибка обновления") }
            DebugLogStore.log("ERROR","SYNC",failure.message ?: "Ошибка синхронизации")
        }
        _state.update { it.copy(loading=false) }
    }
    fun runDiagnostics() = viewModelScope.launch {
        _state.update { it.copy(diagnosticsRunning=true, apiStatus="Проверка…", authStatus="Проверка…", serverDbStatus="Проверка…") }
        if (_state.value.mode == AppMode.SERVER && !app.apiConfigured) {
            val message = "API_BASE_URL не задан или имеет неверный формат. Сетевые запросы не выполнялись."
            _state.update { it.copy(apiStatus="Не настроен", authStatus="Не проверена", serverDbStatus="Не проверена", diagnosticsCheckedAt=java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()), diagnosticsRunning=false, error=message) }
            DebugLogStore.log("WARN", "DIAG", message)
            return@launch
        }
        val checked = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        if (_state.value.mode == AppMode.DEMO) {
            _state.update { it.copy(apiStatus="Не используется в DEMO", authStatus="Локальный профиль", serverDbStatus="Не используется в DEMO", diagnosticsCheckedAt=checked, diagnosticsRunning=false) }
            DebugLogStore.log("INFO","DIAG","Диагностика DEMO: локальный режим")
            return@launch
        }
        try {
            val health = app.api.health()
            val payload = (health.body() ?: health.errorBody())?.string().orEmpty()
            val dbState = runCatching { org.json.JSONObject(payload).optString("database", "unknown") }.getOrDefault("unknown")
            val dbLabel = when(dbState) { "ok" -> "Подключена"; "error" -> "Ошибка подключения"; else -> "Неизвестно" }
            _state.update { it.copy(apiStatus=if (health.isSuccessful) "Доступен · HTTP ${health.code()}" else "Ошибка · HTTP ${health.code()}", serverDbStatus=dbLabel) }
            DebugLogStore.log(if(health.isSuccessful) "INFO" else "ERROR","DIAG","Health check HTTP ${health.code()} · DB: $dbState")
        } catch (e: Exception) {
            _state.update { it.copy(apiStatus="Недоступен: ${e.javaClass.simpleName}", serverDbStatus="Не проверена") }
            DebugLogStore.log("ERROR","DIAG","API health: ${e.message ?: e.javaClass.simpleName}")
        }
        try {
            app.api.me()
            _state.update { it.copy(authStatus="Авторизация работает") }
            DebugLogStore.log("INFO","DIAG","Проверка авторизации успешна")
        } catch (e: Exception) {
            _state.update { it.copy(authStatus="Ошибка авторизации: ${e.message ?: e.javaClass.simpleName}") }
            DebugLogStore.log("ERROR","DIAG","Проверка авторизации: ${e.message ?: e.javaClass.simpleName}")
        }
        _state.update { it.copy(diagnosticsCheckedAt=checked,diagnosticsRunning=false) }
    }

    fun search(value: String) { _state.update { it.copy(search=value) }; refreshEmployees() }
    fun filterType(value: String) { _state.update { it.copy(type=value) }; refreshEmployees() }
    private fun refreshEmployees() = viewModelScope.launch {
        if (_state.value.mode == AppMode.SERVER && !app.apiConfigured) {
            DebugLogStore.log("WARN", "CONFIG", "Поиск на сервере пропущен: API_BASE_URL не настроен")
            return@launch
        }
        runCatching { app.repository.refreshEmployees(_state.value.search,_state.value.type) }
            .onFailure { DebugLogStore.log("ERROR", "SYNC", "Поиск сотрудников: ${it.message ?: it.javaClass.simpleName}") }
    }
    fun markRead(id: String) = viewModelScope.launch { runCatching { app.repository.markNotificationRead(id) }.onFailure { error -> _state.update { state -> state.copy(error=error.message) } } }
    fun logout() = viewModelScope.launch {
        if (app.apiConfigured && _state.value.mode == AppMode.SERVER) runCatching { app.repository.logout() }
        else app.settings.setToken(null)
        DebugLogStore.log("INFO","AUTH","Выход из учётной записи")
        _state.update { it.copy(profile=null,loggedIn=false) }
    }
    fun selectDemoEmployee(id: Long) = viewModelScope.launch {
        val person = app.database.dao().employee(id) ?: return@launch
        _state.update { it.copy(profile = Employee(
            id=person.id, firstName=person.firstName, lastName=person.lastName,
            email=person.email, type=person.type, phone=person.phone,
            specialist=person.specialist
        )) }
    }
    fun resetDemo() = viewModelScope.launch { app.seeder.reset() }
    fun updateTaskStatus(id: Long, status: String) = viewModelScope.launch {
        runCatching { app.repository.updateTaskStatus(id, status); refresh() }
            .onFailure { e -> _state.update { it.copy(error=e.message ?: "Не удалось обновить задачу") } }
    }
    fun createTask(title:String,description:String,assigneeId:Long?,dueDate:String?,priority:String) = viewModelScope.launch {
        runCatching { app.repository.createTask(title,description,assigneeId,dueDate,priority); DebugLogStore.log("INFO","ADMIN","Создана задача: $title"); refresh() }
            .onFailure { e -> _state.update { it.copy(error=e.message ?: "Не удалось создать задачу") }; DebugLogStore.log("ERROR","ADMIN",e.message ?: "Ошибка создания задачи") }
    }
    fun updateRequestStatus(id:Long,status:String) = viewModelScope.launch {
        runCatching { app.repository.updateServiceRequestStatus(id,status); DebugLogStore.log("INFO","ADMIN","Заявка #$id: $status"); refresh() }
            .onFailure { e -> _state.update { it.copy(error=e.message ?: "Не удалось обновить заявку") }; DebugLogStore.log("ERROR","ADMIN",e.message ?: "Ошибка обработки заявки") }
    }
    fun createServiceRequest(title: String, category: String, description: String, location: String) = viewModelScope.launch {
        runCatching { app.repository.createServiceRequest(title, category, description, location) }
            .onFailure { error -> _state.update { it.copy(error=error.message ?: "Не удалось создать заявку") } }
    }
}
