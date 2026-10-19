# Навигация на Navigation Compose — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить самописную навигацию (`tab` + `overlay` в `CoffeeRunApp.kt`) на Navigation Compose с типобезопасными маршрутами, не меняя поведения приложения.

**Architecture:** Один `NavHost` внутри одного `Scaffold`. Четыре вкладки — пункты верхнего уровня (переход с `popUpTo(start) { saveState }` + `restoreState`), экраны Product / Checkout / OrderPlaced — обычные пункты поверх них, нижняя панель скрыта. Аргумент экрана позиции — `Product` (enum) в маршруте `ProductRoute`. `AppViewModel` остаётся общим на Activity, экраны не меняются.

**Tech Stack:** Kotlin 2.2.10, AGP 9.4.1 (встроенный Kotlin), Compose BOM 2026.02.01, `androidx.navigation:navigation-compose:2.10.2`, плагин `org.jetbrains.kotlin.plugin.serialization`, Compose UI tests (`ui-test-junit4`, уже подключён).

**Spec:** задание «сделать навигацию»; текущее поведение — `app/src/main/java/ru/coffeerun/ui/CoffeeRunApp.kt` (коммит `81a4df3`) — и есть спецификация: после миграции всё должно работать так же.

## Что уже сделано (на 81a4df3)

- Все 7 экранов готовы и принимают колбэки навигации (`onBack`, `onCheckout`, `onCart`, …) — менять их не нужно.
- `AppNavigationBar` + `enum Tab` — нижняя панель с бейджем корзины.
- `CoffeeRunApp`: навигация на двух `rememberSaveable`-состояниях (`tab`, `overlay`) + ручные `BackHandler`. Back stack нет: глубина ровно 1, позиция экрана `PRODUCT` хранится отдельной переменной. В коде стоит комментарий «когда появятся аргументы — перейти на Navigation Compose» — аргумент (`product`) уже появился.
- Тестов навигации нет (только `ExampleUnitTest` / `ExampleInstrumentedTest`).

## Альтернативы

| Вариант | Плюсы | Минусы |
|---|---|---|
| **A. Navigation Compose 2.10, типобезопасные маршруты** (выбран) | Стандарт для Compose, то, что обычно ждут в задании; аргументы, back stack, сохранение стека вкладок, deep links «из коробки» | +1 зависимость, +1 плагин (serialization) |
| B. Navigation Compose со строковыми маршрутами (`"product/{name}"`) | Без плагина serialization | Ручной разбор аргументов, опечатки в строках ловятся только в рантайме; в документации помечено как устаревший подход |
| C. Navigation 3 (`navigation3-ui` 1.2.0) | Back stack — свой `SnapshotStateList`, очень близко к текущему коду | Новее, меньше материалов; для вкладок с отдельными стеками код пишется вручную; преподаватель может ждать `NavHost`/`NavController` |
| D. Оставить как есть, добавить стек `List<Screen>` | 0 зависимостей | Это не «сделать навигацию», а дописать велосипед; save/restore стека и deep links — вручную |

## Global Constraints

- Версии: `navigation-compose = "2.10.2"`, плагин serialization — `version.ref = "kotlin"` (2.2.10). `kotlinx-serialization-core` приходит транзитивно из navigation — отдельно не добавлять.
- Экраны в `ui/screens/*` и `AppViewModel` не менять.
- Строки — только через ресурсы (есть `values` и `values-ru`); тесты берут тексты через `activity.getString(...)`, чтобы не зависеть от локали эмулятора.
- Комментарии в коде — по-русски, как в проекте.

## Review Focus

1. Поворот экрана / пересоздание Activity на экране позиции — должна остаться та же позиция и тот же экран (тест `recreate_keepsProductScreen`, Task 2).
2. «Назад» с OrderPlaced ведёт в меню, а не обратно на Checkout с пустой корзиной (тест `placed_backGoesToMenu`, Task 1).
3. «Назад» с любой вкладки, кроме Меню, ведёт в Меню, а из Меню — закрывает приложение (тест `tab_backReturnsToMenu`, Task 1).
4. Повторное нажатие на текущую вкладку не плодит копии в стеке (`launchSingleTop`; тест `tab_reselectDoesNotStack`, Task 1).
5. Переход «В корзину» с экрана позиции и «Повторить» в Заказах подсвечивают вкладку Корзина в нижней панели (тест `productCart_selectsCartTab`, Task 1).

