package ru.coffeerun.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import ru.coffeerun.R
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkOption
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.SizeOption
import ru.coffeerun.model.MAX_NOTE_LENGTH
import ru.coffeerun.ui.theme.spacing

// Блоки выбора на карточке напитка; какие из них показать, решает ProductScreen по Product.customizations

/** Выбор размера: ровно один вариант из [sizes]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SizeSection(
    sizes: List<SizeOption>,
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
            sizes.forEachIndexed { index, (size) ->
                SegmentedButton(
                    selected = size == selectedSize,
                    onClick = { onSizeSelected(size) },
                    // Форма зависит от позиции: скругляются только внешние
                    // углы первой и последней кнопки.
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = sizes.size
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

/** Выбор молока: один вариант из [milks]; недоступные показаны, но отключены. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MilkSection(
    milks: List<MilkOption>,
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
            milks.forEach { (milk, price, isAvailable) ->
                val selected = milk == selectedMilk
                val name = stringResource(milk.labelRes)
                // К названию добавляем пояснение: «нет в наличии» важнее цены,
                // а у бесплатных вариантов пояснения нет.
                val label = when {
                    !isAvailable -> stringResource(
                        R.string.option_with_detail, name, stringResource(R.string.out_of_stock)
                    )
                    price > 0 -> stringResource(
                        R.string.option_with_detail, name, stringResource(R.string.price_extra, price)
                    )
                    else -> name
                }

                FilterChip(
                    selected = selected,
                    onClick = { onMilkSelected(milk) },
                    enabled = isAvailable,
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
                    } else null
                )
            }
        }
    }
}

/** Переключатель «со льдом». */
@Composable
internal fun TemperatureSection(
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

/** Выбор добавок: любое количество вариантов из [available] (добавка → доплата) — они зависят от позиции. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtrasSection(
    available: Map<Extra, Int>,
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
            available.forEach { (extra, price) ->
                val selected = extra in selectedExtras

                FilterChip(
                    selected = selected,
                    onClick = { onExtraToggle(extra) },
                    label = {
                        Text(
                            stringResource(
                                R.string.option_with_detail,
                                stringResource(extra.labelRes),
                                stringResource(R.string.price_extra, price)
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
                    } else null
                )
            }
        }
    }
}

/** Поле комментария к заказу со счётчиком символов. */
@Composable
internal fun NoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = note,
        // Лимит длины проверяет ProductViewModel.setNote
        onValueChange = onNoteChange,
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
