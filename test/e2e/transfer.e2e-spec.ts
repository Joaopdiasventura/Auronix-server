import { randomUUID } from 'crypto';
import { E2eAppContext, createE2eAppContext } from './support/app.helper';
import { registerUser } from './support/auth.helper';
import { createSseCollector, type SseEvent } from './support/sse.helper';
import {
  TransferResponseBody,
  waitForTransferStatus,
} from './support/transfer.helper';

describe('Transfer (e2e)', () => {
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

  it('POST /transfer creates a pending transfer and then completes it asynchronously', async () => {
    const payer = await registerUser(context.request);
    const payee = await registerUser(context.request);

    const response = await context.request
      .post('/transfer')
      .set('Cookie', payer.cookie)
      .send({
        payeeId: payee.user.id,
        value: 5000,
        description: 'Pagamento principal',
      });

    expect(response.status).toBe(201);
    expect(response.body).toEqual(
      expect.objectContaining({
        id: expect.any(String),
        value: 5000,
        description: 'Pagamento principal',
        status: 'pending',
        failureReason: null,
        completedAt: null,
        payer: expect.objectContaining({ id: payer.user.id }),
        payee: expect.objectContaining({ id: payee.user.id }),
      }),
    );
    expect(response.body.payer.password).toBeUndefined();
    expect(response.body.payee.password).toBeUndefined();

    const completedTransfer = await waitForTransferStatus(
      context.request,
      payer.cookie,
      response.body.id,
      ['completed'],
    );

    expect(completedTransfer.status).toBe('completed');
    expect(completedTransfer.failureReason).toBeNull();
    expect(completedTransfer.completedAt).toEqual(expect.any(String));

    const payerProfile = await context.request
      .get('/user')
      .set('Cookie', payer.cookie);
    const payeeProfile = await context.request
      .get('/user')
      .set('Cookie', payee.cookie);

    expect(payerProfile.body.balance).toBe(95000);
    expect(payeeProfile.body.balance).toBe(105000);
  });

  it('POST /transfer rejects unauthenticated requests', async () => {
    const response = await context.request.post('/transfer').send({
      payeeId: randomUUID(),
      value: 5000,
      description: 'Pagamento principal',
    });

    expect(response.status).toBe(403);
  });

  it('POST /transfer rejects invalid payloads', async () => {
    const payer = await registerUser(context.request);

    const response = await context.request
      .post('/transfer')
      .set('Cookie', payer.cookie)
      .send({
        payeeId: 'invalid-id',
        value: 0,
        description: '',
      });

    expect(response.status).toBe(400);
  });

  it('POST /transfer rejects non-whitelisted internal fields', async () => {
    const payer = await registerUser(context.request);
    const payee = await registerUser(context.request);

    const response = await context.request
      .post('/transfer')
      .set('Cookie', payer.cookie)
      .send({
        payerId: randomUUID(),
        payeeId: payee.user.id,
        value: 5000,
        description: 'Pagamento principal',
        status: 'completed',
      });

    expect(response.status).toBe(400);
    expect(response.body.message).toEqual(
      expect.arrayContaining([
        'property payerId should not exist',
        'property status should not exist',
      ]),
    );
  });

  it('POST /transfer rejects self transfers', async () => {
    const payer = await registerUser(context.request);

    const response = await context.request
      .post('/transfer')
      .set('Cookie', payer.cookie)
      .send({
        payeeId: payer.user.id,
        value: 5000,
        description: 'Pagamento principal',
      });

    expect(response.status).toBe(400);
  });

  it('POST /transfer rejects transfers with insufficient synchronous balance', async () => {
    const payer = await registerUser(context.request);
    const payee = await registerUser(context.request);

    const response = await context.request
      .post('/transfer')
      .set('Cookie', payer.cookie)
      .send({
        payeeId: payee.user.id,
        value: 100001,
        description: 'Pagamento principal',
      });

    expect(response.status).toBe(400);
  });

  it('POST /transfer rejects transfers to an unknown payee', async () => {
    const payer = await registerUser(context.request);

    const response = await context.request
      .post('/transfer')
      .set('Cookie', payer.cookie)
      .send({
        payeeId: randomUUID(),
        value: 5000,
        description: 'Pagamento principal',
      });

    expect(response.status).toBe(404);
  });

  it('GET /transfer/:id returns the transfer to both payer and payee and rejects third parties', async () => {
    const payer = await registerUser(context.request);
    const payee = await registerUser(context.request);
    const outsider = await registerUser(context.request);

    const createResponse = await context.request
      .post('/transfer')
      .set('Cookie', payer.cookie)
      .send({
        payeeId: payee.user.id,
        value: 5000,
        description: 'Pagamento principal',
      });

    const transferId = createResponse.body.id as string;

    await waitForTransferStatus(context.request, payer.cookie, transferId, [
      'completed',
    ]);

    const payerResponse = await context.request
      .get(`/transfer/${transferId}`)
      .set('Cookie', payer.cookie);
    const payeeResponse = await context.request
      .get(`/transfer/${transferId}`)
      .set('Cookie', payee.cookie);
    const outsiderResponse = await context.request
      .get(`/transfer/${transferId}`)
      .set('Cookie', outsider.cookie);

    expect(payerResponse.status).toBe(200);
    expect(payeeResponse.status).toBe(200);
    expect(outsiderResponse.status).toBe(403);
    expect(payerResponse.body.payer.password).toBeUndefined();
    expect(payerResponse.body.payee.password).toBeUndefined();
    expect(payeeResponse.body.payer.password).toBeUndefined();
    expect(payeeResponse.body.payee.password).toBeUndefined();
  });

  it('GET /transfer/:id returns 404 for a missing transfer and 400 for an invalid UUID', async () => {
    const payer = await registerUser(context.request);

    const missingResponse = await context.request
      .get(`/transfer/${randomUUID()}`)
      .set('Cookie', payer.cookie);
    const invalidResponse = await context.request
      .get('/transfer/invalid-id')
      .set('Cookie', payer.cookie);

    expect(missingResponse.status).toBe(404);
    expect(invalidResponse.status).toBe(400);
  });

  it('GET /transfer rejects unauthenticated requests and invalid queries', async () => {
    const payer = await registerUser(context.request);

    const unauthorizedResponse = await context.request.get('/transfer');
    const invalidQueryResponse = await context.request
      .get('/transfer')
      .set('Cookie', payer.cookie)
      .query({
        cursor: 'invalid',
        limit: -1,
      });

    expect(unauthorizedResponse.status).toBe(403);
    expect(invalidQueryResponse.status).toBe(400);
  });

  it('GET /transfer returns paginated completed transfers for both payer and payee without exposing unrelated data', async () => {
    const payer = await registerUser(context.request);
    const firstPayee = await registerUser(context.request);
    const secondPayee = await registerUser(context.request);
    const outsiderPayer = await registerUser(context.request);
    const outsiderPayee = await registerUser(context.request);

    const firstTransfer = await createAndCompleteTransfer(
      context,
      payer.cookie,
      firstPayee.user.id,
      3000,
      'Primeira',
    );
    const secondTransfer = await createAndCompleteTransfer(
      context,
      payer.cookie,
      secondPayee.user.id,
      4000,
      'Segunda',
    );
    await createAndCompleteTransfer(
      context,
      outsiderPayer.cookie,
      outsiderPayee.user.id,
      2500,
      'Terceira',
    );

    const payerPage = await context.request
      .get('/transfer')
      .set('Cookie', payer.cookie)
      .query({
        limit: 1,
      });
    const payerNextCursor = payerPage.body.next;
    const payerSecondPage = await context.request
      .get('/transfer')
      .set('Cookie', payer.cookie)
      .query({
        cursor: JSON.stringify(payerNextCursor),
        limit: 1,
      });
    const payeePage = await context.request
      .get('/transfer')
      .set('Cookie', firstPayee.cookie)
      .query({
        limit: 10,
      });

    expect(payerPage.status).toBe(200);
    expect(payerPage.body.data).toHaveLength(1);
    expect(payerPage.body.next).toEqual(
      expect.objectContaining({
        completedAt: expect.any(String),
        id: secondTransfer.id,
      }),
    );
    expect(payerPage.body.data[0].id).toBe(secondTransfer.id);
    expect(payerPage.body.data[0].payer.password).toBeUndefined();
    expect(payerPage.body.data[0].payee.password).toBeUndefined();

    expect(payerSecondPage.status).toBe(200);
    expect(payerSecondPage.body.data).toHaveLength(1);
    expect(payerSecondPage.body.next).toBeNull();
    expect(payerSecondPage.body.data[0].id).toBe(firstTransfer.id);
    expect(payerSecondPage.body.data[0].payer.password).toBeUndefined();
    expect(payerSecondPage.body.data[0].payee.password).toBeUndefined();

    expect(payeePage.status).toBe(200);
    expect(payeePage.body.data).toHaveLength(1);
    expect(payeePage.body.next).toBeNull();
    expect(payeePage.body.data[0].id).toBe(firstTransfer.id);
    expect(payeePage.body.data[0].payer.password).toBeUndefined();
    expect(payeePage.body.data[0].payee.password).toBeUndefined();
  });

  it('Transfer processing prevents double spend and marks the second concurrent transfer as failed', async () => {
    const scenario = await createConcurrentTransferRace(context);

    const [firstTransfer, secondTransfer] = await Promise.all([
      waitForTransferStatus(
        context.request,
        scenario.payer.cookie,
        scenario.firstTransferId,
        ['completed', 'failed'],
      ),
      waitForTransferStatus(
        context.request,
        scenario.payer.cookie,
        scenario.secondTransferId,
        ['completed', 'failed'],
      ),
    ]);

    const completedTransfer =
      firstTransfer.status == 'completed' ? firstTransfer : secondTransfer;
    const failedTransfer =
      firstTransfer.status == 'failed' ? firstTransfer : secondTransfer;

    expect(completedTransfer.status).toBe('completed');
    expect(failedTransfer.status).toBe('failed');
    expect(failedTransfer.failureReason).toEqual(expect.any(String));

    const payerProfile = await context.request
      .get('/user')
      .set('Cookie', scenario.payer.cookie);
    const firstPayeeProfile = await context.request
      .get('/user')
      .set('Cookie', scenario.firstPayee.cookie);
    const secondPayeeProfile = await context.request
      .get('/user')
      .set('Cookie', scenario.secondPayee.cookie);

    expect(payerProfile.body.balance).toBe(30000);

    const completedRecipientId = completedTransfer.payee.id;
    const completedRecipientBalance =
      completedRecipientId == scenario.firstPayee.user.id
        ? firstPayeeProfile.body.balance
        : secondPayeeProfile.body.balance;
    const failedRecipientBalance =
      completedRecipientId == scenario.firstPayee.user.id
        ? secondPayeeProfile.body.balance
        : firstPayeeProfile.body.balance;

    expect(completedRecipientBalance).toBe(170000);
    expect(failedRecipientBalance).toBe(100000);
  });

  it('GET /notification/stream rejects requests without authentication', async () => {
    const response = await context.request.get('/notification/stream');
    expect(response.status).toBe(403);
  });

  it('Transfer success emits completed SSE events for the payer and the payee', async () => {
    const payer = await registerUser(context.request);
    const payee = await registerUser(context.request);

    const payerCollector = await createSseCollector({
      baseUrl: context.baseUrl,
      cookie: payer.cookie,
    });
    const payeeCollector = await createSseCollector({
      baseUrl: context.baseUrl,
      cookie: payee.cookie,
    });

    try {
      const createResponse = await context.request
        .post('/transfer')
        .set('Cookie', payer.cookie)
        .send({
          payeeId: payee.user.id,
          value: 5000,
          description: 'Pagamento SSE',
        });

      expect(createResponse.status).toBe(201);

      const transferId = createResponse.body.id as string;

      const [payerEvents, payeeEvents] = await Promise.all([
        payerCollector.waitFor((events) =>
          hasTransferEvent(events, 'transfer.completed', transferId),
        ),
        payeeCollector.waitFor((events) =>
          hasTransferEvent(events, 'transfer.completed', transferId),
        ),
      ]);

      expect(
        findTransferEvent(payerEvents, 'transfer.completed', transferId),
      ).toBeDefined();
      expect(
        findTransferEvent(payeeEvents, 'transfer.completed', transferId),
      ).toBeDefined();
    } finally {
      await Promise.all([payerCollector.close(), payeeCollector.close()]);
    }
  });

  it('Transfer failure emits a failed SSE event for the payer', async () => {
    const scenario = await createConcurrentTransferRace(context, true);

    const [firstTransfer, secondTransfer] = await Promise.all([
      waitForTransferStatus(
        context.request,
        scenario.payer.cookie,
        scenario.firstTransferId,
        ['completed', 'failed'],
      ),
      waitForTransferStatus(
        context.request,
        scenario.payer.cookie,
        scenario.secondTransferId,
        ['completed', 'failed'],
      ),
    ]);

    const failedTransfer =
      firstTransfer.status == 'failed' ? firstTransfer : secondTransfer;

    try {
      if (!scenario.payerCollector)
        throw new Error(
          'payerCollector was not created for SSE failure scenario',
        );

      const events = await scenario.payerCollector.waitFor((streamEvents) =>
        hasTransferEvent(streamEvents, 'transfer.failed', failedTransfer.id),
      );

      expect(
        findTransferEvent(events, 'transfer.failed', failedTransfer.id),
      ).toBeDefined();
    } finally {
      await scenario.payerCollector.close();
    }
  });

  it('Notification replay returns only events after the provided Last-Event-ID', async () => {
    const payer = await registerUser(context.request);
    const payee = await registerUser(context.request);

    const firstCollector = await createSseCollector({
      baseUrl: context.baseUrl,
      cookie: payer.cookie,
    });

    let lastEventId = '';

    try {
      const firstTransfer = await context.request
        .post('/transfer')
        .set('Cookie', payer.cookie)
        .send({
          payeeId: payee.user.id,
          value: 5000,
          description: 'Primeiro replay',
        });

      const initialEvents = await firstCollector.waitFor(
        (events) =>
          hasTransferEvent(events, 'transfer.completed', firstTransfer.body.id),
      );

      lastEventId = initialEvents[initialEvents.length - 1].id;
    } finally {
      await firstCollector.close();
    }

    const secondTransfer = await context.request
      .post('/transfer')
      .set('Cookie', payer.cookie)
      .send({
        payeeId: payee.user.id,
        value: 4500,
        description: 'Segundo replay',
      });

    await waitForTransferStatus(
      context.request,
      payer.cookie,
      secondTransfer.body.id,
      ['completed'],
    );

    const replayCollector = await createSseCollector({
      baseUrl: context.baseUrl,
      cookie: payer.cookie,
      lastEventId,
    });

    try {
      const replayEvents = await replayCollector.waitFor(
        (events) =>
          hasTransferEvent(events, 'transfer.completed', secondTransfer.body.id),
      );

      const secondTransferEvents = replayEvents.filter(
        (event) =>
          getTransferId(event) == secondTransfer.body.id &&
          event.event == 'transfer.completed',
      );

      expect(secondTransferEvents).toHaveLength(1);
      expect(
        secondTransferEvents.every(
          (event) => Number(event.id) > Number(lastEventId),
        ),
      ).toBe(true);
    } finally {
      await replayCollector.close();
    }
  });
});

