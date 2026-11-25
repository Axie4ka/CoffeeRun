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
const configured = {
  ...request,
  lines: [{
    productId: cappuccino.id,
    quantity: 1,
    unitPrice: cappuccino.price + priceOf(menu.sizes, 'MEDIUM') - priceOf(menu.sizes, 'SMALL')
      + priceOf(menu.milks, 'OAT') + priceOf(menu.extras, 'VANILLA'),
    options: { size: 'MEDIUM', milk: 'OAT', iced: false, extras: ['VANILLA'], note: '' },
  }],
};
assert.equal((await post(randomUUID(), configured)).status, 201);

console.log('Mockoon checkout scenarios passed');
