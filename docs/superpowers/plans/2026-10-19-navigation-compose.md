# CoffeeRun: навигация на Navigation Compose

Подробный план для самостоятельной работы: что уже есть, что нужно сделать, теория, архитектура, пошаговая реализация, тесты и вопросы к защите.

Ветка: `feature/navigation-compose`. Исходное состояние — коммит `81a4df3` на `main`.

---

## 1. Что уже есть в проекте

| Что | Где | Состояние |
|---|---|---|
| 7 экранов: Меню, Позиция, Корзина, Оформление, «Заказ принят», Заказы, Профиль | `ui/screens/*.kt` | Готовы. Каждый экран принимает колбэки (`onBack`, `onCheckout`, `onCart`, …) и **сам никуда не переходит** — это правильно, их трогать не придётся |
| Нижняя панель с бейджем корзины | `ui/components/AppNavigationBar.kt`, `enum Tab` | Готова |
| Общее состояние (корзина, заказы) | `ui/AppViewModel.kt` | Одна ViewModel на Activity, переживает поворот |
| **Навигация** | `ui/CoffeeRunApp.kt` | Самописная: две переменные `tab` и `overlay` + ручные `BackHandler` |
| Тесты навигации | — | Нет |

### Как работает текущая навигация

```
tab:     MENU | CART | ORDERS | PROFILE      ← какая вкладка выбрана
overlay: null | PRODUCT | CHECKOUT | PLACED  ← что открыто поверх вкладок
product: Product                             ← аргумент для PRODUCT, отдельной переменной
```

`when (overlay)` решает, какой экран рисовать; `BackHandler` в каждой ветке вручную описывает, что делает кнопка «назад».

**Почему это стоит заменить:**

1. **Нет стека.** Глубина ровно 1: поверх вкладки можно открыть только один экран. Захотите «Позиция → Похожая позиция» — придётся переписывать.
2. **Аргументы живут отдельно от экрана.** `product` хранится в своей переменной; на каждый новый экран с аргументом — новая переменная.
3. **«Назад» описан вручную в 4 местах.** Легко забыть `BackHandler` на новом экране — и кнопка закроет приложение.
4. **Нет deep links, анимаций переходов, predictive back** — всё это даёт библиотека.
5. В самом коде стоит комментарий: *«Когда появятся аргументы и глубокие ссылки, стоит перейти на Navigation Compose»*. Аргумент (`product`) уже появился.

---

## 2. Требования

### Функциональные (поведение должно остаться как сейчас)

| № | Сценарий | Ожидаемое поведение |
|---|---|---|
| F1 | Нажатие на вкладку в нижней панели | Открывается вкладка, пункт подсвечен |
| F2 | «Назад» на вкладке Корзина / Заказы / Профиль | Возврат в Меню |
| F3 | «Назад» в Меню | Приложение закрывается |
| F4 | Повторное нажатие на текущую вкладку | Ничего не меняется, в стеке нет дублей |
| F5 | Тап по позиции в Меню | Открывается экран позиции **этой** позиции, нижней панели нет |
| F6 | «Назад» / стрелка на экране позиции | Возврат в Меню |
| F7 | Кнопка корзины на экране позиции | Вкладка Корзина, экран позиции закрыт |
| F8 | «Оформить» в Корзине | Экран оформления, нижней панели нет; «назад» — в Корзину |
| F9 | «Подтвердить» на оформлении | Экран «Заказ принят»; Оформление **убрано из стека** |
| F10 | «Назад» / «Закрыть» / «В меню» на «Заказ принят» | Меню (а не Оформление с пустой корзиной) |
| F11 | «Мои заказы» на «Заказ принят» | Вкладка Заказы |
| F12 | «Повторить» в Заказах | Позиции в корзине, открыта вкладка Корзина |

### Нефункциональные

| № | Требование |
|---|---|
| N1 | Поворот экрана сохраняет текущий экран и аргумент (открытую позицию) |
| N2 | Маршруты типобезопасны: аргументы проверяет компилятор, а не строки |
| N3 | Экраны (`ui/screens/*`) и `AppViewModel` не меняются |
| N4 | Строки только из ресурсов (`values`, `values-ru`) |
| N5 | Поведение F1–F12 и N1 закреплено автотестами |

