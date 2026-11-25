# Place Order Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Перенести сценарий покупки в use case и проверять время единым правилом без молчаливого ASAP.

**Architecture:** ViewModel хранит форму и вызывает PlaceOrderUseCase. Use case снимает корзину с версией сессии, проверяет PickupPolicy, передаёт ожидаемую сессию coordinator и атомарно удаляет отправленные количества. Policy использует часы/пояс кофейни.

**Tech Stack:** Kotlin, coroutines, java.time, StateFlow; без новых библиотек.

**Spec:** `docs/superpowers/specs/2026-11-10-place-order-design.md`

## Global Constraints

- Текущая feature-ветка; сохранить прежние рабочие изменения, не делать commit/push/merge.
- Ошибка/отмена сохраняют корзину, смена сессии не выдаёт старый успех.
- Не добавлять автоматический retry POST; не выдавать демо-расписание за серверное.

## Review Focus

- Смена сессии между снимком, очередью HTTP и удалением корзины.
- Сохранение новых количеств и заново добавленной строки.
- ASAP после закрытия и scheduled на другой день.
- UI и отправка используют один экземпляр policy и один пояс.
- Фейки и Android-тесты не зависят от реального времени запуска.

## Task 1: Сценарий и policy

**Files:** domain/checkout/PlaceOrderUseCase.kt, domain/checkout/{PickupPolicy,CheckoutResult}.kt, AuthRepository/NetworkAuthRepository, Orders/OrdersCoordinator, auth/order fixtures.

- [x] Написать тесты сценария и времени, запустить до реализации.
- [x] Выделить результаты, policy и use case; передавать исходную сессию явно.
- [x] Подключить общий SessionProvider через AuthRepository и обновить фейки.
- [x] Проверить целевые сценарии и предсуществующие тесты заказов/сессии.

## Task 2: ViewModel, UI и проверка

**Files:** CheckoutViewModel/CheckoutScreen, AppContainer, ресурсы, checkout/cart/order tests и ROADMAP.

- [x] Перевести ViewModel на use case и policy, сохранить busy/messages/navigation.
- [x] Заменить старый тест silent-ASAP на отказ с сохранением выбранного времени и корзины.
- [x] Использовать пояс кофейни при отображении времени и в picker; добавить понятное сообщение отказа.
- [x] Запустить unit-suite, APK и UI-набор; независимое ревью, исправление существенных замечаний и запись проверок.

## Ledger

- Ruling: согласованный этап выполняется без повторного запроса разрешения. Предзадачный снимок сохранён в игнорируемом build/review/checkout-before.zip.
- Ruling: обязательный expected session в Orders.place необходим, чтобы старый снимок нельзя было отправить с новым аккаунтом.
- Ruling: реального времени открытия в API нет; opensAt остаётся неизвестным в демо-настройке, B09/F05 не объявляются полностью выполненными.
- Ruling: сохраняем текущую ветку feature/favorites-coordinator и рабочие изменения; commit/push/merge отдельно не разрешены.

## Выполнение и ревью

- Новые policy/use-case тесты написаны до реализации; первый запуск зафиксировал отсутствие PickupPolicy. После переноса существующая unit-сюита, APK и Android-компиляция прошли.
- Старый staleTime_fallsBackToAsapOnPlaceOrder заменён проверкой сохранения времени/корзины и сообщения без отправки.
- AuthRepository расширен SessionProvider; production делегирует прежнему TokenStore, тестовый auth реализует короткую атомарную проверку версии. Прежний синтетический default sessions с generation=0 удалён.
- Кнопка busy, сообщения и событие навигации остались в ViewModel; snapshot/removeOrdered и исходная сессия находятся в use case. Добавлена проверка, что старую сессию нельзя передать в place нового аккаунта.
- Read-only ревью точного ZIP выявило два P2. (1) Время устаревает за Mutex: тест с задержкой refresh и переводом часов воспроизвёл ошибку; проверка перенесена также на границу POST, ранняя проверка use case сохранена. (2) Подтверждение использовало пояс телефона: UI-тест Los Angeles не нашёл ожидаемые Ready by 12:15; timezone resolver из policy теперь общий для подтверждения/списка/деталей.
- Новое предупреждение testFixtures о недоступном ViewModel устранено разделением helpers: testPickupPolicy в fixtures, testCheckout с реальной ViewModel только в src/test. Зависимости не добавлялись.
- Ruling: повторная проверка после очереди принадлежит coordinator как границе отправки; использует ту же policy. Дополнительный тип результата не раскрывает Retrofit или UI.
- Ruling: пояс кофейни должен сохраняться на всём пути покупки, поэтому исправление охватывает подтверждение и историю, а не только CheckoutScreen.
- Ruling: открытие остаётся неизвестным в демонстрационных настройках; при известном открытии policy учитывает приготовление после открытия. Дневное расписание проверяется явно, ночные смены не моделируются.
- Unit-проверка после исправлений: 236/236, errors/failures/skipped = 0. Полный UI-прогон выполняется.

- Task 1: complete. Task 2: complete.
- Финальная команда `:app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest`: BUILD SUCCESSFUL. Unit: 236/236; UI: 39/39 на Medium_Phone / Android 14, failures/errors/skipped = 0. Оба P2 проверены RED → GREEN: истёкшее в очереди scheduled/ASAP не отправляется, а подтверждение/список/детали сохраняют время Moscow при JVM timezone Los Angeles.
- Артефакты проверки: build/review/checkout-final.log и checkout-ui-final-results.xml. Предзадачный ZIP checkout-before.zip сохранён для отделения от прежних незакоммиченных изменений. Новых предупреждений компилятора нет; прежние Unsafe/protobuf не менялись.
- Сохранены все прежние рабочие изменения. Текущая ветка feature/favorites-coordinator. Commit/push/merge не выполнялись; тестовый headless-эмулятор остановлен после проверки.
- Следующий этап — RepeatOrderUseCase и актуальные цены/опции при повторе. B09/F05 остаются частично открыты из-за отсутствия реального расписания/серверной валидации; B05 — из-за идемпотентности и неизвестного исхода POST.
