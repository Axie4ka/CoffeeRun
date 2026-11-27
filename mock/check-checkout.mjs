import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const baseUrl = (process.argv[2] ?? 'http://127.0.0.1:3000').replace(/\/$/, '');
const headers = {
  Authorization: 'Bearer demo-token',
  'Content-Type': 'application/json',
};

async function post(key, request, action = '') {
  const response = await fetch(`${baseUrl}/order-attempts/${key}${action}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
  });
  return { status: response.status, body: await response.json() };
}

const menuResponse = await fetch(`${baseUrl}/menu`);
assert.equal(menuResponse.status, 200);
const menu = await menuResponse.json();
const croissant = menu.products.find(product => product.id === 'croissant');
const request = {
  shop: 'MOSKOVSKAYA',
  pickupAt: null,
  lines: [{ productId: croissant.id, quantity: 1, unitPrice: croissant.price }],
};

const key = randomUUID();
const duplicates = await Promise.all([post(key, request), post(key, request)]);
assert.deepEqual(duplicates.map(result => result.status).sort(), [200, 201]);
const orderNumber = duplicates[0].body.number;
assert.equal(duplicates[1].body.number, orderNumber);

const changed = structuredClone(request);
changed.lines[0].unitPrice++;
const mismatch = await post(key, changed);
assert.equal(mismatch.status, 409);
assert.equal(mismatch.body.code, 'ATTEMPT_PAYLOAD_MISMATCH');
const incorrectPrice = await post(randomUUID(), changed);
assert.equal(incorrectPrice.status, 409);
assert.equal(incorrectPrice.body.code, 'ORDER_UNAVAILABLE');

const recovered = await post(key, null, '/resolve');
assert.equal(recovered.status, 200);
assert.equal(recovered.body.number, orderNumber);

const closedKey = randomUUID();
assert.equal((await post(closedKey, null, '/resolve')).status, 410);
assert.equal((await post(closedKey, request)).status, 410);

const racingKey = randomUUID();
const [submission, resolution] = await Promise.all([
  post(racingKey, request),
  post(racingKey, null, '/resolve'),
]);
if (submission.status === 201) {
  assert.equal(resolution.status, 200);
  assert.equal(resolution.body.number, submission.body.number);
} else {
  assert.equal(submission.status, 410);
  assert.equal(resolution.status, 410);
}

const cappuccino = menu.products.find(product => product.id === 'cappuccino');
const priceOf = (options, code) => options.find(option => option.code === code).price;
const findPromotion = type => menu.promotions.find(promotion => promotion.type === type);
assert.equal(findPromotion('CAPPUCCINO_MEDIUM').productId, cappuccino.id);
assert.equal(findPromotion('PASTRY_2_PLUS_1').category, 'PASTRY');
const cappuccinoBase = cappuccino.price + priceOf(menu.sizes, 'MEDIUM') - priceOf(menu.sizes, 'SMALL');
const configured = {
  ...request,
  lines: [{
    productId: cappuccino.id,
    quantity: 1,
    unitPrice: cappuccinoBase + priceOf(menu.milks, 'OAT') + priceOf(menu.extras, 'VANILLA'),
    discount: Math.floor(cappuccinoBase / 5),
    options: { size: 'MEDIUM', milk: 'OAT', iced: false, extras: ['VANILLA'], note: '' },
  }],
};
const promotedKey = randomUUID();
const promoted = await post(promotedKey, configured);
assert.equal(promoted.status, 201);
assert.equal(promoted.body.lines[0].discount, configured.lines[0].discount);
const tampered = structuredClone(configured);
tampered.lines[0].discount++;
assert.equal((await post(randomUUID(), tampered)).status, 409);
assert.equal((await post(promotedKey, tampered)).body.code, 'ATTEMPT_PAYLOAD_MISMATCH');
assert.equal((await post(promotedKey, null, '/resolve')).body.lines[0].discount, configured.lines[0].discount);

const pastryLine = (id, quantity, free = 0) => {
  const product = menu.products.find(item => item.id === id);
  return { productId: id, quantity, unitPrice: product.price, discount: product.price * free };
};
const twoTrios = { ...request, lines: [
  pastryLine('croissant', 3), pastryLine('chocolate_muffin', 2, 2), pastryLine('cinnamon_roll', 1),
] };
const pastryOrder = await post(randomUUID(), twoTrios);
assert.equal(pastryOrder.status, 201);
assert.deepEqual(pastryOrder.body.lines.map(line => line.discount), twoTrios.lines.map(line => line.discount));
const wrongFreeItems = { ...twoTrios, lines: [
  pastryLine('croissant', 3, 2), pastryLine('chocolate_muffin', 2), pastryLine('cinnamon_roll', 1),
] };
assert.equal((await post(randomUUID(), wrongFreeItems)).status, 409);

console.log('Mockoon checkout scenarios passed');