---

## 3. Теория

### 3.1. Три главные сущности

- **`NavController`** — «пульт управления». Хранит **back stack** (стек экранов) и умеет `navigate(...)`, `popBackStack()`. Создаётся `rememberNavController()` и сам сохраняет стек при повороте.
- **`NavHost`** — контейнер, который смотрит на вершину стека и рисует соответствующий экран. Внутри описывается **граф навигации**: список пунктов (`composable<Route> { ... }`).
- **Маршрут (route)** — «адрес» экрана. С версии 2.8 это обычный Kotlin-класс с `@Serializable`:

```kotlin
@Serializable data object CartRoute                        // экран без аргументов
@Serializable data class ProductRoute(val product: Product) // экран с аргументом
```

### 3.2. Back stack

Стек — список `NavBackStackEntry`, вершина видна на экране:

```
navigate(ProductRoute(LATTE))   [Menu] → [Menu, Product(LATTE)]
popBackStack()                  [Menu, Product(LATTE)] → [Menu]
```

Системная кнопка «назад» по умолчанию делает `popBackStack()`. Когда в стеке остался один пункт — закрывается Activity. **Поэтому ручные `BackHandler` больше не нужны** — это и даёт F3, F6, F8.

### 3.3. Типобезопасные маршруты и аргументы

- Аргумент — просто поле класса: `navigate(ProductRoute(Product.LATTE))`.
- Получение на экране: `backStackEntry.toRoute<ProductRoute>().product`.
- Под капотом класс сериализуется (плагин `kotlinx.serialization`), поэтому аргумент **переживает поворот и даже смерть процесса**.
- Поддерживаются примитивы, `String`, enum'ы и их nullable-варианты. `Product` — enum, подходит без доработок.
- **Важно:** передаём **идентификатор**, а не большой объект. Корзину / заказ через маршрут не гоняем — их берём из `AppViewModel`.

### 3.4. Опции навигации (`NavOptionsBuilder`)

| Опция | Что делает | Где нужна у нас |
|---|---|---|
| `popUpTo(X)` | Перед переходом снять со стека всё выше `X` | Любой переход на вкладку; «Заказ принят» |
| `popUpTo(X) { inclusive = true }` | Снять и сам `X` | Не нужно |
| `popUpTo(X) { saveState = true }` | Снятые экраны **сохранить**, а не уничтожить | Переключение вкладок в нижней панели |
| `restoreState = true` | Если для цели есть сохранённое состояние — восстановить | Переключение вкладок |
| `launchSingleTop = true` | Если цель уже на вершине — не создавать копию | Вкладки (F4) |

### 3.5. Стандартный рецепт нижней навигации

