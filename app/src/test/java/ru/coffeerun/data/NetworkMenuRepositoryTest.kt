package ru.coffeerun.data

import ru.coffeerun.StubApi
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.MenuDto
import ru.coffeerun.data.network.ProductDto
import ru.coffeerun.data.network.SizeOptionDto
import ru.coffeerun.model.DrinkSize

/** Сетевое меню: один запрос на всё, ошибки — только [IOException], смена языка — новый запрос. */
class NetworkMenuRepositoryTest {
    private val latte = ProductDto(
        id = "latte", category = "COFFEE", name = "Латте", description = "", details = "",
        imageUrl = "images/latte.jpg", price = 240, priceFrom = true, calories = 190,
        allergens = listOf("MILK"), customizations = listOf("SIZE"), extras = emptyList()
    )
    private val dto = MenuDto(listOf(latte), sizes = emptyList(), milks = emptyList(), extras = emptyList())

    private class FakeApi(var answer: () -> MenuDto) : StubApi() {
        var calls = 0
        override suspend fun getMenu(language: String?): MenuDto {
            calls++
            return answer()
        }
    }

    private var language = "ru"
    private fun repo(api: CoffeeRunApi) = NetworkMenuRepository(api, "http://host/", language = { language })

    @Test
    fun malformedPhoto_keepsProductWithoutRemoteImage() = runTest {
        val repository = repo(FakeApi { dto.copy(products = listOf(latte.copy(imageUrl = "images/bad path.jpg"))) })
        assertEquals("", repository.getProduct("latte")?.imageUrl)
    }

    @Test
    fun invalidMenu_doesNotReplaceLastValidSnapshot() = runTest {
        val api = FakeApi { dto }
        val repository = repo(api)
        repository.getMenu()
        for (invalid in listOf(listOf(latte.copy(price = -1)), listOf(latte, latte.copy(name = "Дубликат")))) {
            api.answer = { dto.copy(products = invalid) }
            org.junit.Assert.assertTrue(runCatching { repository.refresh() }.exceptionOrNull() is IOException)
            assertEquals(240, repository.getMenu().single().price)
        }
    }

    @Test
    fun menuProductAndPrices_oneRequest() = runTest {
        val api = FakeApi { dto }
        val repo = repo(api)
        // Меню и позиция просятся одновременно — как при открытии позиции из «Избранного»
        listOf(async { repo.getMenu() }, async { repo.getProduct("latte") }, async { repo.getOptionPrices() }).awaitAll()
        assertEquals("http://host/images/latte.jpg", repo.getProduct("latte")?.imageUrl)
        assertNull(repo.getProduct("unknown"))
        assertEquals(1, api.calls)
    }

    @Test
    fun languageChange_reloads() = runTest {
        val api = FakeApi { dto }
        val repo = repo(api)
        repo.getMenu()
        language = "en"
        repo.getMenu()
        assertEquals(2, api.calls)
    }

    @Test
    fun http500_isIOException_andNotCached() = runTest {
        val api = FakeApi { throw HttpException(Response.error<MenuDto>(500, "".toResponseBody())) }
        val repo = repo(api)
        val error = runCatching { repo.getMenu() }.exceptionOrNull()
        assertEquals(IOException::class, error!!::class)
        api.answer = { dto }
        assertEquals(1, repo.getMenu().size)
    }

    @Test
    fun brokenJson_isIOException() = runTest {
        val repo = repo(FakeApi { throw SerializationException("нет поля") })
        assertEquals(IOException::class, runCatching { repo.getMenu() }.exceptionOrNull()!!::class)
    }
    @Test
    fun explicitRefresh_returnsOneFreshSnapshotAndUpdatesCache() = runTest {
        val api = FakeApi { dto }
        val repository = repo(api)
        repository.getMenu()
        api.answer = { dto.copy(products = listOf(latte.copy(price = 300, name = "Fresh")), sizes = listOf(SizeOptionDto("MEDIUM", 280))) }
        val fresh = repository.refresh()
        assertEquals(300, fresh.products.single().price)
        assertEquals(280, fresh.prices.sizePrice(DrinkSize.MEDIUM))
        assertEquals("Fresh", repository.getProduct("latte")!!.name)
        assertEquals(fresh.prices, repository.getOptionPrices())
        assertEquals(2, api.calls)
    }

    @Test
    fun failedRefresh_doesNotReturnStaleSnapshotOrDestroyCache() = runTest {
        val api = FakeApi { dto }
        val repository = repo(api)
        repository.getMenu()
        api.answer = { throw IOException("offline") }
        assertEquals(IOException::class, runCatching { repository.refresh() }.exceptionOrNull()!!::class)
        assertEquals(240, repository.getMenu().single().price)
        assertEquals(2, api.calls)
    }

}
