# CoffeeRun: редизайн по Material Design 3 (вариант 3)

**Статус:** дизайн утверждён 2026-10-20, спецификация ждёт проверки.
**Макет:** [Figma — CoffeeRun, редизайн M3 (было / стало)](https://www.figma.com/design/08JN9rdwNWglyWG1KHMOAa). Источник истины — колонки **«Вариант 3»** и **«Вариант 3 · тёмная»**, а также коллекции переменных `CoffeeRun / Вариант 3` и `CoffeeRun / Вариант 3 · тёмная`.
**Ветка:** `feature/redesign-m3`, ответвлена от `feature/navigation-compose`. Навигация уже на Navigation Compose, тесты `NavigationTest` зелёные.

## 1. Цель

Привести 7 экранов приложения к Material Design 3, сохранив фирменный «кофейный» характер:
- пользователь видит фирменную палитру, а не цвета обоев;
- у цветов понятные роли;
- выразительный шрифт стоит только там, где это бренд;
- одно и то же действие везде выглядит одинаково;
- есть полноценная тёмная тема.

**Успех:**
- приложение на Android 12+ показывает палитру варианта 3;
- светлая и тёмная темы совпадают с макетом;
- все тесты `NavigationTest` и новые тесты из раздела 9 зелёные;
- контраст текста не ниже WCAG AA (4.5:1 для обычного текста).

## 2. Ключевые решения

| # | Решение | Почему |
|---|---|---|
| D1 | `dynamicColor` по умолчанию `false` | Сейчас на Android 12+ палитра подменяется цветами обоев, и бренд не виден |
| D2 | Палитра — тональная схема M3 из кофейного цвета: primary `#6E3B1C`, контейнеры светлые (тон ~90) | Старые primary `#3A1500` и primaryContainer `#5B2500` почти чёрные: кнопки чёрные, цены не выделяются |
| D3 | Три акцента: **primary** — действия, **secondary** — выбор, **tertiary** (карамель `#8A4F2A`) — цены, итоги, статусы | Каждому цвету одна понятная роль; tertiary больше не оливковый |
| D4 | Бейдж корзины — `tertiaryFixedDim` / `onTertiaryFixed`: одинаковый в светлой и тёмной теме | Фиксированные роли M3 для акцента, который не должен меняться с темой. В Material 3 1.4.0 они есть в `ColorScheme` |
| D5 | Шрифты: **Yeseva One** — бренд (логотип, заголовки экранов, название позиции, цены, номер заказа); **Nunito** — всё остальное. Playfair Display удаляется | Утверждено в варианте 3 |
| D6 | Заголовок экрана набран Yeseva One 24 и стоит в обычной верхней панели 64dp (`TopAppBar`), без Medium top app bar | Medium-панель давала пустой зазор сверху на экранах без «назад» |
| D7 | Один компонент `QuantityStepper` (40dp) на экране позиции и в корзине; выключенная «−» получает обводку `outline` | Сейчас 32dp в корзине и 40dp на позиции. Без обводки выключенная кнопка сливается с тёмным фоном |
| D8 | Разделы меню — вкладки (`PrimaryTabRow`), а не фильтр-чипы | Для равноправных разделов в M3 есть вкладки |

## 3. Палитра

Значения ниже совпадают с переменными Figma. Роли, которых нет в Figma (`onSecondary`, `onError`, `errorContainer`, `inverse*`, `surfaceDim`/`Bright`, `scrim`, `surfaceVariant`, `primaryFixed*`, `secondaryFixed*`), достроены по тональной логике M3. При необходимости их можно сверить в Material Theme Builder с тем же исходным цветом.

### 3.1. Светлая (`lightScheme`)

| Роль | Цвет | Роль | Цвет |
|---|---|---|---|
| primary | `#6E3B1C` | onPrimary | `#FFFFFF` |
| primaryContainer | `#FFDBC9` | onPrimaryContainer | `#4A2208` |
| secondary | `#765849` | onSecondary | `#FFFFFF` |
| secondaryContainer | `#FFDBCB` | onSecondaryContainer | `#5C4033` |
| tertiary | `#8A4F2A` | onTertiary | `#FFFFFF` |
| tertiaryContainer | `#FFDDB3` | onTertiaryContainer | `#5F3A00` |
| error | `#BA1A1A` | onError | `#FFFFFF` |
| errorContainer | `#FFDAD6` | onErrorContainer | `#93000A` |
| background / surface | `#FFF8F5` | onBackground / onSurface | `#221A15` |
| surfaceVariant | `#F4DED4` | onSurfaceVariant | `#52443C` |
| outline | `#85736B` | outlineVariant | `#D7C2B8` |
| surfaceContainerLowest | `#FFFFFF` | surfaceContainerLow | `#FEF1EA` |
| surfaceContainer | `#F8EBE4` | surfaceContainerHigh | `#F2E5DE` |
| surfaceContainerHighest | `#ECE0D9` | surfaceDim / surfaceBright | `#E8D6CF` / `#FFF8F5` |
| inverseSurface | `#382E2A` | inverseOnSurface | `#FFEDE6` |
| inversePrimary | `#FFB68F` | scrim | `#000000` |

### 3.2. Тёмная (`darkScheme`)

| Роль | Цвет | Роль | Цвет |
|---|---|---|---|
| primary | `#FFB68F` | onPrimary | `#4E2300` |
| primaryContainer | `#6E3B1C` | onPrimaryContainer | `#FFDBC9` |
| secondary | `#E7BEAA` | onSecondary | `#44291D` |
| secondaryContainer | `#5C4033` | onSecondaryContainer | `#FFDBCB` |
| tertiary | `#F2BD7C` | onTertiary | `#4A2800` |
| tertiaryContainer | `#663E00` | onTertiaryContainer | `#FFDDB3` |
| error | `#FFB4AB` | onError | `#690005` |
| errorContainer | `#93000A` | onErrorContainer | `#FFDAD6` |
| background / surface | `#1A120E` | onBackground / onSurface | `#F0DFD8` |
| surfaceVariant | `#52443C` | onSurfaceVariant | `#D7C2B8` |
| outline | `#A08D84` | outlineVariant | `#52443C` |
| surfaceContainerLowest | `#140D09` | surfaceContainerLow | `#221A16` |
| surfaceContainer | `#271E1A` | surfaceContainerHigh | `#322824` |
| surfaceContainerHighest | `#3D332E` | surfaceDim / surfaceBright | `#1A120E` / `#42372F` |
| inverseSurface | `#F0DFD8` | inverseOnSurface | `#382E2A` |
| inversePrimary | `#6E3B1C` | scrim | `#000000` |

### 3.3. Фиксированные роли (одинаковые в обеих схемах)

| Роль | Цвет | Роль | Цвет |
|---|---|---|---|
| primaryFixed | `#FFDBC9` | primaryFixedDim | `#FFB68F` |
| onPrimaryFixed | `#331200` | onPrimaryFixedVariant | `#6E3B1C` |
| secondaryFixed | `#FFDBCB` | secondaryFixedDim | `#E7BEAA` |
| onSecondaryFixed | `#2C160C` | onSecondaryFixedVariant | `#5C4033` |
| tertiaryFixed | `#FFDDB3` | tertiaryFixedDim | `#F2BD7C` |
| onTertiaryFixed | `#2B1700` | onTertiaryFixedVariant | `#663E00` |

### 3.4. Ключевой контраст (по расчёту)

- Белый на primary `#6E3B1C`: около 9:1.
- `#4A2208` на primaryContainer `#FFDBC9`: выше 9:1.
- `#5F3A00` на tertiaryContainer `#FFDDB3`: около 7.8:1.
- Tertiary `#8A4F2A` на фоне `#FFF8F5`: около 6.5:1. Цены — крупный текст (18–24sp), запас по AA большой.
- В тёмной теме: tertiary `#F2BD7C` на `#1A120E` — выше 10:1.

### 3.5. Схемы среднего и высокого контраста

`mediumContrast*`/`highContrast*` в `Theme.kt` сейчас не используются. Они удаляются вместе со старыми значениями в `Color.kt`. Регенерировать их не входит в задачу.

## 4. Где какой цвет (правило трёх акцентов)

| Роль | Где |
|---|---|
| primary | Кнопки `Button`, активная вкладка и индикатор в `PrimaryTabRow`, логотип «CoffeeRun», номер заказа на «Заказ принят» |
| secondary / secondaryContainer | Фильтр-чипы (молоко, добавки), сегменты размера, индикатор в нижней навигации, тональные кнопки «+» и «−». Это стандартные цвета компонентов M3, переопределять их не нужно |
| tertiary | Текст всех цен и итогов, значок геолокации (меню, оформление, «Заказ принят»), иконки «время» и «адрес» на «Заказ принят», цифры статистики в профиле |
| tertiaryContainer / onTertiaryContainer | Статус «Готовится», круг иконки успеха, аватар в профиле |
| tertiaryFixedDim / onTertiaryFixed | Бейдж количества на значке корзины: нижняя навигация и верхняя панель экрана позиции |

Добавки на экране позиции перестают красить выбранные чипы в tertiary (`ExtrasSection`). Все чипы выбора используют стандартный secondary.

## 5. Типографика

| Роль M3 | Шрифт | Размер / строка | Где используется |
|---|---|---|---|
| display*, headline* | **Yeseva One** Regular | M3 по умолчанию | Логотип (headlineMedium), заголовки экранов и название позиции (headlineSmall), «Заказ принят!» и номер заказа (headlineMedium), цифры статистики (headlineSmall) |
| titleLarge | Nunito SemiBold | 22/28 | Имя в профиле, прочие заголовки 22sp |
| titleMedium, titleSmall, label* | Nunito SemiBold | M3 | Как сейчас |
| body* | Nunito Regular | M3 | Как сейчас |
| **priceSmall** (новый) | Yeseva One | 18/24 | Цена в карточке меню, цена строки корзины, сумма в карточке заказа |
| **priceLarge** (новый) | Yeseva One | 24/32 | Цена на экране позиции, «Итого» в корзине и на оформлении |

`priceSmall`/`priceLarge` — свойства-расширения `Typography` в `Type.kt`. Отдельные поля в `Typography` в M3 не добавить, поэтому так:

```kotlin
val Typography.priceSmall: TextStyle get() = …
val Typography.priceLarge: TextStyle get() = …
```

Ресурсы:
- добавить `res/font/yeseva_one_regular.ttf` (Google Fonts, SIL Open Font License 1.1, кириллица есть);
- удалить `res/font/playfair_display_bold.ttf`;
- лицензию шрифта записать в `PHOTO_CREDITS.md` (раздел «Fonts») или в отдельный `FONT_CREDITS.md`.

## 6. Компоненты

### 6.1. `ScreenTopBar` (изменить)

- Заголовок набирается стилем `headlineSmall` (Yeseva One 24/32) вместо `titleLarge`.
- Это обычный `TopAppBar`, высота 64dp.
- Используется на Корзине, Оформлении, Заказах и Профиле.

### 6.2. `QuantityStepper` (новый, `ui/components/QuantityStepper.kt`)

```kotlin
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
)
```

- Две кнопки `FilledTonalIconButton` стандартного размера 40dp. Между ними число стилем `titleMedium`, минимальная ширина 32dp, отступы 8dp.
- Выключенная кнопка: стандартный disabled-стиль M3 плюс обводка `1.dp` цвета `outline` по форме кнопки.
- Подписи для TalkBack — существующие строки `action_decrease` и `action_increase`.
- Заменяет счётчик в `OrderBar` (экран позиции) и в `CartItemCard` (корзина). Границы задают экраны: на позиции 1…10, в корзине минимум 1.

### 6.3. `CartBadge` (общий бейдж корзины)

Бейдж с числом порций сейчас есть в двух местах: `AppNavigationBar` и `ProductTopBar`. Оба переводятся на:
- `containerColor = colorScheme.tertiaryFixedDim`;
- `contentColor = colorScheme.onTertiaryFixed`.

Бейдж выносится в общий composable `CartBadge(count: Int)` в `ui/components/CartBadge.kt`. При `count == 0` он ничего не рисует. Оба места используют его, чтобы цвета задавались в одном месте.

### 6.4. Нижняя панель экрана позиции

- `BottomAppBar` заменяется на `Surface(color = surfaceContainer, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))`.
- Внутри строка из `QuantityStepper` и `Button("В корзину · N ₽")`.
- Отступы 16dp по краям, 12dp по вертикали, учёт `navigationBarsPadding`.

## 7. Экраны

Для каждого экрана ниже перечислено только то, что меняется. Всё остальное остаётся как есть.

### 7.1. Меню (`MenuScreen`, `ProductCard`)
- Шапка: логотип headlineMedium в primary. Под ним строка «значок геолокации (tertiary) · адрес · стрелка» — как сейчас, остаётся заглушкой выбора кофейни.
- Кнопка поиска **удаляется**.
- Фильтр-чипы разделов заменяются на `PrimaryTabRow` с тремя `Tab` (Кофе / Не кофе / Выпечка). Выбор сохраняется в `rememberSaveable`, как сейчас.
- `ProductCard`: цена стилем `priceSmall` в tertiary. Кнопка «+» — `FilledTonalIconButton` стандартного размера: убрать `Modifier.size(36.dp)` и `Modifier.size(20.dp)` у иконки.

### 7.2. Позиция (`ProductScreen`)
- Верхняя панель без изменений: назад, избранное, поделиться, корзина с бейджем. Меняется только цвет бейджа (6.3).
- Название — `headlineSmall` вместо `headlineMedium`. Цена — `priceLarge` в tertiary.
- Чип «Нет в наличии»: убрать `disabledLabelColor = error`, остаётся стандартный disabled-стиль.
- `ExtrasSection`: убрать переопределение цветов на tertiary.
- Нижняя панель — по 6.4.

### 7.3. Корзина (`CartScreen`)
- Удалить `CartSummary`, то есть блок «Товары (N) … ₽ / Итого».
- Нижняя закреплённая панель (`Surface`, `surfaceContainerLow`):
  - строка «Итого · N товар(а/ов)» (`titleMedium`) и сумма (`priceLarge`, tertiary);
  - под ней `Button("Оформить заказ")` на всю ширину.
- `CartItemCard`:
  - удаление — `IconButton` стандартного размера (убрать `size(32.dp)`/`size(20.dp)`);
  - счётчик — `QuantityStepper`;
  - цена строки — `priceSmall` в tertiary.
- Пустая корзина: текст `cart_empty` и под ним `Button("Перейти в меню")`. Нужен новый параметр `onBrowseMenu: () -> Unit`, в `CoffeeRunApp` он вызывает `navController.openTab(Tab.MENU)`.
- Подсказка `cart_hint` остаётся под списком.

### 7.4. Оформление (`CheckoutScreen`)
- Строка кофейни: значок геолокации в tertiary. **Без** стрелки и без `clickable` — это информация, а не действие.
- «Ко времени»: `RadioRow(enabled = false)`, подпись — новая строка «Скоро». Выбрать его нельзя. `enum PickupTime` и состояние `pickup` удаляются, «Как можно скорее» всегда `selected = true`.
- Оплата: вместо двух `RadioRow` один `ListItem` (иконка оплаты, «Оплата при получении», `payment_on_pickup_hint`). Строки `payment_online*` больше не нужны.
- «Итого» в нижней панели — `priceLarge` в tertiary.

### 7.5. Заказ принят (`OrderPlacedScreen`)
- Удалить `TopAppBar` с крестиком и параметр `onClose`. В `CoffeeRunApp` убрать передачу `onClose`. «Назад» и «Вернуться в меню» остаются.
- Остальное меняется через тему: «Заказ принят!» и номер — Yeseva One, круг успеха — новый tertiaryContainer.

### 7.6. Заказы (`OrdersScreen`)
- Активный заказ: без кнопки «Подробнее». `OrderCard` получает `action: (() -> Unit)?` и при `null` не рисует кнопку.
- Нижняя строка карточки: «Итого» (`bodyMedium`, onSurfaceVariant) и сумма (`priceSmall`, tertiary), затем кнопка «Повторить» у истории.
- Пустой список: текст `orders_empty` и `Button("Перейти в меню")`. Нужен новый параметр `onBrowseMenu`, разводка как в 7.3.

### 7.7. Профиль (`ProfileScreen`)
- Компоненты остаются: редактирование, две карточки статистики, 4 пункта списка со стрелками. Все пока заглушки, как сейчас.
- Аватар: `tertiaryContainer` и буква `headlineSmall` в `onTertiaryContainer`.
- Цифры статистики — `headlineSmall` в tertiary. Числа пока демонстрационные (`DEMO_*`), это известное ограничение.

### 7.8. Тема (`Theme.kt`)
- `CoffeeRunTheme(dynamicColor: Boolean = false, …)`.
- В `lightScheme`/`darkScheme` передаются все роли из раздела 3, включая фиксированные (параметры `primaryFixed…onTertiaryFixedVariant` у `lightColorScheme`/`darkColorScheme`).
- Если какой-то параметр в сигнатуре Material 3 1.4.0 отсутствует, использовать `.copy(...)`.

## 8. Строки (`values` и `values-ru`)

| Ключ | ru | en | Статус |
|---|---|---|---|
| `pickup_soon` | Скоро | Coming soon | новый |
| `payment_on_pickup_title` | Оплата при получении | Pay on pickup | новый |
| `browse_menu` | Перейти в меню | Go to menu | новый |
| `cart_total_items` (plurals) | Итого · %d товар / товара / товаров | Total · %d item / items | новый |
| `menu_search`, `order_details`, `cart_items_count`, `pickup_scheduled_hint`, `payment_online`, `payment_online_hint`, `action_close`* | — | — | удалить, если больше нигде не используются |

\* `action_close` удалить только если нигде больше не используется: план проверяет это поиском.

## 9. Тестирование

- `NavigationTest` (11 тестов) должен остаться зелёным без правок. Селекторы опираются на подписи вкладок, `cart_empty`, `cart_checkout`, `checkout_confirm`, `placed_*`, `order_repeat` — все они сохраняются.
- Новые UI-тесты (`androidTest`):
  - `cartEmpty_browseMenuOpensMenu` — в пустой корзине «Перейти в меню» открывает Меню;
  - `checkout_scheduledPickupIsDisabled` — строка «Ко времени» `assertIsNotEnabled()`;
  - `product_minusDisabledAtOne` — на экране позиции «−» при количестве 1 `assertIsNotEnabled()`, после «+» становится доступна;
  - `menu_tabsSwitchCategory` — вкладка «Выпечка» показывает «Круассан» и скрывает «Капучино».
- Ручная проверка на эмуляторе, светлая и тёмная тема (Settings → Display → Dark theme): каждый экран сверить с колонкой «Вариант 3» / «Вариант 3 · тёмная» в Figma.
- Тесты запускаются через `adb shell am instrument` (см. план навигации): `connectedDebugAndroidTest` на этой машине падает из-за кириллицы в путях.

## 10. Вне рамок

- Реализация заглушек (выбор кофейни, избранное, «поделиться», редактирование профиля, настоящая статистика) — элементы остаются в UI и показывают «не реализовано», как сейчас.
- Регенерация схем среднего и высокого контраста.
- Анимации переходов, экраны деталей заказа.
- Фото товаров остаются прежними (в Figma они показаны заглушками).

## 11. Риски

| Риск | Что делать |
|---|---|
| Yeseva One — одно начертание; при `fontWeight = Bold` Android нарисует искусственный жирный | В `FontFamily` только `Font(R.font.yeseva_one_regular, FontWeight.Normal)`, в стилях Yeseva `fontWeight` не задавать |
| Длинные цены и названия в Yeseva шире Playfair | Проверить «Раф ванильный» и «Булочка с корицей» на 360dp; при переполнении — `maxLines = 2` у названия |
| Фиксированные роли могут не прокидываться через `darkColorScheme(...)` в 1.4.0 | Проверить сборкой, иначе `.copy(...)` (7.8) |
