package ru.coffeerun.data.network

import java.io.File
import java.time.ZoneOffset
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkOptions
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop
import ru.coffeerun.model.toOrderItem

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
        assertEquals("latte", o1037.lines.single().productId)
        assertEquals("Латте", o1037.lines.single().name)
        assertEquals(480, o1037.total)
        // 2026-10-31T09:15:00+03:00
        assertEquals(java.time.OffsetDateTime.of(2026, 10, 31, 9, 15, 0, 0, ZoneOffset.ofHours(3)).toInstant().toEpochMilli(), o1037.placedAtMillis)
        assertNull(o1037.pickupAtMillis)

        assertEquals(listOf("raf", "espresso"), o1029.lines.map { it.productId })
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
    fun goneProduct_lineKeptWithPaidPriceAndOptions() {
        val options = DrinkOptionsDto("LARGE", "OAT", iced = true, extras = listOf("VANILLA"), note = "без пенки")
        val dto = OrderDto(
            1046, "PICKED_UP", "MOSKOVSKAYA", "2026-11-03T12:00:00+03:00",
            lines = listOf(OrderLineDto("pumpkin_latte", 2, 320, options))
        )
        val order = dto.toModel(products)!!
        assertEquals("снятая с меню позиция остаётся в истории", 1, order.lines.size)
        val line = order.lines.single()
        assertEquals("pumpkin_latte", line.productId)
        assertNull("заглушку выбирает UI, а не модель данных", line.name)
        assertEquals(2, line.quantity)
        assertEquals(320, line.unitPrice)
        assertEquals(640, line.total)
        assertEquals(DrinkSize.LARGE, line.options?.size)
        assertEquals(MilkType.OAT, line.options?.milk)
        assertEquals(true, line.options?.isIced)
        assertEquals(setOf(Extra.VANILLA), line.options?.extras)
        assertEquals("без пенки", line.options?.note)
        assertEquals(640, order.total)
    }

    @Test
    fun knownProduct_keepsPaidPriceAndSeparateConfigurations() {
        val dto = OrderDto(
            1047, "PICKED_UP", "MOSKOVSKAYA", "2026-11-03T12:00:00+03:00",
            lines = listOf(
                OrderLineDto("latte", 2, 100, DrinkOptionsDto("SMALL", "REGULAR", false, emptyList(), "")),
                OrderLineDto("latte", 1, 170, DrinkOptionsDto("LARGE", "OAT", true, emptyList(), ""))
            )
        )
        val order = dto.toModel(products)!!
        assertEquals(2, order.lines.size)
        assertEquals(listOf(100, 170), order.lines.map { it.unitPrice })
        assertEquals(listOf(DrinkSize.SMALL, DrinkSize.LARGE), order.lines.map { it.options?.size })
        assertEquals(370, order.total)
    }

    @Test
    fun statusUpdate_withoutMenu_keepsKnownNameAndUnknownLines() {
        val known = CartLine(DemoMenu.CROISSANT, 150, quantity = 2).toOrderItem()
        val dto = OrderDto(
            1048, "CANCELLED", "MOSKOVSKAYA", "2026-11-03T12:00:00+03:00",
            lines = listOf(OrderLineDto("croissant", 2, 150), OrderLineDto("retired", 1, 100))
        )
        val order = dto.toModel(emptyMap(), knownItems = listOf(known))!!
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertEquals("Круассан", order.lines.first().name)
        assertEquals(DemoMenu.CROISSANT.description, order.lines.first().description)
        assertEquals(listOf("croissant", "retired"), order.lines.map { it.productId })
        assertNull(order.lines.last().name)
        assertEquals(400, order.total)
    }

    @Test
    fun unknownStatus_isAccepted_unknownShop_isDropped() {
        assertEquals(OrderStatus.ACCEPTED, demo[0].copy(status = "COOKING_ON_MARS").toModel(products)!!.status)
        assertNull(demo[0].copy(shop = "MARS").toModel(products))
        assertNull(demo[0].copy(placedAt = "вчера").toModel(products))
    }
}