```kotlin
navController.navigate(tab.route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

Смысл: стек всегда `[Menu]` или `[Menu, ВкладкаX]`. Отсюда F2 («назад» с любой вкладки → Меню) и F4 (нет дублей). `saveState/restoreState` сохраняют, например, прокрутку списка заказов при переключении вкладок.

### 3.6. Как узнать текущую вкладку

```kotlin
val entry by navController.currentBackStackEntryAsState()
val currentTab = Tab.entries.firstOrNull { tab ->
    entry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
}
```

`currentBackStackEntryAsState()` — это `State`, поэтому при каждом переходе Compose перерисует нижнюю панель. `hierarchy` нужен на случай вложенных графов (сейчас их нет, но так принято). `currentTab == null` → открыт экран поверх вкладок → нижнюю панель прячем.

### 3.7. Где живёт состояние

| Состояние | Где | Почему |
|---|---|---|
| Какой экран открыт, аргументы | `NavController` (back stack) | Сохраняется автоматически |
| Корзина, заказы | `AppViewModel` на Activity | Общие для всех экранов |
| Локальное состояние экрана (выбранный размер, молоко) | `rememberSaveable` внутри экрана | Уже так сделано |

`NavBackStackEntry` умеет держать **свою** ViewModel (`viewModel()` внутри `composable {}`), но нам это не нужно: общее состояние одно. Это хороший пункт для защиты: «почему ViewModel одна на Activity».

### 3.8. Scaffold, insets и снекбары

- Один внешний `Scaffold` с `bottomBar`. Нижнюю панель показываем только когда `currentTab != null`.
- `innerPadding` внешнего `Scaffold` отдаём **только экранам-вкладкам**. У экранов Позиция / Оформление свой `Scaffold` и свои отступы.
- У экранов Позиция / Оформление есть свой `SnackbarHost` с тем же `snackbarHostState`. Если внешний `SnackbarHost` тоже показан — сообщение появится дважды. Поэтому внешний `snackbarHost` тоже только при `currentTab != null`.

---

## 4. Альтернативы и выбор

| Вариант | Плюсы | Минусы | Вердикт |
|---|---|---|---|
| **A. Navigation Compose 2.10 + типобезопасные маршруты** | Официальный стандарт, его ждут в учебном задании; стек, аргументы, сохранение вкладок, deep links, predictive back | +1 библиотека, +1 плагин сериализации | **Выбран** |
| B. Navigation Compose со строковыми маршрутами `"product/{name}"` | Не нужен плагин сериализации | Ручной разбор аргументов, опечатка в строке ловится только при запуске; в документации — устаревший способ | Только если плагин не подключается |
| C. Navigation 3 (`navigation3-ui` 1.2.0) | Стек — обычный `SnapshotStateList`, вы им владеете напрямую; ближе всего к текущему коду | Новая библиотека, мало учебных материалов; отдельные стеки вкладок пишутся руками; преподаватель может ожидать `NavHost`/`NavController` | Интересно, но рискованно для сдачи |
| D. Доработать самописную (стек `List<Screen>`) | 0 зависимостей | Это не «сделать навигацию», а дописать свой велосипед; сохранение стека, deep links, анимации — вручную | Нет |

---

## 5. Архитектура

### 5.1. Граф навигации

```
                       NavHost(start = MenuRoute)
 ┌──────────────── вкладки (нижняя панель видна) ────────────────┐
 │  MenuRoute      CartRoute       OrdersRoute      ProfileRoute │
 └────┬───────────────┬────────────────────────────────────▲─────┘
      │ тап по        │ «Оформить»                         │
      │ позиции       ▼                                    │
      ▼          CheckoutRoute ── «Подтвердить» ──►  PlacedRoute
 ProductRoute(product)            popUpTo(Menu)      │   │
      │ кнопка корзины                               │   └─ «Мои заказы» → OrdersRoute
      └──────────► CartRoute                         └───── «Закрыть»/«В меню»/назад → MenuRoute
 (нижняя панель скрыта на Product, Checkout, Placed)
