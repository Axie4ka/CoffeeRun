# Favorites Coordinator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Отделить сетевые данные избранного от оптимистического сценария без изменения поведения.

**Architecture:** Репозиторий предоставляет confirmedIds и операции для явно указанной сессии. Общий координатор реализует UI-контракт Favorites и владеет pending/version, последовательностью запросов и временем жизни отправки.

**Tech Stack:** Kotlin, Flow, coroutines, Retrofit; новые зависимости не нужны.

**Spec:** `docs/superpowers/specs/2026-11-10-favorites-coordinator-design.md`

## Global Constraints

- Сохранить оптимистичное обновление и проверки сессии.
- Отдельная ветка, существующие незакоммиченные файлы сохранить, commit/push не выполнять.
- Не менять алгоритм на Channel одновременно с выделением классов.

## Review Focus

- Успех старой отправки при более новом противоположном намерении.
- Поздний ответ после повторного входа с тем же токеном.
- Отмена ожидающего экрана не отменяет принятую отправку.
- Ошибка refresh не очищает подтверждённые данные.
- Все UI-потребители используют единое отображаемое состояние.

## Task 1: Контракты, реализация и подключение

**Files:** `domain/favorites/Favorites.kt`, `domain/favorites/FavoritesCoordinator.kt`, `data/FavoritesRepository.kt`, `data/NetworkFavoritesRepository.kt`; AppContainer, три ViewModel, testFixtures и тестовые потребители.

**Interfaces:** Favorites: ids/refresh/toggle; FavoritesRepository: confirmedIds, refresh(session), setFavorite(productId, selected, session), clear(session).

- [x] Перенести существующие конкурентные тесты на FavoritesCoordinator; запустить и зафиксировать отсутствие реализации.
- [x] Выделить координатор, оставить подтверждённые ID и явные сетевые операции в репозитории.
- [x] Подключить один координатор в Application, обновить UI-контракт и фейки.
- [x] Проверить целевые тесты координатора.

## Task 2: Границы репозитория и регрессии

**Files:** NetworkFavoritesRepositoryTest, FavoritesCoordinatorTest, ROADMAP и настоящий план.

- [x] Проверить отсутствие фоновых запросов из конструктора, подтверждение после ответа, явную установку и старый ответ после выхода.
- [x] Добавить проверку успеха старого намерения при новом противоположном нажатии.
- [x] Запустить полную unit-сюиту, assembleDebug, compileDebugAndroidTestKotlin; целевые UI-тесты при доступном устройстве.
- [x] Выполнить независимое ревью, исправить существенные замечания и записать фактическую проверку в ROADMAP.

## Ledger

- Начало: codex/favorites-coordinator; исходный снимок app/src и docs/*.md сохранён в системном TEMP/coffeerun-favorites-before.zip.
- Ruling: пользователь поручил работать самостоятельно на время отсутствия — план выполняется без повторного запроса согласования.
- Ruling: работаем в текущем checkout на новой ветке, не создаём worktree — нужна актуальная незакоммиченная база; файлы не сбрасываются.
- Ruling: сохраняем алгоритм с Mutex, выделяем ответственность отдельно от замены алгоритма — меньше риск регрессии.
- Ruling: commit/push не выполняются; исходные изменения и новый рефакторинг остаются в рабочем дереве новой ветки.

## Выполнение и проверка

- Task 1: контракты, координатор и подключение выполнены. Существующие 13 конкурентных тестов перенесены на координатор с настоящим сетевым репозиторием. Первый запуск подтвердил отсутствие нового класса; после реализации целевые тесты прошли.
- Task 2: добавлено 8 тестов границы репозитория и тест старого успеха при более новом намерении. Полная unit-сюита: 206/206, failures/errors/skipped = 0.
- `:app:assembleDebug` и `:app:compileDebugAndroidTestKotlin` прошли.
- Независимое read-only ревью сравнило изменения с исходным ZIP, существенных замечаний нет. Неизвестный исход тайм-аута, долговременная очередь и серверный порядок оставлены отдельными задачами, как в spec.
- Добавлены два UI-теста с настоящими FavoritesCoordinator и NetworkFavoritesRepository, задерживается только API. Первый запуск выявил неверное ожидание теста: экран списка ждёт refresh за ожидающей отправкой. В тесте сохранено существующее поведение, проверяется переход между экранами до ответа и появление товара после подтверждения. Тестовый API исправлен так, чтобы GET возвращал действительно добавленные ID.
- Ruling: серверные ограничения не маскируем рефакторингом; откат при IOException и отсутствие долговременной очереди сохранены. Это прежние ограничения.
- Ruling: границы пакетов меняются поэтапно; session-контракты пока остаются на существующих путях, общий перенос domain/data не входит в этот этап.

- Task 1: complete. Task 2: complete.
- Полный `:app:connectedDebugAndroidTest` на Medium_Phone / Android 14: 35/35, без failures/errors/skipped. После уточнения блокировки в тестовом SessionProvider повторно проверены оба новых UI-теста: 2/2.
- Финальная команда `:app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.coffeerun.FavoritesCoordinatorUiTest`: BUILD SUCCESSFUL; unit XML — 206/206, UI XML — 2/2. Полный UI XML сохранён в TEMP/coffeerun-favorites-ui-full-results.xml.
- `git diff --check`: ошибок пробелов нет; Git сообщает только о нормализации LF/CRLF. Gradle предупреждает об устаревшем Unsafe в зависимостях protobuf/DataStore; зависимости не менялись.
- Итог: отдельная ветка codex/favorites-coordinator оставлена с рабочими изменениями; commit/push/merge не выполнялись. Headless-эмулятор, запущенный для проверки, остановлен. Исходные незакоммиченные изменения сохранены.
- Следующий этап — OrdersCoordinator и явные места загрузки заказов; в этой задаче он не реализовывался.
