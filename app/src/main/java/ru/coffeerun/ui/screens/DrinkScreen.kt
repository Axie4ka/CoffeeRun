package ru.coffeerun.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkOptions
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing

// Ограничения формы заказа: длина комментария и количество порций
private const val MAX_NOTE_LENGTH = 100
private const val MIN_QUANTITY = 1
private const val MAX_QUANTITY = 10

/**
 * Экран настройки напитка: размер, молоко, температура, добавки, комментарий
 * и количество порций
 *
 * Выбранные параметры экран хранит сам, аэкран получает
 * [cartCount] для бейджа и сообщает о добавлении через [onAddToCart], передавая
 * готовую позицию корзины (напиток, опции, цена и количество порций)
 */
@Composable
fun DrinkScreen(
    cartCount: Int,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onCart: () -> Unit,
    onAddToCart: (CartLine) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    // rememberSaveable, а не remember: выбор пользователя переживает поворот
    // экрана и пересоздание Activity.
    var selectedSize by rememberSaveable { mutableStateOf(DrinkSize.MEDIUM) }
    var selectedMilk by rememberSaveable { mutableStateOf(MilkType.REGULAR) }
    var isIced by rememberSaveable { mutableStateOf(false) }
    var selectedExtras by rememberSaveable { mutableStateOf(setOf<Extra>()) }
    var note by rememberSaveable { mutableStateOf("") }
    // Цены — производные значения: не хранятся в состоянии, а пересчитываются
    // из выбранных параметров при каждой рекомпозиции.
    val unitPrice = selectedSize.price + selectedMilk.price + selectedExtras.sumOf { it.price }
    var quantity by rememberSaveable { mutableIntStateOf(MIN_QUANTITY) }
    val totalPrice = unitPrice * quantity
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DrinkTopBar(
                cartCount = cartCount,
                onBack = onBack,
                onFavorite = onFavorite,
                onShare = onShare,
                onCart = onCart
            )
        },
        bottomBar = {
            OrderBar(
                quantity = quantity,
                totalPrice = totalPrice,
                onDecrease = { quantity-- },
                onIncrease = { quantity++ },
                onAddToCart = {
                    onAddToCart(
                        CartLine(
                            product = Product.CAPPUCCINO,
                            unitPrice = unitPrice,
                            quantity = quantity,
                            options = DrinkOptions(selectedSize, selectedMilk, isIced, selectedExtras)
                        )
                    )
                }
            )
        },

        snackbarHost = { SnackbarHost(snackbarHostState) },

    ) { innerPadding ->
        Column(
            // consumeWindowInsets помечает отступы Scaffold как уже учтённые,
            // поэтому imePadding добавляет только недостающую часть высоты
            // клавиатуры, а не всю её поверх нижней панели.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            DrinkHeader(
                // Цена за одну порцию — без учёта количества.
                price = unitPrice,
                calories = 120,
                modifier = Modifier.padding(MaterialTheme.spacing.large)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large))

            SizeSection(
                selectedSize = selectedSize,
                onSizeSelected = { selectedSize = it },
                modifier = Modifier.padding(MaterialTheme.spacing.large)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large))

            MilkSection(
                selectedMilk = selectedMilk,
                onMilkSelected = { selectedMilk = it },
                modifier = Modifier.padding(MaterialTheme.spacing.large)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large))

            TemperatureSection(
                isIced = isIced,
                onIcedChange = { isIced = it }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large))

            ExtrasSection(
                selectedExtras = selectedExtras,
                onExtraToggle = { extra ->
                    selectedExtras = if (extra in selectedExtras) {
                        selectedExtras - extra
                    } else {
                        selectedExtras + extra
                    }
                },
                modifier = Modifier.padding(MaterialTheme.spacing.large)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large))

            NoteSection(
                note = note,
                onNoteChange = { note = it },
                modifier = Modifier.padding(MaterialTheme.spacing.large)
            )
        }
    }
}