```

### 5.2. Стек в ключевых сценариях

| Сценарий | Стек после |
|---|---|
| Запуск | `[Menu]` |
| Тап «Корзина» | `[Menu, Cart]` |
| Тап «Заказы» (с Корзины) | `[Menu, Orders]` (Cart сохранён) |
| Тап по Латте в Меню | `[Menu, Product(LATTE)]` |
| Кнопка корзины на Латте | `[Menu, Cart]` |
| «Оформить» | `[Menu, Cart, Checkout]` |
| «Подтвердить» | `[Menu, Placed]` ← Cart и Checkout сняты |
| «Мои заказы» | `[Menu, Orders]` |

### 5.3. Файлы

| Файл | Изменение |
|---|---|
| `gradle/libs.versions.toml` | Версия `navigation = "2.10.2"`, библиотека `androidx-navigation-compose`, плагин `kotlin-serialization` |
| `build.gradle.kts` (корень) | `alias(libs.plugins.kotlin.serialization) apply false` |
| `app/build.gradle.kts` | Применить плагин, добавить зависимость |
| `ui/navigation/Routes.kt` | **Новый.** Все маршруты |
| `ui/components/AppNavigationBar.kt` | У `Tab` новое поле `route` |
| `ui/CoffeeRunApp.kt` | Переписать: `NavHost` вместо `when` |
| `androidTest/.../NavigationTest.kt` | **Новый.** UI-тесты |

---

## 6. План работы

Порядок важен: **сначала тесты на старом коде**, потом миграция. Тесты фиксируют текущее поведение; если после миграции они зелёные — вы ничего не сломали. Это называется регрессионные тесты (characterization tests).

### Этап 1. Регрессионные UI-тесты (≈1 ч)

**Файл:** `app/src/androidTest/java/ru/coffeerun/NavigationTest.kt`

Каркас:

```kotlin
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    // Тексты из ресурсов — тест работает и на русской, и на английской локали эмулятора
    private fun str(@StringRes id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    // Пункт нижней панели. Заголовок TopAppBar «Корзина» не кликабелен — так их и различаем
    private fun tab(@StringRes id: Int) = rule.onNode(hasText(str(id)) and hasClickAction())

    private fun assertMenuShown() = rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()

    private fun placeCappuccinoOrder() {
        val name = str(Product.CAPPUCCINO.nameRes)
        rule.onNodeWithContentDescription(str(R.string.add_product, name)).performClick()
        tab(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_checkout)).performClick()
        rule.onNodeWithText(str(R.string.checkout_confirm)).performClick()
    }
}
```

Тесты (по одному на требование):

| Тест | Требование | Суть |
|---|---|---|
| `tab_backReturnsToMenu` | F1, F2 | Тап Корзина → видно `cart_empty` → `Espresso.pressBack()` → Меню |
| `tab_reselectDoesNotStack` | F4 | 3 раза тап Заказы → один `pressBack()` → Меню |
| `product_hidesBottomBarAndBackReturnsToMenu` | F5, F6 | Тап по `Product.CAPPUCCINO.nameRes` → `tab(tab_orders).assertDoesNotExist()` → назад → панель снова есть |
| `productCart_selectsCartTab` | F7 | На позиции клик по `contentDescription = action_cart` → `tab(tab_cart).assertIsSelected()` |
| `placed_backGoesToMenu` | F9, F10 | `placeCappuccinoOrder()` → видно `placed_title` → назад → Меню, `checkout_title` не существует |
| `placed_toOrdersOpensOrdersTab` | F11 | `placeCappuccinoOrder()` → клик `placed_to_orders` → `tab(tab_orders).assertIsSelected()` |
| `recreate_keepsProductScreen` | N1 | Открыть Латте → `rule.activityRule.scenario.recreate()` → панели нет, название Латте видно |

- [ ] Написать тесты
- [ ] Запустить на эмуляторе: `./gradlew :app:connectedDebugAndroidTest`
- [ ] **Все 7 должны быть зелёными на старом коде.** Если какой-то красный — сначала разберитесь: это неверный селектор (чините тест) или баг старого кода (запишите и обсудите)
- [ ] Коммит: `test: регрессионные UI-тесты навигации`

> Совет: если тап по позиции не находит узел (позиция ниже экрана), добавьте `.performScrollTo()` или выберите позицию из первой строки меню.

### Этап 2. Зависимости (≈15 мин)

`gradle/libs.versions.toml`:

```toml
[versions]
navigation = "2.10.2"

[libraries]
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] В корневой `build.gradle.kts`: `alias(libs.plugins.kotlin.serialization) apply false`
- [ ] В `app/build.gradle.kts`: `alias(libs.plugins.kotlin.serialization)` в `plugins {}` и `implementation(libs.androidx.navigation.compose)`
- [ ] `kotlinx-serialization-core` отдельно **не добавлять** — он приходит вместе с navigation
- [ ] Проверка: `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`

> Версия плагина сериализации **обязана** совпадать с версией Kotlin (у нас 2.2.10) — поэтому `version.ref = "kotlin"`.

### Этап 3. Маршруты (≈15 мин)

**Новый файл** `ui/navigation/Routes.kt`:

```kotlin
@Serializable data object MenuRoute
@Serializable data object CartRoute
@Serializable data object OrdersRoute
@Serializable data object ProfileRoute
@Serializable data class ProductRoute(val product: Product)
@Serializable data object CheckoutRoute
@Serializable data object PlacedRoute
```

В `AppNavigationBar.kt` связать вкладку с маршрутом:

