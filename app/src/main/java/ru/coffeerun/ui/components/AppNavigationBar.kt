package ru.coffeerun.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R

/** Четыре раздела верхнего уровня. */
enum class Tab(@StringRes val labelRes: Int) {
    MENU(R.string.tab_menu),
    CART(R.string.tab_cart),
    ORDERS(R.string.tab_orders),
    PROFILE(R.string.tab_profile)
}

@Composable
private fun Tab.painter(): Painter = when (this) {
    Tab.MENU -> painterResource(R.drawable.ic_local_cafe)
    Tab.CART -> rememberVectorPainter(Icons.Default.ShoppingCart)
    Tab.ORDERS -> painterResource(R.drawable.ic_receipt)
    Tab.PROFILE -> rememberVectorPainter(Icons.Default.Person)
}

/** Нижняя навигация. На вкладке «Корзина» показывает бейдж с числом порций. */
@Composable
fun AppNavigationBar(
    selected: Tab,
    cartCount: Int,
    onSelect: (Tab) -> Unit
) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            val icon = tab.painter()
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    if (tab == Tab.CART && cartCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) { Text(cartCount.toString()) }
                            }
                        ) { Icon(icon, contentDescription = null) }
                    } else {
                        Icon(icon, contentDescription = null)
                    }
                },
                label = { Text(label) }
            )
        }
    }
}
