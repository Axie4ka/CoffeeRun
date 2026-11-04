package ru.coffeerun.ui.menu

import ru.coffeerun.model.Product

/**
 * Позиции, в названии или описании которых есть [query]: без учёта регистра, «ё» равна «е».
 * Пустой запрос ничего не отсеивает.
 */
fun searchProducts(products: List<Product>, query: String): List<Product> {
    val q = query.trim().normalized()
    if (q.isEmpty()) return products
    return products.filter { q in it.name.normalized() || q in it.description.normalized() }
}

private fun String.normalized() = lowercase().replace('ё', 'е')
