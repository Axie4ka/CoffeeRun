package ru.coffeerun

import java.io.File
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.network.MenuDto

/** Тело ответа GET /menu из окружения Mockoon разбирается в [MenuDto] — мок и DTO не разошлись. */
class MenuDtoTest {

    /** Ответ 200 маршрута `menu` из mock/coffeerun.json; unit-тесты запускаются из папки app. */
    private fun mockMenuBody(): String {
        val env = CoffeeRunJson.parseToJsonElement(File("../mock/coffeerun.json").readText()).jsonObject
        val route = env.getValue("routes").jsonArray.map { it.jsonObject }
            .single { it.getValue("endpoint").jsonPrimitive.content == "menu" }
        val ok = route.getValue("responses").jsonArray.map { it.jsonObject }
            .single { it.getValue("statusCode").jsonPrimitive.int == 200 }
        return ok.getValue("body").jsonPrimitive.content
    }

    private val menu: MenuDto = CoffeeRunJson.decodeFromString(mockMenuBody())

    @Test
    fun products_allThirteenWithUniqueIds() {
        assertEquals(13, menu.products.size)
        assertEquals(13, menu.products.map { it.id }.toSet().size)
    }

    @Test
    fun options_sizesMilksExtras() {
        assertEquals(listOf("SMALL", "MEDIUM", "LARGE"), menu.sizes.map { it.code })
        assertEquals(5, menu.milks.size)
        assertEquals(11, menu.extras.size)
        assertFalse(menu.milks.single { it.code == "COCONUT" }.available)
    }

    @Test
    fun productExtras_existInOptionList() {
        val codes = menu.extras.map { it.code }.toSet()
        for (product in menu.products) {
            assertTrue("${product.id}: ${product.extras - codes}", codes.containsAll(product.extras))
        }
    }

    @Test
    fun unknownField_ignored() {
        val dto: MenuDto = CoffeeRunJson.decodeFromString(
            """{"products":[],"sizes":[],"milks":[],"extras":[],"promo":"new"}"""
        )
        assertTrue(dto.products.isEmpty())
    }
}