---

### Task 1: Регрессионные UI-тесты навигации на текущем коде

Тесты пишутся **до** миграции и должны пройти на текущей реализации — они фиксируют поведение, которое Task 2 обязан сохранить.

**Files:**
- Create: `app/src/androidTest/java/ru/coffeerun/NavigationTest.kt`

**Interfaces:**
- Produces: тестовый класс `NavigationTest` с `@get:Rule val rule = createAndroidComposeRule<MainActivity>()` и хелперами
  `str(@StringRes id: Int, vararg args: Any): String = rule.activity.getString(id, *args)`,
  `tab(@StringRes id: Int) = rule.onNode(hasText(str(id)) and hasClickAction())` (у заголовка TopAppBar с тем же текстом нет click action),
  `addCappuccinoAndCheckout()` — клик по `contentDescription = str(R.string.add_product, str(Product.CAPPUCCINO.nameRes))`, `tab(R.string.tab_cart).performClick()`, клик по `str(R.string.cart_checkout)`, клик по `str(R.string.checkout_confirm)`.
  «Назад» — `Espresso.pressBack()`; проверка «открыто меню» — `onNodeWithText(str(R.string.app_name)).assertIsDisplayed()`.

- [ ] **Step 1: Написать тесты**

```kotlin
@Test fun tab_backReturnsToMenu() {
    tab(R.string.tab_cart).performClick()
    rule.onNodeWithText(str(R.string.cart_empty)).assertIsDisplayed()
    Espresso.pressBack()
    rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
}
@Test fun tab_reselectDoesNotStack() {
    repeat(3) { tab(R.string.tab_orders).performClick() }
    Espresso.pressBack()
    rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
}
@Test fun product_hidesBottomBarAndBackReturnsToMenu() {
    rule.onNodeWithText(str(Product.CAPPUCCINO.nameRes)).performClick()
    tab(R.string.tab_orders).assertDoesNotExist()
    Espresso.pressBack()
    tab(R.string.tab_orders).assertIsDisplayed()
}
@Test fun productCart_selectsCartTab() {
    rule.onNodeWithText(str(Product.CAPPUCCINO.nameRes)).performClick()
    rule.onNodeWithContentDescription(str(R.string.action_cart)).performClick()
    tab(R.string.tab_cart).assertIsSelected()
}
@Test fun placed_backGoesToMenu() {
    addCappuccinoAndCheckout()
    rule.onNodeWithText(str(R.string.placed_title)).assertIsDisplayed()
    Espresso.pressBack()
    rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
    rule.onNodeWithText(str(R.string.checkout_title)).assertDoesNotExist()
}
@Test fun placed_toOrdersOpensOrdersTab() {
    addCappuccinoAndCheckout()
    rule.onNodeWithText(str(R.string.placed_to_orders)).performClick()
    tab(R.string.tab_orders).assertIsSelected()
}
```

- [ ] **Step 2: Запустить на эмуляторе — все зелёные**

