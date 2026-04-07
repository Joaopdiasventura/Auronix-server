import { randomUUID } from 'crypto';
import { Client } from 'pg';
import { E2eAppContext, createE2eAppContext } from './support/app.helper';
import { registerUser } from './support/auth.helper';
import { getE2ePostgresUrl } from './support/e2e-environment';

describe('PaymentRequest (e2e)', () => {
  let context: E2eAppContext;

  beforeAll(async () => {
    context = await createE2eAppContext();
  });

  beforeEach(async () => {
    await context.resetState();
  });

  afterAll(async () => {
    if (context) await context.close();
  });

  it('POST /payment-request creates a payment request for the authenticated user', async () => {
    const fixture = await registerUser(context.request);

    const response = await context.request
      .post('/payment-request')
      .set('Cookie', fixture.cookie)
      .send({
        value: 5000,
      });

    expect(response.status).toBe(201);
    expect(response.body).toEqual(
      expect.objectContaining({
        id: expect.any(String),
        value: 5000,
        createdAt: expect.any(String),
      }),
    );
  });

  it('POST /payment-request rejects unauthenticated requests', async () => {
    const response = await context.request.post('/payment-request').send({
      value: 5000,
    });

    expect(response.status).toBe(403);
  });

  it('POST /payment-request rejects invalid payloads', async () => {
    const fixture = await registerUser(context.request);

    const response = await context.request
      .post('/payment-request')
      .set('Cookie', fixture.cookie)
      .send({
        value: 0,
      });

    expect(response.status).toBe(400);
  });

  it('POST /payment-request rejects non-whitelisted fields', async () => {
    const fixture = await registerUser(context.request);

    const response = await context.request
      .post('/payment-request')
      .set('Cookie', fixture.cookie)
      .send({
        value: 5000,
        userId: randomUUID(),
      });

    expect(response.status).toBe(400);
    expect(response.body.message).toContain('property userId should not exist');
  });

  it('GET /payment-request/:id returns an existing payment request', async () => {
    const fixture = await registerUser(context.request);
    const createResponse = await context.request
      .post('/payment-request')
      .set('Cookie', fixture.cookie)
      .send({
        value: 5000,
      });

    const response = await context.request
      .get(`/payment-request/${createResponse.body.id}`)
      .set('Cookie', fixture.cookie);

    expect(response.status).toBe(200);
    expect(response.body).toEqual(
      expect.objectContaining({
        id: createResponse.body.id,
        value: 5000,
      }),
    );
  });

  it('GET /payment-request/:id rejects unauthenticated requests', async () => {
    const response = await context.request.get(
      `/payment-request/${randomUUID()}`,
    );
    expect(response.status).toBe(403);
  });

  it('GET /payment-request/:id returns 404 for a missing payment request', async () => {
    const fixture = await registerUser(context.request);

    const response = await context.request
      .get(`/payment-request/${randomUUID()}`)
      .set('Cookie', fixture.cookie);

    expect(response.status).toBe(404);
  });

  it('GET /payment-request/:id returns 400 for an invalid UUID', async () => {
    const fixture = await registerUser(context.request);

    const response = await context.request
      .get('/payment-request/invalid-id')
      .set('Cookie', fixture.cookie);

    expect(response.status).toBe(400);
  });

  it('GET /payment-request/:id returns 404 for an expired payment request even before the delayed deletion runs', async () => {
    const fixture = await registerUser(context.request);

    const createResponse = await context.request
      .post('/payment-request')
      .set('Cookie', fixture.cookie)
      .send({
        value: 5000,
      });

    await backdatePaymentRequestExpiration(createResponse.body.id);

    const response = await context.request
      .get(`/payment-request/${createResponse.body.id}`)
      .set('Cookie', fixture.cookie);

    expect(response.status).toBe(404);
  });
});

async function backdatePaymentRequestExpiration(id: string): Promise<void> {
  const client = new Client({
    connectionString: getE2ePostgresUrl(),
  });

  await client.connect();

  try {
    await client.query(
      `
        UPDATE payment_requests
        SET expires_at = now() - interval '1 second'
        WHERE id = $1
      `,
      [id],
    );
  } finally {
    await client.end();
  }
}
