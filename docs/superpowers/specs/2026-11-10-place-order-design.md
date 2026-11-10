# PlaceOrderUseCase и PickupPolicy

Согласовано в чате: сначала вынести покупку из CheckoutViewModel, затем общие правила времени; сохранить снимок корзины, новые количества и защиту сессии, убрать молчаливый переход к ASAP.

## Контракты

- PlaceOrderUseCase(cart, orders, sessions, pickupPolicy).invoke(shop, pickupAtMillis): CheckoutResult. Снимок снимается под withSession; исходная AuthSession передаётся явно в Orders.place и сохраняется до атомарного removeOrdered. Отмена не превращается в ошибку сети.
- CheckoutResult: Placed(order), EmptyCart, InvalidPickupTime, Unavailable, NetworkError. Сетевой PlaceOrderResult остаётся отдельным существующим типом. Тексты и навигация остаются в UI.
- PickupPolicy: выбор сегодняшнего timestamp, допустимость scheduled/ASAP, earliestPickup с округлением до 5 минут. Часы и функция расписания по Shop передаются извне; телефонный ZoneId не используется.
- PickupHours: ZoneId кофейни, closesAt, opensAt nullable. Демонстрационный fallback: Europe/Moscow, закрытие 22:00, открытие неизвестно. Если opensAt указан, учитывается открытие и 15 минут приготовления; ночные смены этим этапом не добавляются. Реальное расписание и серверная валидация остаются B09/F05.
- AuthRepository расширяет существующий SessionProvider: NetworkAuthRepository делегирует TokenStore, фейк соблюдает тот же атомарный контракт. Это позволяет AppContainer собрать use case без нового независимого владельца сессии.
- Orders.place получает обязательный session: AuthSession. Никакого повторного выбора текущего аккаунта после снятия снимка.

## Границы

Нет новых библиотек, Room, смены серверного контракта или автоматических повторов POST. Неизвестный исход после тайм-аута остаётся B05; клиент не гарантирует идемпотентность. Исторические и актуальные цены не пересчитываются этим этапом.

## Проверка

Use case: пустая корзина, снимок параметров, добавления/повторное добавление строк, ошибка, cancellation, смена сессии до HTTP и перед удалением корзины, устаревшее время. Policy: границы закрытия, открытие при известном расписании, ASAP, смена суток, часовой пояс телефона, округление. UI: сохранённое устаревшее время и сообщение без отправки; основной checkout, смена кофейни и busy. Unit-suite, debug APK, полный UI-набор и независимое ревью.

## Уточнения после ревью

- Время перепроверяется OrdersCoordinator внутри Mutex непосредственно перед repository.place: во время ожидания оно могло устареть. Координатор возвращает PlaceOrderResult.InvalidPickupTime, use case переводит его в CheckoutResult.InvalidPickupTime. Это дополнительная проверка на границе отправки тем же PickupPolicy; сетевой репозиторий о расписании не знает.
- В production use case, coordinator и UI используют один экземпляр policy. LocalShopZone передаёт её resolver через существующий стиль CompositionLocalProvider; подтверждение, список и детали показывают время той же кофейни, а не телефона.
