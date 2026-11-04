package ru.coffeerun.ui.product

import ru.coffeerun.ui.components.sharedProductImage
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import ru.coffeerun.data.DemoMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.model.Customization
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.ui.components.CartBadge
import ru.coffeerun.ui.components.QuantityStepper
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.priceLarge
import ru.coffeerun.ui.theme.spacing

/**
 * Экран позиции меню. Набор блоков зависит от [product]: [Product.customizations]
 * задаёт, что можно выбрать (размер, молоко, лёд, добавки, комментарий). У выпечки
 * блоков нет — только описание, калории, аллергены и количество порций.
 *
 * Stateless: выбор пользователя ([form]) и цену порции ([unitPrice]) держит
 * [ProductViewModel], экран только показывает их и сообщает о действиях.
 */
@Composable
fun ProductScreen(
    product: Product,
    form: ProductForm,
    unitPrice: Int,
    prices: OptionPrices,
    cartCount: Int,
    onSizeSelected: (DrinkSize) -> Unit,
    onMilkSelected: (MilkType) -> Unit,
    onIcedChange: (Boolean) -> Unit,
    onExtraToggle: (Extra) -> Unit,
    onNoteChange: (String) -> Unit,
    onQuantityChange: (delta: Int) -> Unit,
    onAddToCart: () -> Unit,
    onBack: () -> Unit,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onCart: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    val features = product.customizations
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ProductTopBar(
                cartCount = cartCount,
                onBack = onBack,
                isFavorite = isFavorite,
                onFavorite = onFavorite,
                onShare = onShare,
                onCart = onCart
            )
        },
        bottomBar = {
            OrderBar(
                quantity = form.quantity,
                totalPrice = unitPrice * form.quantity,
                onDecrease = { onQuantityChange(-1) },
                onIncrease = { onQuantityChange(+1) },
                onAddToCart = onAddToCart
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
            ProductHeader(
                product = product,
                // Цена за одну порцию — без учёта количества.
                price = unitPrice,
                modifier = Modifier.padding(MaterialTheme.spacing.large)
            )

            if (Customization.SIZE in features) {
                SectionDivider()
                SizeSection(
                    sizes = prices.sizes,
                    selectedSize = form.size,
                    onSizeSelected = onSizeSelected,
                    modifier = Modifier.padding(MaterialTheme.spacing.large)
                )
            }

            if (Customization.MILK in features) {
                SectionDivider()
                MilkSection(
                    milks = prices.milks,
                    selectedMilk = form.milk,
                    onMilkSelected = onMilkSelected,
                    modifier = Modifier.padding(MaterialTheme.spacing.large)
                )
            }

            if (Customization.ICE in features) {
                SectionDivider()
                TemperatureSection(
                    isIced = form.isIced,
                    onIcedChange = onIcedChange
                )
            }

            // Добавку без цены заказать нельзя — её сейчас нет в кофейне
            val extras = product.extras.mapNotNull { extra -> prices.extras[extra]?.let { extra to it } }.toMap()
            if (extras.isNotEmpty()) {
                SectionDivider()
                ExtrasSection(
                    available = extras,
                    selectedExtras = form.extras,
                    onExtraToggle = onExtraToggle,
                    modifier = Modifier.padding(MaterialTheme.spacing.large)
                )
            }

            if (Customization.NOTE in features) {
                SectionDivider()
                NoteSection(
                    note = form.note,
                    onNoteChange = onNoteChange,
                    modifier = Modifier.padding(MaterialTheme.spacing.large)
                )
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large))
}

/** Верхняя панель: «назад», избранное, «поделиться» и корзина с бейджем. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductTopBar(
    cartCount: Int,
    onBack: () -> Unit,
    isFavorite: Boolean,
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
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite)
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_share)
                )
            }
            IconButton(onClick = onCart) {
                BadgedBox(badge = { CartBadge(cartCount) }) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = stringResource(R.string.action_cart)
                    )
                }
            }
        }
    )
}

/** Шапка: фото, название, цена за порцию, описание, калории и аллергены. */
@Composable
private fun ProductHeader(
    product: Product,
    price: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AsyncImage(
            model = product.imageUrl,
            // Фото декоративное: название идёт текстом ниже,
            // поэтому TalkBack картинку пропускает.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .sharedProductImage(product.id)
                .fillMaxWidth()
                .height(240.dp)
                .clip(MaterialTheme.shapes.large)
                // Фон виден, пока фото грузится или если не загрузилось
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.price_rub, price),
                style = MaterialTheme.typography.priceLarge,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        Text(
            text = product.details,
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
                text = stringResource(R.string.drink_calories, product.calories),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (product.allergens.isNotEmpty()) {
                VerticalDivider(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.spacing.medium)
                        .height(MaterialTheme.spacing.large)
                )
                Text(
                    text = stringResource(
                        R.string.drink_allergens,
                        product.allergens.map { stringResource(it.labelRes) }.joinToString(", ")
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
    // Вместо BottomAppBar (он устаревает и рассчитан на иконки и FAB) — панель в стиле docked toolbar
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Границы количества держатся на отключении кнопок: на пределе
            // «−» и «+» недоступны, поэтому отдельной проверки в обработчиках нет.
            QuantityStepper(
                quantity = quantity,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                canDecrease = quantity > MIN_QUANTITY,
                canIncrease = quantity < MAX_QUANTITY
            )
            Button(
                onClick = onAddToCart,
                modifier = Modifier.weight(1f)
            ) {
                // Цена прокручивается вверх, когда растёт, и вниз, когда падает: видно, что она пересчиталась
                AnimatedContent(
                    targetState = totalPrice,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "price"
                ) { price ->
                    Text(
                        text = stringResource(R.string.add_to_cart, price),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun ProductScreenPreview() {
    CoffeeRunTheme {
        ProductScreen(
            product = DemoMenu.CAPPUCCINO,
            form = ProductForm(),
            unitPrice = 240,
            prices = DemoMenu.prices,
            cartCount = 2,
            onSizeSelected = {},
            onMilkSelected = {},
            onIcedChange = {},
            onExtraToggle = {},
            onNoteChange = {},
            onQuantityChange = {},
            onAddToCart = {},
            onBack = {},
            isFavorite = false,
            onFavorite = {},
            onShare = {},
            onCart = {}
        )
    }
}