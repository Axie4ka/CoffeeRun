import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const baseUrl = process.argv[2] ?? 'http://127.0.0.1:3000';
const token = 'Bearer demo-token';

async function request(path, method = 'GET', body = undefined, authorization = token) {
  return fetch(new URL(path, baseUrl), {
    method,
    headers: { Authorization: authorization, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

const first = { id: randomUUID(), productId: 'cappuccino', name: 'Morning',
  options: { size: 'MEDIUM', milk: 'OAT', isIced: false, extras: [], note: 'No sugar' } };
const second = { ...first, id: randomUUID(), name: 'Iced',
  options: { ...first.options, isIced: true } };
const collection = '/me/recipes';
const item = recipe => `${collection}/${recipe.id}`;
const avatarPath = '/me/avatar';
const originalAvatar = await (await request(avatarPath)).json();

try {
  assert.equal((await request(collection, 'GET', undefined, 'Bearer invalid')).status, 401);
  assert.equal((await request(avatarPath, 'GET', undefined, 'Bearer invalid')).status, 401);
  assert.equal((await request(item(first), 'PUT', first)).status, 204);
  assert.equal((await request(item(second), 'PUT', second)).status, 204);
  const fromSecondClient = await (await request(collection)).json();
  assert.deepEqual(fromSecondClient.find(recipe => recipe.id === first.id), first);
  assert.deepEqual(fromSecondClient.find(recipe => recipe.id === second.id), second);
  const edited = { ...first, options: { ...first.options, milk: 'ALMOND' }, futureField: 'preserved' };
  assert.equal((await request(item(first), 'PUT', edited)).status, 204);
  const stale = fromSecondClient.find(recipe => recipe.id === first.id);
  const renamed = { ...edited, name: 'Weekend' };
  const renameBody = { name: renamed.name };
  assert.equal((await request(item(stale), 'PATCH', renameBody, 'Bearer invalid')).status, 401);
  for (const invalid of [{}, { name: 42 }, { name: '   ' }, { name: 'x'.repeat(61) }, renamed]) {
    assert.equal((await request(item(stale), 'PATCH', invalid)).status, 400);
  }
  const confirmed = await request(item(stale), 'PATCH', renameBody);
  assert.equal(confirmed.status, 200);
  assert.deepEqual(await confirmed.json(), renamed);
  assert.deepEqual((await (await request(collection)).json()).find(recipe => recipe.id === first.id), renamed);
  assert.equal((await request(item(first), 'DELETE')).status, 204);
  assert.equal((await request(item(stale), 'PATCH', renameBody)).status, 404);
  assert.equal((await (await request(collection)).json()).some(recipe => recipe.id === first.id), false);

  const jpeg = await readFile(new URL('../app/src/androidTest/assets/avatar.jpg', import.meta.url));
  const avatar = { jpegBase64: jpeg.toString('base64') };
  const uploaded = await request(avatarPath, 'PUT', avatar);
  assert.equal(uploaded.status, 200);
  assert.deepEqual(Buffer.from((await uploaded.json()).jpegBase64, 'base64'), jpeg);
  const downloaded = await request(avatarPath);
  assert.equal(downloaded.status, 200);
  assert.deepEqual(Buffer.from((await downloaded.json()).jpegBase64, 'base64'), jpeg);
  assert.equal((await request(avatarPath, 'DELETE')).status, 204);
  assert.deepEqual(await (await request(avatarPath)).json(), { jpegBase64: null });

  const menu = await (await request('/menu')).json();
  assert.deepEqual(menu.shopSchedules.map(shop => shop.preparationMinutes), [12, 18]);
  const pastry = menu.products.find(product => product.id === 'croissant');
  const orderResponse = await request(`/order-attempts/${randomUUID()}`, 'POST', {
    shop: 'STROITELEY', pickupAt: null,
    lines: [{ productId: pastry.id, quantity: 1, unitPrice: pastry.price }],
  });
  assert.equal(orderResponse.status, 201);
  const order = await orderResponse.json();
  const delay = Date.parse(order.promisedReadyAt) - Date.parse(order.placedAt);
  assert.ok(delay >= 18 * 60_000 && delay < 18 * 60_000 + 2_000, JSON.stringify(order));
  console.log('Mockoon personalization and pickup checks passed');
} finally {
  await request(item(first), 'DELETE');
  await request(item(second), 'DELETE');
  if (originalAvatar.jpegBase64) await request(avatarPath, 'PUT', originalAvatar);
  else await request(avatarPath, 'DELETE');
}
