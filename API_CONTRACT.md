# Контракт мобильного API

Base URL задаётся в `android/app/build.gradle.kts`, должен завершаться на `/api/v1/`.

## Авторизация

`POST auth/login`
```json
{"email":"doctor@clinic.ru","password":"secret"}
```
Ожидается:
```json
{"token":"plain-token-returned-once","user":{"id":1,"first_name":"Иван","last_name":"Иванов","email":"doctor@clinic.ru","type":"doctor"}}
```

## Формат списков
Клиент ожидает объект:
```json
{"data":[{"id":1,"name":"Терапия"}]}
```
Если ваш контроллер возвращает массив напрямую, измените `ApiList<T>` / соответствующий метод Retrofit.

## Сотрудник
Обязательные поля: `id`, `first_name`, `last_name`, `email`, `type`.
Дополнительные: `phone`, `mobile`, `picture`, `specialist`, `medical_degree`,
`biography`, `departments`.

## Расписание
Клиент поддерживает `id`, `user_id`, `day_of_week`, `start_time`, `end_time`,
`date`. Если реальные поля таблицы отличаются, настройте API Resource на Laravel
и отдавайте приведённый формат, не меняя структуру БД.

## Уведомления
Ожидаются `id`, `title`, `message`, `created_at`, `read_at`, `type`.


## Дополнительные данные календаря (v1)

Для календаря персонала мобильное приложение дополнительно запрашивает:

- `GET /api/v1/absences` — отпуска и больничные.
- `GET /api/v1/shift-replacements` — назначения сотрудников на замещающие смены.

Оба метода возвращают стандартную обёртку `{"data": [...]}`.

Пример записи отсутствия:
```json
{
  "id": 1,
  "user_id": 12,
  "date": "2026-11-24",
  "end_date": "2026-11-28",
  "type": "vacation",
  "reason": "Ежегодный отпуск",
  "status": "approved"
}
```

`type`: `vacation` или `sick_leave`. `status`: `approved`, `pending` или `rejected`.

Пример замены:
```json
{
  "id": 1,
  "schedule_id": 25,
  "employee_id": 12,
  "replacement_employee_id": 18,
  "date": "2026-11-20",
  "start_time": "09:00",
  "end_time": "17:00",
  "note": "Подмена на время отсутствия"
}
```

Данные предназначены для просмотра. Мобильное приложение не создаёт и не изменяет кадровые записи.


## Задачи и заявки (текущая версия приложения)

В этой сборке задачи и заявки реализованы в локальном хранилище Room для DEMO и автономной демонстрации. Серверные маршруты для них пока не подключены. Для промышленного режима потребуется согласовать роли, исполнителей, права доступа и API-контракт с Laravel.

Рекомендуемые будущие маршруты:
- `GET /api/v1/tasks`
- `PATCH /api/v1/tasks/{id}/status`
- `GET /api/v1/service-requests`
- `POST /api/v1/service-requests`

Мобильный клиент не отправляет эти запросы на сервер в текущей сборке.

## SERVER-режим: задачи и заявки

- `GET /api/v1/tasks` → `{success,message,data:[...]}`
- `PATCH /api/v1/tasks/{id}/status` body `{"status":"new|in_progress|done"}`
- `GET /api/v1/service-requests` → `{success,message,data:[...]}`
- `POST /api/v1/service-requests` body `{"title":"...","category":"IT|Хозяйственная служба|Администрация","description":"...","location":"..."}`

В Android-клиенте ответы разбираются через `ApiResponse<T>`. SERVER-режим отправляет создание заявки и смену статуса на Laravel, DEMO-режим сохраняет их локально.


## Администрирование
- `POST /api/v1/tasks` — администратор создаёт задачу: `title`, `description`, `assignee_id`, `due_date`, `priority`.
- `PATCH /api/v1/service-requests/{id}/status` — администратор меняет статус заявки: `new`, `in_progress`, `done`, `rejected`.

## Отладка
Встроенный журнал сохраняет локально до 300 записей. HTTP-записи содержат только метод, путь, код ответа и длительность; тела, токены и пароли не логируются.

## Health check
- `GET /api/v1/health` — публичная проверка API и подключения сервера к БД. Возвращает `status`, `application`, `laravel`, `php`, `database`, `timestamp`.
- `GET /api/v1/auth/me` — отдельная проверка действительности Bearer-токена.
