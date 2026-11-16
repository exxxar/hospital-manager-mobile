package ru.diploma.clinicstaff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.diploma.clinicstaff.data.AppMode
import ru.diploma.clinicstaff.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ClinicApp
        setContent {
            val vm: ClinicViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ClinicViewModel(app) as T
            })
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF145B55), secondary = androidx.compose.ui.graphics.Color(0xFF477F79))) {
                ClinicRoot(vm)
            }
        }
    }
}

private enum class Tab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Главная", Icons.Default.Home), STAFF("Сотрудники", Icons.Default.Groups),
    DEPARTMENTS("Отделения", Icons.Default.LocalHospital), SCHEDULE("График", Icons.Default.CalendarMonth),
    ALERTS("Уведомления", Icons.Default.Notifications), ACTIVITY("Активность", Icons.Default.DynamicFeed),
    TASKS("Задачи", Icons.Default.TaskAlt), REQUESTS("Заявки", Icons.Default.SupportAgent),
    ADMIN("Админ", Icons.Default.AdminPanelSettings), DIAGNOSTICS("Диагностика", Icons.Default.HealthAndSafety), DEBUG("Отладка", Icons.Default.BugReport),
    PROFILE("Профиль", Icons.Default.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClinicRoot(vm: ClinicViewModel) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var selectedEmployee by remember { mutableStateOf<Long?>(null) }
    var showLogin by remember { mutableStateOf(false) }

    if (state.mode == AppMode.SERVER && !state.loggedIn) {
        LoginScreen(state.loading,state.error, onLogin=vm::login, onBack={ vm.chooseMode(AppMode.DEMO) })
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Клиника", fontWeight=FontWeight.Bold)
                    Text(if (state.mode == AppMode.DEMO) "ДЕМО-режим · локальные данные" else "Подключение к серверу", style=MaterialTheme.typography.labelSmall)
                } },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, contentDescription="Обновить") }
                    IconButton(onClick = { tab = Tab.PROFILE; selectedEmployee = null }) {
                        Icon(Icons.Default.AccountCircle, contentDescription="Личный кабинет")
                    }
                    IconButton(onClick = { showLogin = true }) { Icon(Icons.Default.Settings, contentDescription="Режим") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.values().filter { it != Tab.ADMIN || state.profile?.type == "admin" }.forEach { item ->
                    NavigationBarItem(selected=tab==item, onClick={tab=item;selectedEmployee=null},
                        icon={Icon(item.icon,contentDescription=item.title)}, label={Text(item.title)})
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { Text(it, color=MaterialTheme.colorScheme.error, modifier=Modifier.padding(12.dp)) }
            if (selectedEmployee != null) {
                val person = state.employees.firstOrNull { it.id == selectedEmployee }
                if (person != null) EmployeeDetail(person, onBack={selectedEmployee=null})
            } else when(tab) {
                Tab.HOME -> HomeScreen(state, onNavigate={ tab=it })
                Tab.STAFF -> StaffScreen(state, vm::search, vm::filterType, onSelect={selectedEmployee=it})
                Tab.DEPARTMENTS -> DepartmentsScreen(state)
                Tab.SCHEDULE -> ScheduleScreen(state)
                Tab.ALERTS -> NotificationsScreen(state, vm::markRead)
                Tab.ACTIVITY -> ActivityScreen(state)
                Tab.TASKS -> TasksScreen(state, vm::updateTaskStatus)
                Tab.REQUESTS -> RequestsScreen(state, vm::createServiceRequest)
                Tab.ADMIN -> if (state.profile?.type == "admin") AdminScreen(state, vm::createTask, vm::updateTaskStatus, vm::updateRequestStatus)
                    else AccessDeniedScreen()
                Tab.DIAGNOSTICS -> DiagnosticsScreen(state, vm::runDiagnostics)
                Tab.DEBUG -> DebugScreen()
                Tab.PROFILE -> ProfileScreen(
                    state = state,
                    onSelectDemoEmployee = vm::selectDemoEmployee,
                    onLogout = vm::logout
                )
            }
        }
    }
    if (showLogin) {
        ModeDialog(state.mode, onDismiss={showLogin=false}, onMode={ vm.chooseMode(it); showLogin=false },
            onLogout={vm.logout();showLogin=false}, onReset={vm.resetDemo();showLogin=false})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(loading:Boolean,error:String?,onLogin:(String,String)->Unit,onBack:()->Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.Center) {
        Icon(Icons.Default.LocalHospital,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Вход для сотрудников",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Используйте учётную запись медицинской клиники.")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email,{email=it},label={Text("Электронная почта")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Пароль")},singleLine=true,modifier=Modifier.fillMaxWidth())
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(onClick={onLogin(email,password)},enabled=!loading && email.isNotBlank() && password.isNotBlank(),modifier=Modifier.fillMaxWidth()) {
            if(loading) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Text("Войти")
        }
        TextButton(onClick=onBack,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Вернуться в демо")}
    }
}

@Composable
private fun HomeScreen(s:UiState, onNavigate:(Tab)->Unit) {
    val today = LocalDate.now()
    val todayAbsences = s.absences.count { absence ->
        val from=runCatching{LocalDate.parse(absence.date.take(10))}.getOrNull()
        val to=runCatching{LocalDate.parse((absence.endDate?:absence.date).take(10))}.getOrNull() ?: from
        from!=null && to!=null && !today.isBefore(from) && !today.isAfter(to) && absence.status.lowercase()!="rejected"
    }
    val unread=s.notifications.count{!it.isRead}
    val todayShifts=s.schedules.count { item ->
        val exact=runCatching{item.date?.let{LocalDate.parse(it.take(10))}}.getOrNull()
        if(exact!=null) exact==today else item.dayOfWeek.normalizedWeekday()==today.dayOfWeek.value
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Добро пожаловать",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(s.profile?.fullName ?: "Демонстрационная учётная запись",style=MaterialTheme.typography.titleMedium)
            Text(today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale("ru","RU"))).replaceFirstChar{it.titlecase(Locale("ru","RU"))},
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Рабочий день",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text("Сегодня в расписании $todayShifts смен · отсутствуют $todayAbsences")
                    }
                    Icon(Icons.Default.LocalHospital,null,modifier=Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SectionTitle("Быстрый доступ") }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={onNavigate(Tab.STAFF)},modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.Groups,null); Spacer(Modifier.width(6.dp)); Text("Сотрудники")
                    }
                    OutlinedButton(onClick={onNavigate(Tab.SCHEDULE)},modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.CalendarMonth,null); Spacer(Modifier.width(6.dp)); Text("Расписание")
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={onNavigate(Tab.ACTIVITY)},modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.DynamicFeed,null); Spacer(Modifier.width(6.dp)); Text("Активность")
                    }
                    OutlinedButton(onClick={onNavigate(Tab.ALERTS)},modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.Notifications,null); Spacer(Modifier.width(6.dp)); Text("Уведомления")
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={onNavigate(Tab.TASKS)},modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.TaskAlt,null); Spacer(Modifier.width(6.dp)); Text("Мои задачи")
                    }
                    OutlinedButton(onClick={onNavigate(Tab.REQUESTS)},modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.SupportAgent,null); Spacer(Modifier.width(6.dp)); Text("Заявки")
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={onNavigate(Tab.DIAGNOSTICS)},modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.HealthAndSafety,null); Spacer(Modifier.width(6.dp)); Text("Диагностика")
                    }
                    if (s.profile?.type == "admin") {
                        OutlinedButton(onClick={onNavigate(Tab.ADMIN)},modifier=Modifier.weight(1f)) {
                            Icon(Icons.Default.AdminPanelSettings,null); Spacer(Modifier.width(6.dp)); Text("Администрирование")
                        }
                    }
                    OutlinedButton(onClick={onNavigate(Tab.DEBUG)},modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.BugReport,null); Spacer(Modifier.width(6.dp)); Text("Отладка")
                    }
                }
            }
        }
        item { SectionTitle("Клиника сегодня") }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MetricCard("Сотрудники",s.employees.size.toString(),Modifier.weight(1f))
                MetricCard("Отделения",s.departments.size.toString(),Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MetricCard("Новые уведомления",unread.toString(),Modifier.weight(1f))
                MetricCard("Отсутствуют",todayAbsences.toString(),Modifier.weight(1f))
            }
        }
        item { SectionTitle("Ближайшие смены") }
        if(s.schedules.isEmpty()) item { ListCard("Расписание пока пустое","После синхронизации здесь появятся смены сотрудников.") }
        items(s.schedules.take(4)) { item ->
            val employee=s.employees.firstOrNull{it.id==item.userId}
            ListCard(employee?.let{"${it.lastName} ${it.firstName}"} ?: item.dayOfWeek.ifBlank{"Смена"},
                "${item.startTime} — ${item.endTime}${if(item.date!=null) " · ${item.date}" else " · ${item.dayOfWeek}"}")
        }
        item { SectionTitle("Кадровые события") }
        val latestAbsences=s.absences.sortedByDescending{it.date}.take(3)
        if(latestAbsences.isEmpty()) item { ListCard("Нет кадровых событий","Новые отпуска и больничные будут отображаться здесь.") }
        items(latestAbsences) { absence ->
            val employee=s.employees.firstOrNull{it.id==absence.userId}
            val label=if(absence.type.lowercase() in listOf("sick_leave","sick")) "Больничный" else "Отпуск"
            ListCard("$label · ${employee?.lastName ?: "Сотрудник"}","${absence.date} — ${absence.endDate ?: absence.date} · ${statusLabel(absence.status)}")
        }
        item { SectionTitle("Последние уведомления") }
        if(s.notifications.isEmpty()) item { ListCard("Пока тихо","Новые уведомления клиники появятся в этом разделе.") }
        items(s.notifications.take(3)) { item ->
            ListCard(item.title,(if(item.isRead) "Прочитано · " else "Новое · ")+item.message)
        }
    }
}

