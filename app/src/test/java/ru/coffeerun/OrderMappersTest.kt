package ru.coffeerun

import java.io.File
import java.time.ZoneOffset
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.network.DrinkOptionsDto
import ru.coffeerun.data.network.OrderDto
import ru.coffeerun.data.network.OrderLineDto
import ru.coffeerun.data.network.orderRequest
import ru.coffeerun.data.network.toModel
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkOptions
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop

/** Заказы: тело POST /orders из корзины и ответ сервера в [ru.coffeerun.model.Order]. */
class OrderMappersTest {
    private val products = DemoMenu.products.associateBy { it.id }

    /** Демо-заказы из хранилища `orders` в mock/coffeerun.json. */
    private val demo: List<OrderDto> = run {
        val env = CoffeeRunJson.parseToJsonElement(File("../mock/coffeerun.json").readText()).jsonObject
        val bucket = env.getValue("data").jsonArray.map { it.jsonObject }.single { it.getValue("name").jsonPrimitive.content == "orders" }
        CoffeeRunJson.decodeFromString(bucket.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun mockDemoOrders_mapWithProductsAndTime() {
        val (o1037, o1029) = demo.map { it.toModel(products)!! }
        assertEquals(1037, o1037.number)
        assertEquals(OrderStatus.PICKED_UP, o1037.status)
        assertEquals(Shop.MOSKOVSKAYA, o1037.shop)
        assertEquals(DemoMenu.LATTE, o1037.lines.single().product)
        assertEquals(480, o1037.total)
        // 2026-10-31T09:15:00+03:00
        assertEquals(java.time.OffsetDateTime.of(2026, 10, 31, 9, 15, 0, 0, ZoneOffset.ofHours(3)).toInstant().toEpochMilli(), o1037.placedAtMillis)
        assertNull(o1037.pickupAtMillis)

        assertEquals(listOf(DemoMenu.RAF, DemoMenu.ESPRESSO), o1029.lines.map { it.product })
        assertEquals(DrinkSize.LARGE, o1029.lines[0].options?.size)
        assertNull("эспрессо из меню «+» — без опций", o1029.lines[1].options)
        assertEquals(450, o1029.total)
    }

    @Test
    fun cart_toRequest_codesAndTime() {
        val latte = CartLine(
            DemoMenu.LATTE, unitPrice = 400, quantity = 2,
            options = DrinkOptions(DrinkSize.LARGE, MilkType.OAT, isIced = true, extras = setOf(Extra.VANILLA), note = "без пенки")
        )
        val croissant = CartLine(DemoMenu.CROISSANT, unitPrice = 180)
        // 18:30 по Москве
        val pickup = java.time.OffsetDateTime.of(2026, 11, 3, 18, 30, 0, 0, ZoneOffset.ofHours(3)).toInstant().toEpochMilli()
        val request = orderRequest(listOf(latte, croissant), Shop.STROITELEY, pickup, zone = ZoneOffset.ofHours(3))
        assertEquals("STROITELEY", request.shop)
        assertEquals("2026-11-03T18:30:00+03:00", request.pickupAt)
        assertEquals(
            listOf(
                OrderLineDto("latte", 2, 400, DrinkOptionsDto("LARGE", "OAT", iced = true, extras = listOf("VANILLA"), note = "без пенки")),
                OrderLineDto("croissant", 1, 180, null)
            ),
            request.lines
        )
        assertNull("как можно скорее", orderRequest(listOf(croissant), Shop.MOSKOVSKAYA, null).pickupAt)
    }

    @Test
    fun goneProduct_lineHidden_totalKept() {
        val dto = demo[1].copy(lines = demo[1].lines + OrderLineDto("pumpkin_latte", 1, 320))
        val order = dto.toModel(products)!!
        assertEquals(2, order.lines.size)
        assertEquals("за снятую с меню позицию заплачено — итог полный", 450 + 320, order.total)
    }

    @Test
    fun unknownStatus_isAccepted_unknownShop_isDropped() {
        assertEquals(OrderStatus.ACCEPTED, demo[0].copy(status = "COOKING_ON_MARS").toModel(products)!!.status)
        assertNull(demo[0].copy(shop = "MARS").toModel(products))
        assertNull(demo[0].copy(placedAt = "вчера").toModel(products))
    }
}
