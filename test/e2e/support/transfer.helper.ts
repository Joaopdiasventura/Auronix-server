import type { SuperTest, Test as SupertestTest } from 'supertest';

export interface TransferResponseBody {
  id: string;
  value: number;
  description: string;
  status: string;
  failureReason: string | null;
  completedAt: string | null;
  payer: { id: string };
  payee: { id: string };
  createdAt: string;
}

export async function waitForTransferStatus(
  client: SuperTest<SupertestTest>,
  cookie: string,
  transferId: string,
  statuses: string[],
  timeoutMs = 15000,
): Promise<TransferResponseBody> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const response = await client
      .get(`/transfer/${transferId}`)
      .set('Cookie', cookie);

    if (response.status == 200 && statuses.includes(response.body.status))
      return response.body as TransferResponseBody;

    await sleep(100);
  }

  throw new Error(
    `Timed out waiting for transfer ${transferId} to reach one of: ${statuses.join(', ')}`,
  );
}

export async function sleep(durationInMs: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, durationInMs));
}
