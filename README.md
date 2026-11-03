# CoffeeRun

Приложение для заказа кофе с самовывозом: меню, настройка напитка, корзина, оформление ко времени, история заказов, избранное, вход и профиль. Android, Kotlin, Jetpack Compose + Material 3.

Данные приходят с REST API. Сервера у проекта нет — его имитирует [Mockoon](https://mockoon.com/) по контракту [docs/openapi.yaml](docs/openapi.yaml).

## Быстрый старт

1. **Мок-сервер.** Установить Mockoon (десктоп) → **Open local environment** → `mock/coffeerun.json` → ▶ Start. Порт 3000. Подробности и демо-данные — в [mock/README.md](mock/README.md).
2. **Приложение.** Открыть проект в Android Studio и запустить конфигурацию `app` на эмуляторе. Debug-сборка ходит на `http://10.0.2.2:3000/` — это `localhost` компьютера, если смотреть из эмулятора.
3. **Вход.** Телефон `+7 999 000-00-00`, пароль `coffee123`.

Без входа можно смотреть меню и собирать корзину. Оформление, заказы, избранное и данные профиля требуют входа: из корзины после входа сразу открывается оформление.

## Сборка и тесты

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew connectedDebugAndroidTest
```

На Windows — `gradlew.bat` вместо `./gradlew`. UI-тестам нужен запущенный эмулятор, Mockoon им не нужен: они работают на репозиториях в памяти.

CI (GitHub Actions, [.github/workflows/android.yml](.github/workflows/android.yml)) на каждый PR в `main` собирает debug и гоняет unit-тесты.

## Стек

| Что | Чем |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose (типобезопасные маршруты) |
| Архитектура | MVVM: экран → ViewModel со `StateFlow<UiState>` → репозиторий за интерфейсом |
| Асинхронность | Kotlin Coroutines: `suspend`-запросы, `viewModelScope`, общий `applicationScope` |
| Сеть | Retrofit 3, OkHttp 5, kotlinx-serialization |
| Картинки | Coil 3 |
| Хранение токена | DataStore Preferences |
| Тесты | JUnit 4, kotlinx-coroutines-test, MockWebServer, Compose UI Test |

## Как устроено

```
ui/            экраны (XxxScreen) и их ViewModel, навигация, тема
  ↓ события вверх, состояние вниз (StateFlow)
data/          репозитории: интерфейс + Network-реализация + InMemory для UI-тестов
  ├─ network/  CoffeeRunApi (Retrofit), DTO, маппинг DTO ↔ модели, AuthInterceptor
  └─ local/    TokenStore (DataStore)
model/         предметные классы: Product, CartLine, Order, User…
```

- **Один контейнер зависимостей** — `AppContainer` в [CoffeeRunApplication.kt](app/src/main/java/ru/coffeerun/CoffeeRunApplication.kt). Приложение собирает его из сетевых репозиториев, UI-тесты — из репозиториев в памяти.
- **Ошибки.** Загрузка экрана: любая сетевая беда (нет связи, HTTP-ошибка, ответ не по контракту) становится `IOException`, и экран показывает «Повторить». Формы и действия получают результат-значение (`AuthResult`, `ProfileSaveResult`, `PlaceOrderResult`) и показывают снэкбар.
- **Авторизация.** Токен хранится в DataStore. `AuthInterceptor` добавляет `Authorization: Bearer …` ко всем запросам, кроме `/auth/*`. 401 на запрос с токеном значит, что токен недействителен: он стирается, и приложение переходит в режим гостя (401 от `/auth/login` — это просто неверный пароль).
- **Состояние аккаунта.** Избранное и заказы живут в `applicationScope`: после входа загружаются, после выхода очищаются.

## Документы

- [docs/openapi.yaml](docs/openapi.yaml) — контракт API.
- [mock/README.md](mock/README.md) — мок-сервер, демо-данные, как показать ошибки.
- [PHOTO_CREDITS.md](PHOTO_CREDITS.md) — источники фотографий.