async function createAndCompleteTransfer(
  context: E2eAppContext,
  payerCookie: string,
  payeeId: string,
  value: number,
  description: string,
): Promise<TransferResponseBody> {
  const createResponse = await context.request
    .post('/transfer')
    .set('Cookie', payerCookie)
    .send({
      payeeId,
      value,
      description,
    });

  if (createResponse.status != 201)
    throw new Error(
      `Failed to create transfer: ${createResponse.status} ${JSON.stringify(createResponse.body)}`,
    );

  return waitForTransferStatus(
    context.request,
    payerCookie,
    createResponse.body.id,
    ['completed'],
  );
}

async function createConcurrentTransferRace(
  context: E2eAppContext,
  collectSse = false,
): Promise<{
  payer: Awaited<ReturnType<typeof registerUser>>;
  firstPayee: Awaited<ReturnType<typeof registerUser>>;
  secondPayee: Awaited<ReturnType<typeof registerUser>>;
  firstTransferId: string;
  secondTransferId: string;
  payerCollector?: Awaited<ReturnType<typeof createSseCollector>>;
}> {
  const payer = await registerUser(context.request);
  const firstPayee = await registerUser(context.request);
  const secondPayee = await registerUser(context.request);
  const payerCollector = collectSse
    ? await createSseCollector({
        baseUrl: context.baseUrl,
        cookie: payer.cookie,
      })
    : undefined;

  await context.pauseTransferProcessing();

  try {
    const [firstResponse, secondResponse] = await Promise.all([
      context.request
        .post('/transfer')
        .set('Cookie', payer.cookie)
        .send({
          payeeId: firstPayee.user.id,
          value: 70000,
          description: 'Primeira corrida',
        }),
      context.request
        .post('/transfer')
        .set('Cookie', payer.cookie)
        .send({
          payeeId: secondPayee.user.id,
          value: 70000,
          description: 'Segunda corrida',
        }),
    ]);

    if (firstResponse.status != 201 || secondResponse.status != 201) {
      if (payerCollector) await payerCollector.close();
      throw new Error(
        `Concurrent transfer setup failed: ${firstResponse.status}/${secondResponse.status}`,
      );
    }

    return {
      payer,
      firstPayee,
      secondPayee,
      firstTransferId: firstResponse.body.id,
      secondTransferId: secondResponse.body.id,
      payerCollector,
    };
  } finally {
    await context.resumeTransferProcessing();
  }
}

function hasTransferEvent(
  events: SseEvent[],
  type: string,
  transferId: string,
): boolean {
  return findTransferEvent(events, type, transferId) != undefined;
}

function findTransferEvent(
  events: SseEvent[],
  type: string,
  transferId: string,
): SseEvent | undefined {
  return events.find(
    (event) => event.event == type && getTransferId(event) == transferId,
  );
}

function countTransferEvents(events: SseEvent[], transferId: string): number {
  return events.filter((event) => getTransferId(event) == transferId).length;
}

function getTransferId(event: SseEvent): string | undefined {
  if (typeof event.data != 'object' || event.data == null) return undefined;

  const payload = event.data as {
    data?: {
      transferId?: string;
    };
  };

  return payload.data?.transferId;
}
