package ru.coffeerun

import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.domain.orders.RepeatOrderUseCase

fun testRepeatOrder(menu: MenuRepository, cart: CartRepository) =
    RepeatOrderUseCase(menu, cart, InMemoryAuthRepository(loggedIn = true))
