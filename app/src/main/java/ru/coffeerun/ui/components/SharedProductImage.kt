package ru.coffeerun.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Общая сцена переходов вокруг NavHost; null — вне NavHost (превью, тесты отдельных экранов). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Анимация входа и выхода текущего экрана в NavHost. */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Фото позиции «перелетает» из карточки в меню или избранном в шапку экрана напитка и обратно.
 * Без сцены переходов ничего не делает: превью и тесты экранов работают как раньше.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedProductImage(productId: String): Modifier {
    val scene = LocalSharedTransitionScope.current ?: return this
    val screen = LocalNavAnimatedScope.current ?: return this
    return with(scene) { this@sharedProductImage.sharedElement(rememberSharedContentState("product-image-$productId"), screen) }
}
