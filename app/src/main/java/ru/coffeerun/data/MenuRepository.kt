package ru.coffeerun.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product
import ru.coffeerun.model.MenuSnapshot

/** Источник меню. suspend — чтобы подмена на сетевую реализацию не меняла вызывающий код. */
interface MenuRepository {
    fun observeSnapshot(): Flow<MenuSnapshot> = flow { emit(MenuSnapshot(getMenu(), getOptionPrices())) }

    /** Сетевая реализация запрашивает новый снимок и сообщает об ошибке, если сервер недоступен. */
    suspend fun refresh(): MenuSnapshot = observeSnapshot().first()

    suspend fun getMenu(): List<Product>
    suspend fun getProduct(id: String): Product?

    /** Цены и наличие размеров, молока и добавок — приходят вместе с меню. */
    suspend fun getOptionPrices(): OptionPrices
}