@Composable
private fun ActivityScreen(s:UiState) {
    data class FeedItem(val date:String,val title:String,val detail:String,val kind:String)
    val feed = buildList {
        s.notifications.forEach { add(FeedItem(it.createdAt ?: "",it.title,it.message,"notification")) }
        s.absences.forEach { absence ->
            val person=s.employees.firstOrNull{it.id==absence.userId}
            val kind=if(absence.type.lowercase() in listOf("sick_leave","sick")) "Больничный" else "Отпуск"
            add(FeedItem(absence.date,"$kind · ${person?.let{it.lastName+" "+it.firstName} ?: "Сотрудник"}",
                "${absence.date} — ${absence.endDate ?: absence.date} · ${statusLabel(absence.status)}","absence"))
        }
        s.replacements.forEach { item ->
            val from=s.employees.firstOrNull{it.id==item.employeeId}
            val to=s.employees.firstOrNull{it.id==item.replacementEmployeeId}
            add(FeedItem(item.date,"Замена рабочей смены","${from?.lastName ?: "Сотрудник"} → ${to?.lastName ?: "Сотрудник"} · ${item.startTime}–${item.endTime}","replacement"))
        }
        s.dayOffs.forEach { item ->
            val person=s.employees.firstOrNull{it.id==item.userId}
            add(FeedItem(item.date,"Выходной · ${person?.lastName ?: "Сотрудник"}",item.reason ?: "Запланированный выходной","dayoff"))
        }
        s.tasks.forEach { task ->
            add(FeedItem(task.dueDate ?: "", "Задача · ${task.title}", "${statusLabel(task.status)} · ${task.description}", "task"))
        }
        s.serviceRequests.forEach { request ->
            add(FeedItem(request.createdAt ?: "", "Заявка · ${request.title}", "${request.category} · ${request.location} · ${request.status}", "request"))
        }
    }.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Лента активности",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Кадровые события и обновления клиники",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if(feed.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DynamicFeed,null,modifier=Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary)
                    Text("Пока нет активности",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text("Когда появятся уведомления, отпуска, выходные и замены, они отобразятся здесь.")
                }
            }
        } else {
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(feed) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.Top) {
                            val icon=when(item.kind) {
                                "absence"->Icons.Default.EventBusy
                                "replacement"->Icons.Default.SwapHoriz
                                "dayoff"->Icons.Default.BeachAccess
                                "task"->Icons.Default.TaskAlt
                                "request"->Icons.Default.SupportAgent
                                else->Icons.Default.Notifications
                            }
                            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title,fontWeight=FontWeight.SemiBold)
                                Text(item.detail,style=MaterialTheme.typography.bodySmall)
                                if(item.date.isNotBlank()) Text(item.date.take(16),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksScreen(s:UiState,onStatus:(Long,String)->Unit) {
    var filter by remember { mutableStateOf("all") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Задачи",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Поручения и рабочие задачи сотрудников",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("all" to "Все","new" to "Новые","in_progress" to "В работе","done" to "Готово").forEach { (key,label) ->
                FilterChip(selected=filter==key,onClick={filter=key},label={Text(label)})
            }
        }
        val tasks=s.tasks.filter { filter=="all" || it.status==filter }
        if(tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("Задач в этой категории нет") }
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
            items(tasks) { task ->
                val employee=s.employees.firstOrNull{it.id==task.assigneeId}
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(task.title,Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                            PriorityBadge(task.priority)
                        }
                        Text(task.description,style=MaterialTheme.typography.bodySmall)
                        Text("Исполнитель: ${employee?.let{it.firstName+" "+it.lastName} ?: "Не назначен"}",
                            style=MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("Срок: ${task.dueDate ?: "не указан"} · ${statusLabel(task.status)}",
                                modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                            if(task.status!="done") {
                                TextButton(onClick={onStatus(task.id,if(task.status=="new") "in_progress" else "done")}) {
                                    Text(if(task.status=="new") "В работу" else "Завершить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority:String) {
    val label=when(priority.lowercase()){"high","urgent"->"Высокий";"low"->"Низкий";else->"Обычный"}
    val color=when(priority.lowercase()) {
        "high","urgent"->MaterialTheme.colorScheme.errorContainer
        "low"->MaterialTheme.colorScheme.surfaceVariant
        else->MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color=color,shape=MaterialTheme.shapes.small) {
        Text(label,Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestsScreen(
    s:UiState,
    onCreate:(String,String,String,String)->Unit
) {
    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("IT") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var categoryMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Заявки",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("Обращения в службы клиники",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick={showForm=true}) { Icon(Icons.Default.Add,null); Text("Создать") }
        }
        Spacer(Modifier.height(12.dp))
        if(s.serviceRequests.isEmpty()) {
            Text("Заявок пока нет",color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(s.serviceRequests) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(item.title,Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                            RequestStatus(item.status)
                        }
                        Text("${item.category} · ${item.location}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        Text(item.description,style=MaterialTheme.typography.bodySmall)
                        Text("Создана: ${item.createdAt ?: "—"}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if(showForm) {
        AlertDialog(
            onDismissRequest={showForm=false},
            title={Text("Новая заявка")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title,{title=it},label={Text("Тема")},singleLine=true)
                    ExposedDropdownMenuBox(expanded=categoryMenu,onExpandedChange={categoryMenu=it}) {
                        OutlinedTextField(category,{},readOnly=true,label={Text("Служба")},
                            trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(categoryMenu)},
                            modifier=Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded=categoryMenu,onDismissRequest={categoryMenu=false}) {
                            listOf("IT","Хозяйственная служба","Администрация").forEach { option ->
                                DropdownMenuItem(text={Text(option)},onClick={category=option;categoryMenu=false})
                            }
                        }
                    }
                    OutlinedTextField(description,{description=it},label={Text("Описание")},minLines=3)
                    OutlinedTextField(location,{location=it},label={Text("Место / кабинет")},singleLine=true)
                }
            },
            confirmButton={
                TextButton(onClick={
                    onCreate(title,category,description,location)
                    title="";description="";location="";showForm=false
                },enabled=title.isNotBlank() && description.isNotBlank()) { Text("Отправить") }
            },
            dismissButton={TextButton(onClick={showForm=false}){Text("Отмена")}}
        )
    }
}

@Composable
private fun RequestStatus(status:String) {
    val label=when(status.lowercase()) {
        "in_progress"->"В работе";"done","closed"->"Выполнена";"rejected"->"Отклонена";else->"Новая"
    }
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.small) {
        Text(label,Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall)
    }
}


@Composable
private fun AccessDeniedScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock,null,modifier=Modifier.size(48.dp),tint=MaterialTheme.colorScheme.error)
        Text("Нет доступа",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Административный раздел доступен только пользователям с ролью admin.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminScreen(
    s:UiState,
    onCreateTask:(String,String,Long?,String?,String)->Unit,
    onTaskStatus:(Long,String)->Unit,
    onRequestStatus:(Long,String)->Unit
) {
    var showTaskForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf<Long?>(null) }
    var dueDate by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("normal") }
    var employeeMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Администрирование",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Управление задачами и обращениями сотрудников",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=section==0,onClick={section=0},label={Text("Задачи (${s.tasks.size})")})
            FilterChip(selected=section==1,onClick={section=1},label={Text("Заявки (${s.serviceRequests.size})")})
        }
        if(section==0) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Поручения",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                Button(onClick={showTaskForm=true}) { Icon(Icons.Default.Add,null); Text("Поставить задачу") }
            }
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(s.tasks) { task ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(task.title,fontWeight=FontWeight.SemiBold)
                            Text(task.description,style=MaterialTheme.typography.bodySmall)
                            val person=s.employees.firstOrNull{it.id==task.assigneeId}
                            Text("Исполнитель: ${person?.let{it.firstName+" "+it.lastName} ?: "Не назначен"} · срок ${task.dueDate ?: "—"}",
                                style=MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                PriorityBadge(task.priority)
                                Spacer(Modifier.weight(1f))
                                Text(statusLabel(task.status),style=MaterialTheme.typography.labelSmall)
                                if(task.status!="done") TextButton(onClick={onTaskStatus(task.id,if(task.status=="new") "in_progress" else "done")}) {
                                    Text(if(task.status=="new") "В работу" else "Завершить")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text("Обращения сотрудников",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(s.serviceRequests) { req ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(req.title,Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                                RequestStatus(req.status)
                            }
                            Text("${req.category} · ${req.location}",style=MaterialTheme.typography.labelSmall)
                            Text(req.description,style=MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick={onRequestStatus(req.id,"in_progress")}) { Text("В работу") }
                                TextButton(onClick={onRequestStatus(req.id,"done")}) { Text("Выполнена") }
                                TextButton(onClick={onRequestStatus(req.id,"rejected")}) { Text("Отклонить") }
                            }
                        }
                    }
                }
            }
        }
    }
    if(showTaskForm) {
        AlertDialog(
            onDismissRequest={showTaskForm=false},
            title={Text("Новая задача")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(title,{title=it},label={Text("Название")},singleLine=true)
                    OutlinedTextField(description,{description=it},label={Text("Описание")},minLines=2)
                    ExposedDropdownMenuBox(expanded=employeeMenu,onExpandedChange={employeeMenu=it}) {
                        OutlinedTextField(
                            value=assignee?.let{id->s.employees.firstOrNull{it.id==id}?.let{it.firstName+" "+it.lastName}} ?: "Без исполнителя",
                            onValueChange={},readOnly=true,label={Text("Исполнитель")},
                            trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(employeeMenu)},
                            modifier=Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded=employeeMenu,onDismissRequest={employeeMenu=false}) {
                            DropdownMenuItem(text={Text("Без исполнителя")},onClick={assignee=null;employeeMenu=false})
                            s.employees.forEach { e ->
                                DropdownMenuItem(text={Text("${e.firstName} ${e.lastName}")},onClick={assignee=e.id;employeeMenu=false})
                            }
                        }
                    }
                    OutlinedTextField(dueDate,{dueDate=it},label={Text("Срок (ГГГГ-ММ-ДД)")},singleLine=true)
                    ExposedDropdownMenuBox(expanded=priorityMenu,onExpandedChange={priorityMenu=it}) {
                        OutlinedTextField(priority,{},readOnly=true,label={Text("Приоритет")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(priorityMenu)},modifier=Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded=priorityMenu,onDismissRequest={priorityMenu=false}) {
                            listOf("low","normal","high").forEach { p -> DropdownMenuItem(text={Text(when(p){"low"->"Низкий";"high"->"Высокий";else->"Обычный"})},onClick={priority=p;priorityMenu=false}) }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={
                onCreateTask(title,description,assignee,dueDate.ifBlank{null},priority)
                title="";description="";dueDate="";assignee=null;showTaskForm=false
            },enabled=title.isNotBlank()){Text("Создать")}},
            dismissButton={TextButton(onClick={showTaskForm=false}){Text("Отмена")}}
        )
    }
}

@Composable
private fun DiagnosticsScreen(s: UiState, onCheck: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Диагностика системы", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
        Text(if(s.mode==ru.diploma.clinicstaff.data.AppMode.DEMO) "Работа в автономном демонстрационном режиме" else "Проверка соединения с сервером клиники")
        Button(onClick=onCheck, enabled=!s.diagnosticsRunning, modifier=Modifier.fillMaxWidth()) {
            if(s.diagnosticsRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth=2.dp)
            else Icon(Icons.Default.Refresh, contentDescription=null)
            Spacer(Modifier.width(8.dp)); Text(if(s.diagnosticsRunning) "Проверяем…" else "Запустить проверку")
        }
        Text("Состояние подключения", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        DiagnosticRow("API-сервер", s.apiStatus)
        DiagnosticRow("Авторизация", s.authStatus)
        DiagnosticRow("База данных сервера", s.serverDbStatus)
        DiagnosticRow("Последняя проверка", s.diagnosticsCheckedAt)
        HorizontalDivider()
        Text("Локальные данные", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        DiagnosticRow("Локальная БД", "Room · доступна приложению")
        DiagnosticRow("Сотрудники", s.employees.size.toString())
        DiagnosticRow("Отделения", s.departments.size.toString())
        DiagnosticRow("Записи расписания", s.schedules.size.toString())
        DiagnosticRow("Уведомления", s.notifications.size.toString())
        DiagnosticRow("Задачи", s.tasks.size.toString())
        DiagnosticRow("Заявки", s.serviceRequests.size.toString())
        DiagnosticRow("Последняя синхронизация", s.lastSyncAt)
        HorizontalDivider()
        Text("Подсказка", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        Text("Если API недоступен — проверьте адрес сервера в API_BASE_URL, доступность сети и настройки HTTPS. Ошибки запросов и синхронизации записываются во вкладку «Отладка».", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(label, modifier=Modifier.weight(1f), style=MaterialTheme.typography.bodyMedium)
            Text(value, style=MaterialTheme.typography.bodySmall, fontWeight=FontWeight.SemiBold, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DebugScreen() {
    val entries by ru.diploma.clinicstaff.data.remote.DebugLogStore.entries.collectAsState()
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    val context=androidx.compose.ui.platform.LocalContext.current
    var filter by remember { mutableStateOf("ALL") }
    val visible=entries.filter { filter=="ALL" || it.level==filter }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Отладка приложения",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("${entries.size} записей · последние 300 сохраняются локально",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(ru.diploma.clinicstaff.data.remote.DebugLogStore.exportText()))}) { Icon(Icons.Default.ContentCopy,"Копировать") }
            IconButton(onClick={ru.diploma.clinicstaff.data.remote.DebugLogStore::clear}) { Icon(Icons.Default.DeleteSweep,"Очистить") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("ALL","INFO","WARN","ERROR","HTTP").forEach { f ->
                FilterChip(selected=filter==f,onClick={filter=f},label={Text(f)})
            }
        }
        Spacer(Modifier.height(8.dp))
        if(visible.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("Записей пока нет") }
        else LazyColumn(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(visible.reversed()) { entry ->
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=when(entry.level) {
                    "ERROR"->MaterialTheme.colorScheme.errorContainer
                    "WARN"->MaterialTheme.colorScheme.tertiaryContainer
                    else->MaterialTheme.colorScheme.surfaceVariant
                })) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${entry.time}  ${entry.level}  ${entry.tag}",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                        Text(entry.message,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffScreen(s:UiState,onSearch:(String)->Unit,onType:(String)->Unit,onSelect:(Long)->Unit) {
    var search by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Сотрудники",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        OutlinedTextField(search,{search=it;onSearch(it)},modifier=Modifier.fillMaxWidth(),label={Text("Поиск по имени или email")},singleLine=true)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("" to "Все","doctor" to "Врачи","nurse" to "Медсёстры","accountant" to "Бухгалтеры").forEach { (v,label) ->
                FilterChip(selected=type==v,onClick={type=v;onType(v)},label={Text(label)})
            }
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(s.employees) { e ->
                Card(onClick={onSelect(e.id)},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${e.firstName} ${e.lastName}",fontWeight=FontWeight.SemiBold)
                            Text("${roleName(e.type)} · ${e.email}",style=MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight,null)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeDetail(e:ru.diploma.clinicstaff.data.local.EmployeeEntity,onBack:()->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick=onBack){Text("← Назад")}
        Icon(Icons.Default.AccountCircle,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(72.dp))
        Text("${e.firstName} ${e.lastName}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        DetailLine("Должность",roleName(e.type)); DetailLine("Email",e.email)
        DetailLine("Телефон",e.phone ?: "Не указан")
        DetailLine("Специализация",e.specialist ?: "Не указана")
    }
}

@Composable
private fun DepartmentsScreen(s:UiState) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("Отделения",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold) }
        items(s.departments) { d -> ListCard(d.name,d.description ?: "Информация не указана") }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleScreen(s:UiState) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var employeeFilter by remember { mutableStateOf<Long?>(s.profile?.id) }
    var employeeMenu by remember { mutableStateOf(false) }
    val locale = remember { Locale("ru", "RU") }
    val leading = month.atDay(1).dayOfWeek.value - 1
    val totalCells = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    val weekNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val filteredSchedules = s.schedules.filter { employeeFilter == null || it.userId == employeeFilter }
    val filteredDaysOff = s.dayOffs.filter { employeeFilter == null || it.userId == employeeFilter }
    val filteredAbsences = s.absences.filter { employeeFilter == null || it.userId == employeeFilter }
    val filteredReplacements = s.replacements.filter { employeeFilter == null || it.employeeId == employeeFilter || it.replacementEmployeeId == employeeFilter }
    fun inRange(start: String, end: String?, date: LocalDate): Boolean {
        val from = runCatching { LocalDate.parse(start.take(10)) }.getOrNull() ?: return false
        val to = runCatching { LocalDate.parse((end ?: start).take(10)) }.getOrNull() ?: from
        return !date.isBefore(from) && !date.isAfter(to)
    }
    fun hasSchedule(date: LocalDate) = filteredSchedules.any { item ->
        val exact = runCatching { item.date?.let { LocalDate.parse(it.take(10)) } }.getOrNull()
        if (exact != null) exact == date else item.dayOfWeek.normalizedWeekday() == date.dayOfWeek.value
    }
    fun hasDayOff(date: LocalDate) = filteredDaysOff.any { it.date.take(10) == date.toString() }
    fun hasAbsence(date: LocalDate) = filteredAbsences.any { inRange(it.date,it.endDate,date) && it.status.lowercase() != "rejected" }
    fun hasReplacement(date: LocalDate) = filteredReplacements.any { it.date.take(10) == date.toString() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("График сотрудников",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        ExposedDropdownMenuBox(expanded=employeeMenu,onExpandedChange={employeeMenu=it}) {
            OutlinedTextField(
                value=if(employeeFilter==null) "Все сотрудники" else s.employees.firstOrNull { it.id==employeeFilter }?.let { "${it.lastName} ${it.firstName}" } ?: "Мой график",
                onValueChange={},readOnly=true,label={Text("Фильтр по сотруднику")},
                trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded=employeeMenu)},
                modifier=Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded=employeeMenu,onDismissRequest={employeeMenu=false}) {
                DropdownMenuItem(text={Text("Все сотрудники")},onClick={employeeFilter=null;employeeMenu=false})
                s.employees.forEach { person ->
                    DropdownMenuItem(text={Text("${person.lastName} ${person.firstName}")},onClick={employeeFilter=person.id;employeeMenu=false})
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={month=month.minusMonths(1);selectedDate=month.atDay(1)}) { Icon(Icons.Default.ChevronLeft,contentDescription="Предыдущий месяц") }
                    Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE,locale).replaceFirstChar{it.titlecase(locale)}+" ${month.year}",
                        modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    IconButton(onClick={month=month.plusMonths(1);selectedDate=month.atDay(1)}) { Icon(Icons.Default.ChevronRight,contentDescription="Следующий месяц") }
                }
                Row(Modifier.fillMaxWidth()) { weekNames.forEach { Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelMedium) } }
                for (week in 0 until totalCells/7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (column in 0..6) {
                            val number=week*7+column-leading+1
                            if(number in 1..month.lengthOfMonth()) {
                                val date=month.atDay(number)
                                val selected=date==selectedDate
                                Column(Modifier.weight(1f).padding(2.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    Surface(onClick={selectedDate=date},shape=MaterialTheme.shapes.medium,
                                        color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        modifier=Modifier.fillMaxWidth().height(40.dp)) {
                                        Box(contentAlignment=Alignment.Center) {
                                            Text("$number",color=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                    Row(Modifier.height(9.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                                        if(hasSchedule(date)) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary,androidx.compose.foundation.shape.CircleShape))
                                        if(hasDayOff(date)) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.tertiary,androidx.compose.foundation.shape.CircleShape))
                                        if(hasAbsence(date)) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.error,androidx.compose.foundation.shape.CircleShape))
                                        if(hasReplacement(date)) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.secondary,androidx.compose.foundation.shape.CircleShape))
                                    }
                                }
                            } else Spacer(Modifier.weight(1f).height(49.dp))
                        }
                    }
                }
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary,"Смена")
                    LegendDot(MaterialTheme.colorScheme.tertiary,"Выходной")
                    LegendDot(MaterialTheme.colorScheme.error,"Отпуск / больничный")
                    LegendDot(MaterialTheme.colorScheme.secondary,"Замена смены")
                }
            }
        }
        SectionTitle(selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy",locale)).replaceFirstChar{it.titlecase(locale)})
        val daySchedules=filteredSchedules.filter { item ->
            val exact=runCatching{item.date?.let{LocalDate.parse(it.take(10))}}.getOrNull()
            if(exact!=null) exact==selectedDate else item.dayOfWeek.normalizedWeekday()==selectedDate.dayOfWeek.value
        }
        val dayOffs=filteredDaysOff.filter{it.date.take(10)==selectedDate.toString()}
        val dayAbsences=filteredAbsences.filter{inRange(it.date,it.endDate,selectedDate) && it.status.lowercase()!="rejected"}
        val dayReplacements=filteredReplacements.filter{it.date.take(10)==selectedDate.toString()}
        if(daySchedules.isEmpty()&&dayOffs.isEmpty()&&dayAbsences.isEmpty()&&dayReplacements.isEmpty()) {
            ListCard("Нет записей","На выбранную дату нет смен, отсутствий или замен.")
        }
        daySchedules.forEach { x ->
            val employee=s.employees.firstOrNull{it.id==x.userId}
            ListCard(employee?.let{"${it.lastName} ${it.firstName}"} ?: "Рабочая смена","${x.startTime} — ${x.endTime} · ${x.dayOfWeek}")
        }
        dayOffs.forEach { x -> ListCard("Выходной день",x.reason ?: "Запланированный выходной") }
        dayAbsences.forEach { x ->
            val employee=s.employees.firstOrNull{it.id==x.userId}
            val kind=when(x.type.lowercase()){"sick_leave","sick","больничный"->"Больничный";else->"Отпуск"}
            val period=if(x.endDate!=null && x.endDate!=x.date) "${x.date} — ${x.endDate}" else x.date
            ListCard("$kind · ${employee?.let{it.lastName+" "+it.firstName} ?: "Сотрудник"}","$period · ${x.reason ?: "Причина не указана"} · ${statusLabel(x.status)}")
        }
        dayReplacements.forEach { x ->
            val from=s.employees.firstOrNull{it.id==x.employeeId}
            val to=s.employees.firstOrNull{it.id==x.replacementEmployeeId}
            ListCard("Замена смены","${from?.lastName ?: "Сотрудник"} → ${to?.lastName ?: "Сотрудник"} · ${x.startTime}–${x.endTime}${x.note?.let{" · $it"} ?: ""}")
        }
        SectionTitle("События месяца")
        val monthAbsences=filteredAbsences.filter { absence ->
            val start=runCatching{LocalDate.parse(absence.date.take(10))}.getOrNull()
            val end=runCatching{LocalDate.parse((absence.endDate?:absence.date).take(10))}.getOrNull() ?: start
            start!=null && end!=null && !end.isBefore(month.atDay(1)) && !start.isAfter(month.atEndOfMonth()) && absence.status.lowercase()!="rejected"
        }
        val monthReplacements=filteredReplacements.filter { it.date.startsWith(month.toString()) }
        if(monthAbsences.isEmpty()&&monthReplacements.isEmpty()) ListCard("Нет дополнительных событий","Отпусков, больничных и замен на этот месяц нет.")
        monthAbsences.forEach { x ->
            val person=s.employees.firstOrNull{it.id==x.userId}
            ListCard("${if(x.type.lowercase() in listOf("sick_leave","sick","больничный")) "Больничный" else "Отпуск"} · ${person?.lastName ?: "Сотрудник"}","${x.date} — ${x.endDate ?: x.date} · ${statusLabel(x.status)}")
        }
        monthReplacements.forEach { x ->
            val from=s.employees.firstOrNull{it.id==x.employeeId}; val to=s.employees.firstOrNull{it.id==x.replacementEmployeeId}
            ListCard("Замена · ${x.date}","${from?.lastName ?: "Сотрудник"} → ${to?.lastName ?: "Сотрудник"} (${x.startTime}–${x.endTime})")
        }
    }
}

