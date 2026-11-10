# Orders Coordinator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Выделить согласование заказов из NetworkOrderRepository и заменить автозагрузку явными потребителями.

**Architecture:** Общий OrdersCoordinator реализует Orders, делегирует чтение кеша репозиторию и согласует сетевые методы. Repository получает AuthSession в каждом запросе и защищает применение ответа.

**Tech Stack:** Kotlin, coroutines, StateFlow, Retrofit; зависимости без изменений.

**Spec:** `docs/superpowers/specs/2026-11-10-orders-coordinator-design.md`

## Global Constraints

- Сохранить согласованность списка/деталей и принадлежность данных сессии.
- Не менять оформление, повтор, серверный контракт и алгоритмы других подсистем.
- Не делать commit/push/merge; сохранить прежние рабочие изменения.

## Review Focus

- Параллельные refresh делят запрос; отмена одного ожидающего не мешает другим.
- Новый вход не ждёт неотменяемый ответ старого поколения.
- 409 отмены обновляет список без повторного захвата Mutex.
- Частичный кеш после place/load не выдаётся за полную историю в профиле.
- Профиль после ошибки может повторить загрузку; гость не делает персональные запросы.

## Task 1: Репозиторий и coordinator

**Files:** `domain/orders/Orders.kt`, `domain/orders/OrdersCoordinator.kt`, `data/OrderRepository.kt`, `data/NetworkOrderRepository.kt`, тесты обоих классов.

**Interfaces:** Orders: orders/isLoaded, refresh/place/cancel/load/find/observeOrder; OrderRepository: те же сетевые методы с обязательной session, clear(session).

- [x] Перенести тесты гонок на новый coordinator; зафиксировать отсутствие реализации.
- [x] Выделить сетевые операции и кеш, включая isLoaded; 409 refresh оставить coordinator.
- [x] Реализовать coordinator с одним Mutex операций на сессию и общим Deferred refresh под SessionProvider.
- [x] Проверить отсутствие автозагрузки и явные загрузки после входа.

## Task 2: Потребители и проверка

**Files:** AppContainer, ViewModel заказов/деталей/оформления/профиля/принятого заказа, ProfileScreen, fixtures и их потребители, ROADMAP.

- [x] Переключить UI и фейки на Orders; сетевой репозиторий UI не передавать.
- [x] Добавить явную загрузку полного счётчика профиля, nullable-счётчик и проверки ошибки/retry/гостя.
- [x] Проверить полную unit-сюиту, APK и UI-тесты с реальным coordinator.
- [x] Независимое read-only ревью точного diff этапа; исправить существенные замечания, обновить ROADMAP.

## Ledger

- Ruling: пользователь поручил продолжать согласованный этап — дополнительное согласование не запрашивается.
- Ruling: используем текущую feature/favorites-coordinator; не переносим незакоммиченные изменения в новый checkout. Предзадачный ZIP: TEMP/coffeerun-orders-before.zip (sandbox TEMP).
- Ruling: общий refresh сохраняется из-за двух явных потребителей и гарантии отмены ожидающего; отдельный loading Mutex удаляется.
- Ruling: сохраняем исторические модели и текущую зависимость загрузки от меню; B08 не расширяет структурный этап.

## Проверка и ревью

- Task 1: реализация и перенос конкурентных тестов выполнены. Первый тестовый запуск зафиксировал отсутствие OrdersCoordinator; после выделения все тесты coordinator/repository прошли.
- Task 2: UI и фейки используют Orders; профиль явно загружает неизвестную историю. Четыре теста профиля сначала упали на отсутствии загрузки, затем прошли после подключения.
- Дополнительный тест воспроизвёл гонку: уведомление новой сессии приходит раньше сброса isLoaded. Профиль теперь реагирует также на сброс isLoaded; RED → GREEN.
- Read-only reviewer сравнил ядро заказов с сохранённым ранее TEMP/coffeerun-favorites-before.zip (избранное не меняло эти файлы). Предзадачный ZIP в sandbox TEMP оказался недоступен из следующего вызова; не используется как долговременный артефакт ревью.
- Ревью: один P2 — при успешном /me и ошибке /orders UI не давал повторить загрузку. Настоящий coordinator/repository в OrderSessionTest воспроизвёл проблему: ожидание кнопки Повторить истекло. Добавлены отдельный флаг ошибки счётчика и действие Повторить в профиле; проверка включена в финальный UI-прогон.
- Других конкретных замечаний по Mutex, Deferred, смене поколения, записи кеша или подключению потребителей ревью не выявило. Реальный сервер, B05 и B08 остаются вне этого этапа.
- Финальная unit-сюита: 218/218, failures/errors/skipped = 0; debug APK собран, Android-тесты скомпилированы. Полный UI-прогон ещё выполняется.
- Ruling: для отказа загрузки счётчика нужен доступный повтор в профиле, а не только метод ViewModel; используется существующий локализованный текст и кнопка, новых зависимостей/ресурсов нет.

- Первый полный UI-прогон: 36/37 прошли, включая новый доступный повтор счётчика. Упавший sameTokenNewLogin_clearsOldNavigation ожидал старую автозагрузку после нового входа. Тест обновлён под согласованное поведение: сначала кеш/навигация очищены, затем пользователь явно открывает заказы и видит новый статус. Производственный код для этого не менялся.

- Task 1: complete. Task 2: complete.
- Финальная команда `:app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` — BUILD SUCCESSFUL. Unit: 218/218. Полный UI-набор на Medium_Phone / Android 14: 37/37, failures/errors/skipped = 0. В том числе проверка P2 с Повторить и явная загрузка после одинакового токена.
- Фактические результаты: `build/review/orders-final-check.log`, `build/review/orders-ui-final-results.xml`; файлы находятся в игнорируемом build. `git diff --check` — без ошибок пробелов. Предупреждения Unsafe в зависимостях не менялись этим этапом.
- Исходные рабочие изменения сохранены; feature/favorites-coordinator остаётся текущей веткой. Commit/push/merge не выполнялись. Запущенный для проверки headless-эмулятор остановлен после тестов.
- Следующий этап: PlaceOrderUseCase + PickupPolicy; затем RepeatOrderUseCase. Серверная идемпотентность, независимость истории от меню и Room остаются отдельными задачами.
