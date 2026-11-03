package ru.coffeerun

import java.io.File
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.network.MenuDto
import ru.coffeerun.data.network.ExtraOptionDto
import ru.coffeerun.data.network.ProductDto
import ru.coffeerun.data.network.SizeOptionDto
import ru.coffeerun.data.network.toModel
import ru.coffeerun.data.network.toOptionPrices
import ru.coffeerun.model.Allergen

/**
 * Тело ответа GET /menu из окружения Mockoon разбирается в [MenuDto] — мок и DTO не разошлись;
 * маппинг в модель даёт то же меню, что [LocalMenu].
 */
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
    fun toModel_mockMenuEqualsLocalMenu() {
        assertEquals(LocalMenu.products, menu.products.map { it.toModel(BuildConfig.BASE_URL) })
    }

    @Test
    fun toOptionPrices_mockPricesEqualLocalMenu() {
        assertEquals(LocalMenu.prices, menu.toOptionPrices())
    }

    @Test
    fun toOptionPrices_unknownCodesDropped() {
        val dto = menu.copy(sizes = menu.sizes + SizeOptionDto("XL", 340), extras = menu.extras + ExtraOptionDto("SALT", 10))
        assertEquals(LocalMenu.prices, dto.toOptionPrices())
    }

    private val tea = ProductDto(
        id = "tea", category = "NOT_COFFEE", name = "Чай", description = "", details = "",
        imageUrl = "images/tea.jpg", price = 140, priceFrom = true, calories = 2,
        allergens = listOf("SOY", "MILK", "SESAME"), customizations = emptyList(), extras = listOf("LEMON", "SALT")
    )

    @Test
    fun toModel_unknownAllergens_becomeOneOtherAtTheEnd() {
        assertEquals(listOf(Allergen.MILK, Allergen.OTHER), tea.toModel("http://host/")!!.allergens.toList())
    }

    @Test
    fun toModel_unknownExtrasDropped() {
        assertEquals(listOf("LEMON"), tea.toModel("http://host/")!!.extras.map { it.name })
    }

    @Test
    fun toModel_unknownCategory_null() {
        assertNull(tea.copy(category = "SANDWICH").toModel("http://host/"))
    }

    @Test
    fun toModel_imageUrl_relativeResolvedAbsoluteKept() {
        assertEquals("http://host/images/tea.jpg", tea.toModel("http://host/")!!.imageUrl)
        assertEquals("https://cdn/x.jpg", tea.copy(imageUrl = "https://cdn/x.jpg").toModel("http://host/")!!.imageUrl)
    }

    @Test
    fun unknownField_ignored() {
        val dto: MenuDto = CoffeeRunJson.decodeFromString(
            """{"products":[],"sizes":[],"milks":[],"extras":[],"promo":"new"}"""
        )
        assertTrue(dto.products.isEmpty())
    }
}
