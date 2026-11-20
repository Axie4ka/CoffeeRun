# Order details single source Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for this approved step. Progress is recorded below. No commits, push or PR without explicit user approval.

**Goal:** Детали заказа наблюдают данные репозитория и больше не хранят отдельную копию `fresh`.

**Architecture:** Добавить `OrderRepository.observeOrder(number: Int): Flow<Order?>` с реализацией через общий список по умолчанию. ViewModel преобразует поток в экранный StateFlow; результат load/cancel не хранится в отдельной модели. Загрузка одного заказа вставляет или обновляет запись в общем списке; повторный retry отменяет предыдущую загрузку.

**Tech Stack:** Существующие Kotlin, Flow, ViewModel, Retrofit и JUnit; Room появится в ЛР6.

**Spec:** Согласованный второй шаг из чата, `docs/ROADMAP.md` B04/A01.

## Global Constraints

- Сохранить изменения первого шага с OrderItem и пользовательские правки.
- Работать в текущей ветке; новые ветки и зависимости не нужны.
- Сохранить сообщения о загрузке, сетевой ошибке и отмене. 404 закрывает детали; отсутствующий на сервере заказ удаляется и из общего кеша.
- Этот шаг не решает все гонки refresh/place/cancel и смены сессии: они остаются отдельными задачами B01/B03.
- Контракт API, Mockoon и архитектура повторения заказа не меняются.

## Review Focus

- Изменение статуса в репозитории обновляет уже загруженные детали, в том числе после TooLate.
- Очистка общего списка убирает отображаемый заказ.
- load вставляет отсутствующий заказ без дублей и с сохранением сортировки от новых к старым.
- Повторный retry не допускает, чтобы отменённая загрузка вернула старое состояние экрана.
- Ошибка сети сохраняет уже имеющиеся данные; 404 не оставляет устаревшую запись в кеше.

## Task 1: Регрессионные проверки

**Files:** `app/src/test/java/ru/coffeerun/ui/orders/OrderDetailsViewModelTest.kt`, `app/src/test/java/ru/coffeerun/data/NetworkOrderRepositoryTest.kt`.

- [x] Проверить исходное состояние unit-тестов (`BUILD SUCCESSFUL`, Gradle up-to-date).
- [x] Исправить фейк так, чтобы load/cancel обновляли общий список согласно контракту.
- [x] Подтвердить красными тесты обновления/очистки деталей, TooLate, retry и загрузки отсутствующего заказа.

## Task 2: Единый источник и проверка

**Files:** `data/OrderRepository.kt`, `data/NetworkOrderRepository.kt`, `ui/orders/OrderDetailsViewModel.kt`, тесты и `docs/ROADMAP.md`.

**Interfaces:** `observeOrder(number: Int): Flow<Order?>`; существующие сигнатуры load/find/cancel сохраняются.

- [x] Добавить поток одного заказа и подключить ViewModel без fresh.
- [x] Согласовать load с общим списком, 404 и отменой coroutine.
- [x] Отменять предыдущий retry; снимать флаг отмены заказа в finally.
- [x] Запустить все unit-тесты, собрать debug и скомпилировать Android-тесты.
- [x] Проверить обновление статуса на открытом экране через целевой UI-тест.
- [x] Выполнить независимое read-only review и проверить diff.

## Progress

- Пользователь согласовал второй шаг после завершения первого. Коммит первого шага не создавался.
- Для Gradle используется процессный JAVA_TOOL_OPTIONS с jdk.net.unixdomain.tmpdir в `.gradle/uds`; настройки проекта/Windows не меняются.
- RED: 6 новых сценариев упали по ожидаемым причинам на прежней реализации (24 теста в двух целевых классах).
- GREEN: 155/155 unit-тестов, `assembleDebug`, `compileDebugAndroidTestKotlin`; 3/3 UI-теста OrderHistoryTest, включая обновление открытых деталей.
- Read-only review: замечаний в согласованном scope нет. Отложены B01/B03. Зафиксирован остаточный риск B01: неотменённый load прежней сессии может повторно заполнить очищенный кеш; новая вставка расширяет последствия прежней гонки. Следующим шагом рекомендована привязка запросов к сессии.
- Делегированный fake в OrderHistoryTest явно переопределяет observeOrder, чтобы наблюдать собственный historyState, а не состояние делегата.
- Контракт API, Mockoon, зависимости и пользовательские правки не менялись. Коммит, push и PR не создавались.