```kotlin
enum class Tab(@StringRes val labelRes: Int, val route: Any) {
    MENU(R.string.tab_menu, MenuRoute),
    CART(R.string.tab_cart, CartRoute),
    ORDERS(R.string.tab_orders, OrdersRoute),
    PROFILE(R.string.tab_profile, ProfileRoute)
}
```

- [ ] Сборка проходит

### Этап 4. Переписать `CoffeeRunApp` (≈1–2 ч)

Структура:

```kotlin
@Composable
fun CoffeeRunApp(viewModel: AppViewModel, snackbarHostState: SnackbarHostState) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentTab = /* см. 3.6 */
    val notify: () -> Unit = MaterialTheme.snackbar::notifyPlaceholder

    Scaffold(
        snackbarHost = { if (currentTab != null) SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentTab != null) AppNavigationBar(
                selected = currentTab,
                cartCount = viewModel.cartCount,
                onSelect = navController::openTab
            )
        }
    ) { innerPadding ->
        val tabModifier = Modifier.padding(innerPadding)
        NavHost(navController, startDestination = MenuRoute) {
            composable<MenuRoute> { MenuScreen(..., modifier = tabModifier) }
            composable<CartRoute> { ... }
            composable<OrdersRoute> { ... }
            composable<ProfileRoute> { ProfileScreen(modifier = tabModifier) }
            composable<ProductRoute> { entry ->
                val product = entry.toRoute<ProductRoute>().product
                ProductScreen(product = product, ...)
            }
            composable<CheckoutRoute> { ... }
            composable<PlacedRoute> { ... }
        }
    }
}
```

Две функции перехода на вкладку — **это ключевое решение**, разберитесь, зачем их две:

```kotlin
/** Нижняя панель: переключение вкладок с сохранением их состояния (см. 3.5). */
private fun NavController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Переход на вкладку с экрана поверх вкладок: этот экран закрываем, а не сохраняем. */
private fun NavController.jumpToTab(tab: Tab) = navigate(tab.route) {
    popUpTo(MenuRoute)
    launchSingleTop = true
}
```

> **Почему не одна `openTab` везде?** С «Заказ принят» стек `[Menu, Placed]`. `openTab(ORDERS)` снимет `Placed` с флагом `saveState` — он запомнится как «сохранённое состояние вкладки Меню». Потом пользователь тапнет «Меню», `restoreState` восстановит… экран «Заказ принят». Это баг. С экрана позиции то же самое. Поэтому с экранов поверх вкладок — `jumpToTab` без сохранения.

Таблица колбэков:

| Экран | Колбэк | Действие |
|---|---|---|
| Menu | `onProductClick = { p -> }` | `navController.navigate(ProductRoute(p))` |
| Menu | `onAddProduct` | `viewModel.addToCart(CartLine(it, unitPrice = it.price))` — как было |
| Product | `onBack` | `navController.popBackStack()` |
| Product | `onCart` | `navController.jumpToTab(Tab.CART)` |
| Cart | `onCheckout` | `navController.navigate(CheckoutRoute)` |
| Orders | `onRepeat` | `viewModel.repeat(it)`, затем `navController.openTab(Tab.CART)` (мы на вкладке — сохраняем) |
| Checkout | `onBack` | `navController.popBackStack()` |
| Checkout | `onConfirm` | если `viewModel.placeOrder(Shop.MOSKOVSKAYA) != null` → `navController.navigate(PlacedRoute) { popUpTo(MenuRoute) }` |
| Placed | `onClose`, `onToMenu` | `navController.popBackStack(MenuRoute, inclusive = false)` |
| Placed | `onToOrders` | `navController.jumpToTab(Tab.ORDERS)` |

Удалить:

- [ ] `enum class Overlay`
- [ ] переменные `tab`, `overlay`, `product`
- [ ] все `BackHandler` и импорт `androidx.activity.compose.BackHandler`
- [ ] комментарий «Навигация построена на двух состояниях…» — заменить одной строкой, почему ViewModel общая

Мелочь, но важная: на `PlacedRoute`, если `viewModel.lastOrder == null` (возможно после смерти процесса — ViewModel умерла, а стек восстановился), сейчас рисуется пустой экран. Достаточно:

```kotlin
val order = viewModel.lastOrder
if (order == null) LaunchedEffect(Unit) { navController.popBackStack(MenuRoute, false) }
else OrderPlacedScreen(order = order, ...)
```

- [ ] Сборка проходит
- [ ] Коммит: `feat: навигация на Navigation Compose`

### Этап 5. Проверка (≈30 мин)

- [ ] `./gradlew :app:connectedDebugAndroidTest` — **все 7 тестов зелёные без правок тестов**
- [ ] Вручную на эмуляторе пройти F1–F12
- [ ] Повернуть экран на Позиции, на Оформлении, на вкладке Заказы
- [ ] Прокрутить Заказы → уйти в Профиль → вернуться: прокрутка сохранилась (заслуга `saveState`)
- [ ] Бейдж корзины обновляется
- [ ] Android 14+: жест «назад» показывает predictive-back анимацию
- [ ] Смерть процесса: Developer options → «Don't keep activities», свернуть/развернуть на экране позиции — та же позиция

---

## 7. Типичные ошибки

| Ошибка | Симптом | Как избежать |
|---|---|---|
| Нет `launchSingleTop` | Тап по вкладке 3 раза → «назад» нужно жать 3 раза | Рецепт 3.5 |
| `popUpTo` не туда | «Назад» с вкладки закрывает приложение или водит по кругу | `popUpTo(findStartDestination())` |
| `openTab` с экрана поверх вкладок | Тап «Меню» открывает старый «Заказ принят» | `jumpToTab` (этап 4) |
| Нет `popUpTo(MenuRoute)` при переходе на Placed | «Назад» с «Заказ принят» → Оформление с пустой корзиной | Таблица колбэков |
| `navigate()` прямо в теле composable | Бесконечные переходы, краш | Только в колбэках или `LaunchedEffect` |
| `NavController` передан внутрь экранов | Экраны нельзя превьюить и тестировать отдельно | Экраны получают лямбды — как сейчас |
| Целый объект в маршруте | Краш при сериализации / огромный Bundle | В маршруте только id/enum |
| Версия плагина ≠ версии Kotlin | Ошибка сборки | `version.ref = "kotlin"` |
| Два `SnackbarHost` с одним state | Снекбар показывается дважды | 3.8 |

---

## 8. Вопросы, которые могут задать на защите

1. **Чем `NavController` отличается от `NavHost`?** — Контроллер хранит стек и выполняет переходы, хост рисует экран с вершины стека.
2. **Что такое back stack и что делает системная кнопка «назад»?** — 3.2.
3. **Как передаётся аргумент на экран позиции и почему он переживает поворот?** — 3.3: маршрут сериализуется в `SavedState`.
4. **Зачем `saveState`/`restoreState`/`launchSingleTop`?** — 3.4, 3.5.
5. **Почему экраны не получают `NavController`?** — Разделение ответственности: экран сообщает «пользователь хочет назад», а куда — решает навигационный слой. Экраны остаются переиспользуемыми и тестируемыми.
6. **Почему одна ViewModel на Activity, а не на экран?** — Корзина и заказы общие для всех экранов.
7. **Почему Navigation Compose, а не Navigation 3?** — Раздел 4.
8. **Как бы вы добавили deep link `coffeerun://product/LATTE`?** — `composable<ProductRoute>(deepLinks = listOf(navDeepLink<ProductRoute>(basePath = "coffeerun://product")))` + `intent-filter` в манифесте.

---

## 9. Что сознательно не делаем

- Deep links, вложенные графы, анимации переходов сверх стандартных, отдельные ViewModel на экран — задание этого не требует, а архитектура позволит добавить их позже без переделки.
- Сохранение корзины на диск (смерть процесса её стирает) — это не навигация, отдельная задача.

## 10. Полезные ссылки

- Navigation Compose: https://developer.android.com/develop/ui/compose/navigation
- Типобезопасная навигация: https://developer.android.com/guide/navigation/design/type-safety
- Нижняя навигация и несколько стеков: https://developer.android.com/guide/navigation/backstack/multi-back-stacks
- Тестирование навигации: https://developer.android.com/guide/navigation/testing