private fun statusLabel(status:String):String = when(status.lowercase()) {
    "approved","confirmed","одобрено" -> "Подтверждено"
    "pending","ожидает" -> "Ожидает согласования"
    "rejected","отклонено" -> "Отклонено"
    "new","новая" -> "Новая"
    "in_progress" -> "В работе"
    "done","completed" -> "Выполнено"
    else -> status
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Text(label, style=MaterialTheme.typography.labelSmall)
    }
}

private fun String.normalizedWeekday(): Int? {
    val value = trim().lowercase(Locale("ru", "RU"))
    return when {
        value.startsWith("пн") || value.startsWith("пон") || value == "monday" -> 1
        value.startsWith("вт") || value.startsWith("вто") || value == "tuesday" -> 2
        value.startsWith("ср") || value.startsWith("сре") || value == "wednesday" -> 3
        value.startsWith("чт") || value.startsWith("чет") || value == "thursday" -> 4
        value.startsWith("пт") || value.startsWith("пят") || value == "friday" -> 5
        value.startsWith("сб") || value.startsWith("суб") || value == "saturday" -> 6
        value.startsWith("вс") || value.startsWith("вос") || value == "sunday" -> 7
        else -> null
    }
}
@Composable
private fun NotificationsScreen(s:UiState,onRead:(String)->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("Уведомления",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold) }
        items(s.notifications) { n ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(n.title,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        if(!n.isRead) AssistChip(onClick={onRead(n.id)},label={Text("Прочитано")})
                    }
                    Text(n.message)
                    n.createdAt?.let { Text(it,style=MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun ModeDialog(mode:AppMode,onDismiss:()->Unit,onMode:(AppMode)->Unit,onLogout:()->Unit,onReset:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Режим приложения")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Выберите источник данных.")
            Button(onClick={onMode(AppMode.DEMO)},modifier=Modifier.fillMaxWidth()){Text("Демо — автономно")}
            OutlinedButton(onClick={onMode(AppMode.SERVER)},modifier=Modifier.fillMaxWidth()){Text("Сервер клиники")}
            if(mode==AppMode.DEMO) OutlinedButton(onClick=onReset,modifier=Modifier.fillMaxWidth()){Text("Сбросить демо-данные")}
            if(mode==AppMode.SERVER) TextButton(onClick=onLogout){Text("Выйти из аккаунта")}
        }},confirmButton={TextButton(onClick=onDismiss){Text("Закрыть")}})
}

@Composable private fun MetricCard(title:String,value:String,modifier:Modifier=Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp)) { Text(value,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(title,style=MaterialTheme.typography.labelSmall) } }
}
@Composable private fun ListCard(title:String,subtitle:String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodyMedium) } }
}
@Composable private fun SectionTitle(value:String) { Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
@Composable private fun DetailLine(title:String,value:String) { Column(Modifier.padding(vertical=8.dp)) { Text(title,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(value) } }
private fun roleName(type:String)=when(type) {
    "doctor"->"Врач";"nurse"->"Медицинская сестра";"accountant"->"Бухгалтер";"admin"->"Администратор"
    "pharmacist"->"Фармацевт";"laboratorist"->"Лаборант";"receptionist"->"Регистратор";else->type
}
