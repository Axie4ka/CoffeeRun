package ru.coffeerun.domain.promotion

import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import ru.coffeerun.model.MenuSnapshot

private val pricingResume = MutableStateFlow(0L)

internal fun refreshPromotionClock() = pricingResume.update { it + 1 }

/** Recheck wall time at each boundary and when Android returns to the foreground. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun MenuSnapshot.pricingTimes(
    now: () -> Long = System::currentTimeMillis,
    resume: Flow<Long> = pricingResume
) = resume.flatMapLatest {
    val boundaries = promotions.flatMap { listOf(it.startsAtMillis, it.endsAtMillis) }
        .filter { it != Long.MAX_VALUE }.distinct().sorted()
    flow {
        while (true) {
            val current = now()
            emit(current)
            val next = boundaries.firstOrNull { it > current } ?: break
            delay(next - current)
        }
    }
}