Run: `./gradlew :app:connectedDebugAndroidTest --tests ru.coffeerun.NavigationTest`
Expected: `6 tests completed, 0 failed`. Если какой-то красный — это баг текущего кода или неверный селектор; поправить селектор, поведение не трогать.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/ru/coffeerun/NavigationTest.kt
git commit -m "test: регрессионные UI-тесты навигации"
```

---

### Task 2: Миграция `CoffeeRunApp` на NavHost

**Files:**
- Modify: `gradle/libs.versions.toml` — `navigation = "2.10.2"`, библиотека `androidx-navigation-compose`, плагин `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`
- Modify: `build.gradle.kts` (корень) — `alias(libs.plugins.kotlin.serialization) apply false`
- Modify: `app/build.gradle.kts` — плагин + `implementation(libs.androidx.navigation.compose)`
- Create: `app/src/main/java/ru/coffeerun/ui/navigation/Routes.kt`
- Modify: `app/src/main/java/ru/coffeerun/ui/components/AppNavigationBar.kt` — у `Tab` поле `route`
- Modify: `app/src/main/java/ru/coffeerun/ui/CoffeeRunApp.kt` — полностью переписать тело
- Test: `app/src/androidTest/java/ru/coffeerun/NavigationTest.kt`

**Interfaces:**
- Consumes: `NavigationTest` из Task 1.
- Produces (`Routes.kt`, все `@Serializable`): `data object MenuRoute`, `data object CartRoute`, `data object OrdersRoute`, `data object ProfileRoute`, `data class ProductRoute(val product: Product)`, `data object CheckoutRoute`, `data object PlacedRoute`.
- `enum class Tab(@StringRes val labelRes: Int, val route: Any)` — `MENU(…, MenuRoute)` и т. д.
- `private fun NavController.openTab(tab: Tab)` в `CoffeeRunApp.kt`.

- [ ] **Step 1: Добавить тест на пересоздание Activity (сейчас должен пройти — `rememberSaveable`)**

```kotlin
@Test fun recreate_keepsProductScreen() {
    rule.onNodeWithText(str(Product.LATTE.nameRes)).performClick()
    rule.activityRule.scenario.recreate()
    tab(R.string.tab_orders).assertDoesNotExist()
    rule.onNodeWithText(str(Product.LATTE.nameRes)).assertIsDisplayed()
}
```

- [ ] **Step 2: Подключить зависимости и синхронизировать**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Создать `Routes.kt`, добавить `Tab.route`**

- [ ] **Step 4: Переписать `CoffeeRunApp`**

Решения, которые нельзя менять:
- `val navController = rememberNavController()`; `val entry by navController.currentBackStackEntryAsState()`; текущая вкладка — `Tab.entries.firstOrNull { t -> entry?.destination?.hierarchy?.any { it.hasRoute(t.route::class) } == true }`; `null` ⇒ экран поверх вкладок.
- Один `Scaffold`: `bottomBar` и `snackbarHost` показываются только когда вкладка не `null` (у Product/Checkout свой `SnackbarHost` с тем же `snackbarHostState` — два хоста одновременно дадут дубль). `innerPadding` передаётся только экранам-вкладкам.
- `NavHost(navController, startDestination = MenuRoute)`; `composable<ProductRoute> { ProductScreen(product = it.toRoute<ProductRoute>().product, …) }`.
- `openTab`:
  ```kotlin
  navigate(tab.route) {
      popUpTo(graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
  }
  ```
- Переходы: Menu→`navigate(ProductRoute(p))`; Product.onCart/Orders.onRepeat/Placed.onToOrders→`openTab(Tab.CART | Tab.ORDERS)`; Cart.onCheckout→`navigate(CheckoutRoute)`; Checkout.onConfirm (если `placeOrder` ≠ null)→`navigate(PlacedRoute) { popUpTo(MenuRoute) }`; `onBack`→`popBackStack()`; Placed.onClose/onToMenu→`popBackStack(MenuRoute, inclusive = false)`.
- Удалить `enum Overlay`, все `BackHandler`, переменные `tab`/`overlay`/`product` и комментарий про «два состояния».

- [ ] **Step 5: Прогнать все тесты**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: `NavigationTest` — 7 tests, 0 failed.

- [ ] **Step 6: Ручная проверка на эмуляторе**

Нижняя панель на всех вкладках; бейдж корзины обновляется; Cart → прокрутка/состояние сохраняется при уходе на другую вкладку и возврате; системный жест «назад» с анимацией predictive back работает на Android 14+.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/java/ru/coffeerun/ui app/src/androidTest
git commit -m "feat: навигация на Navigation Compose с типобезопасными маршрутами"
```