/** Верхняя панель: «назад», избранное, «поделиться» и корзина с бейджем. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrinkTopBar(
    cartCount: Int,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onCart: () -> Unit,
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
        },
        actions = {
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite)
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_share)
                )
            }
            IconButton(onClick = onCart) {
                BadgedBox(
                    badge = {
                        // Пустую корзину бейджем не отмечаем.
                        if (cartCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Text(cartCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = stringResource(R.string.action_cart)
                    )
                }
            }
        }
    )
}

/** Шапка: фото напитка, название, цена за порцию, описание, калории и аллергены. */
@Composable
private fun DrinkHeader(
    price: Int,
    calories: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.cappuccino),
            // Фото декоративное: название напитка идёт текстом ниже,
            // поэтому TalkBack картинку пропускает.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(MaterialTheme.shapes.large)
        )

        Spacer(Modifier.width(MaterialTheme.spacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.drink_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.price_rub, price),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        Text(
            text = stringResource(R.string.drink_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(MaterialTheme.spacing.extraSmall))
            Text(
                text = stringResource(R.string.drink_calories, calories),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.spacing.medium)
                    .height(MaterialTheme.spacing.large)
            )
            Text(
                text = stringResource(
                    R.string.drink_allergens,
                    stringResource(R.string.allergen_milk)
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Выбор размера: ровно один вариант из [DrinkSize]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SizeSection(
    selectedSize: DrinkSize,
    onSizeSelected: (DrinkSize) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.section_size),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DrinkSize.entries.forEachIndexed { index, size ->
                SegmentedButton(
                    selected = size == selectedSize,
                    onClick = { onSizeSelected(size) },
                    // Форма зависит от позиции: скругляются только внешние
                    // углы первой и последней кнопки.
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = DrinkSize.entries.size
                    )
                ) {
                    Text(
                        text = stringResource(size.labelRes),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Выбор молока: один вариант из [MilkType]; недоступные показаны, но отключены. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilkSection(
    selectedMilk: MilkType,
    onMilkSelected: (MilkType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.section_milk),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            MilkType.entries.forEach { milk ->
                val selected = milk == selectedMilk
                val name = stringResource(milk.labelRes)
                // К названию добавляем пояснение: «нет в наличии» важнее цены,
                // а у бесплатных вариантов пояснения нет.
                val label = when {
                    !milk.isAvailable -> stringResource(
                        R.string.option_with_detail, name, stringResource(R.string.out_of_stock)
                    )
                    milk.price > 0 -> stringResource(
                        R.string.option_with_detail, name, stringResource(R.string.price_extra, milk.price)
                    )
                    else -> name
                }

                FilterChip(
                    selected = selected,
                    onClick = { onMilkSelected(milk) },
                    enabled = milk.isAvailable,
                    label = { Text(label) },
                    // null, а не пустая лямбда: иначе чип оставит под иконку
                    // пустое место.
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    // Отключён только чип «нет в наличии» — выделяем его цветом ошибки.
                    colors = FilterChipDefaults.filterChipColors(
                        disabledLabelColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }
    }
}

/** Переключатель «со льдом». */
@Composable
private fun TemperatureSection(
    isIced: Boolean,
    onIcedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        overlineContent = {
            Text(stringResource(R.string.section_temperature))
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.temperature_iced),
                style = MaterialTheme.typography.titleMedium
            )
        },
        trailingContent = {
            Switch(
                checked = isIced,
                // Нажатия обрабатывает вся строка (toggleable ниже), сам Switch
                // только показывает состояние. Так область нажатия больше,
                // а TalkBack читает строку как один переключатель.
                onCheckedChange = null,
                thumbContent = if (isIced) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else null
            )
        },
        modifier = modifier.toggleable(
            value = isIced,
            role = Role.Switch,
            onValueChange = onIcedChange
        )
    )
}

/** Выбор добавок: любое количество вариантов из [Extra]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtrasSection(
    selectedExtras: Set<Extra>,
    onExtraToggle: (Extra) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.section_extras),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Extra.entries.forEach { extra ->
                val selected = extra in selectedExtras

                FilterChip(
                    selected = selected,
                    onClick = { onExtraToggle(extra) },
                    label = {
                        Text(
                            stringResource(
                                R.string.option_with_detail,
                                stringResource(extra.labelRes),
                                stringResource(R.string.price_extra, extra.price)
                            )
                        )
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    // Выбранные добавки красим в tertiary, чтобы они визуально
                    // отличались от чипов молока.
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        }
    }
}

/** Поле комментария к заказу со счётчиком символов. */
@Composable
private fun NoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = note,
        onValueChange = { newText ->
            // Текст длиннее лимита отклоняется целиком, а не обрезается:
            // слишком длинная вставка из буфера просто не применится.
            if (newText.length <= MAX_NOTE_LENGTH) onNoteChange(newText)
        },
        label = { Text(stringResource(R.string.section_note)) },
        placeholder = { Text(stringResource(R.string.note_placeholder)) },
        supportingText = {
            Text(
                text = stringResource(R.string.note_counter, note.length, MAX_NOTE_LENGTH),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        },
        maxLines = 3,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            // «Готово» снимает фокус с поля — вместе с ним скрывается клавиатура.
            onDone = { focusManager.clearFocus() }
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Нижняя панель: счётчик порций и кнопка «в корзину» с итоговой ценой. */
@Composable
private fun OrderBar(
    quantity: Int,
    totalPrice: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onAddToCart: () -> Unit
) {
    BottomAppBar {
        // Границы количества держатся на отключении кнопок: на пределе
        // «−» и «+» недоступны, поэтому отдельной проверки в обработчиках нет.
        FilledTonalIconButton(
            onClick = onDecrease,
            enabled = quantity > MIN_QUANTITY
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_remove),
                contentDescription = stringResource(R.string.action_decrease)
            )
        }

        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            // Минимальная ширина, чтобы кнопки не сдвигались при переходе 9 → 10.
            modifier = Modifier.widthIn(min = 32.dp)
        )

        FilledTonalIconButton(
            onClick = onIncrease,
            enabled = quantity < MAX_QUANTITY
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.action_increase)
            )
        }

        Spacer(Modifier.width(MaterialTheme.spacing.large))

        Button(
            onClick = onAddToCart,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.add_to_cart, totalPrice),
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun DrinkScreenPreview() {
    // dynamicColor = false, чтобы превью показывало фирменную палитру.
    CoffeeRunTheme(dynamicColor = false) {
        DrinkScreen(
            cartCount = 2,
            onBack = {},
            onFavorite = {},
            onShare = {},
            onCart = {},
            onAddToCart = {}
        )
    }
}