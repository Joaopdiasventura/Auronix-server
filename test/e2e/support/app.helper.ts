import { getQueueToken } from '@nestjs/bullmq';
import { ValidationPipe } from '@nestjs/common';
import cookie from '@fastify/cookie';
import { ConfigService } from '@nestjs/config';
import { getStorageToken } from '@nestjs/throttler';
import { Test, TestingModule } from '@nestjs/testing';
import {
  FastifyAdapter,
  NestFastifyApplication,
} from '@nestjs/platform-fastify';
import { Queue } from 'bullmq';
import request, { type SuperTest, type Test as SupertestTest } from 'supertest';
import { PaymentRequestQueueName } from '../../../src/core/payment-request/queue/payment-request-queue-name.enum';
import { TransferQueueName } from '../../../src/core/transfer/queue/transfer-queue-name.enum';
import { AppModule } from '../../../src/app.module';
import { NotificationQueueName } from '../../../src/shared/modules/notification/queue/notification-queue-name.enum';
import { flushE2eCache } from './cache.helper';
import { ensureE2eDatabase, truncateE2eDatabase } from './database.helper';
import { configureE2eEnvironment } from './e2e-environment';

export interface E2eAppContext {
  app: NestFastifyApplication;
  baseUrl: string;
  request: SuperTest<SupertestTest>;
  pauseTransferProcessing(): Promise<void>;
  resumeTransferProcessing(): Promise<void>;
  resetState(): Promise<void>;
  close(): Promise<void>;
}

export async function createE2eAppContext(): Promise<E2eAppContext> {
  configureE2eEnvironment();
  await ensureE2eDatabase();

  const moduleFixture: TestingModule = await Test.createTestingModule({
    imports: [AppModule],
  }).compile();

  const app = moduleFixture.createNestApplication<NestFastifyApplication>(
    new FastifyAdapter(),
  );
  const configService = app.get(ConfigService);

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: { enableImplicitConversion: true },
    }),
  );

  await app.register(cookie, {
    secret: configService.get<string>('cookie.secret'),
  });

  await app.init();
  await app.getHttpAdapter().getInstance().ready();
  await app.listen(0, '127.0.0.1');
  const throttlerStorage = app.get(getStorageToken(), { strict: false });
  const queues = getManagedQueues(app);
  await waitForQueuesReady(queues.all);

  const context: E2eAppContext = {
    app,
    baseUrl: await app.getUrl(),
    request: request(await app.getUrl()) as unknown as SuperTest<SupertestTest>,
    async pauseTransferProcessing(): Promise<void> {
      await pauseQueue(queues.transfer);
    },
    async resumeTransferProcessing(): Promise<void> {
      await resumeQueue(queues.transfer);
    },
    async resetState(): Promise<void> {
      await resetE2eState(queues.all);
      clearThrottlerState(throttlerStorage);
    },
    async close(): Promise<void> {
      await app.close();
    },
  };

  await context.resetState();
  return context;
}

export async function resetE2eState(queues: Queue[] = []): Promise<void> {
  if (queues.length == 0) {
    await truncateE2eDatabase();
    await flushE2eCache();
    return;
  }

  await Promise.all(queues.map((queue) => pauseQueue(queue)));

  try {
    await waitForQueuesIdle(queues);
    await truncateE2eDatabase();
    await flushE2eCache();
  } finally {
    await Promise.all(queues.map((queue) => resumeQueue(queue)));
  }
}

function clearThrottlerState(storageService: unknown): void {
  const throttlerStorage = storageService as {
    storage?: Map<unknown, unknown>;
    timeoutIds?: Map<unknown, unknown>;
  };

  if (throttlerStorage.storage instanceof Map) {
    throttlerStorage.storage.clear();
  }

  if (!(throttlerStorage.timeoutIds instanceof Map)) return;

  for (const timeouts of throttlerStorage.timeoutIds.values()) {
    if (!Array.isArray(timeouts)) continue;

    for (const timeout of timeouts) clearTimeout(timeout as NodeJS.Timeout);
  }

  throttlerStorage.timeoutIds.clear();
}

function getManagedQueues(app: NestFastifyApplication): {
  all: Queue[];
  notification: Queue;
  paymentRequest: Queue;
  transfer: Queue;
} {
  const notification = app.get<Queue>(
    getQueueToken(NotificationQueueName.Publishing),
  );
  const paymentRequest = app.get<Queue>(
    getQueueToken(PaymentRequestQueueName.Expiration),
  );
  const transfer = app.get<Queue>(getQueueToken(TransferQueueName.Processing));

  return {
    all: [notification, paymentRequest, transfer],
    notification,
    paymentRequest,
    transfer,
  };
}

async function waitForQueuesReady(queues: Queue[]): Promise<void> {
  await Promise.all(queues.map((queue) => queue.waitUntilReady()));
}

async function waitForQueuesIdle(queues: Queue[]): Promise<void> {
  const deadline = Date.now() + 15000;

  while (Date.now() < deadline) {
    const activeCounts = await Promise.all(
      queues.map((queue) => queue.getActiveCount()),
    );

    if (activeCounts.every((count) => count == 0)) return;
    await sleep(50);
  }

  throw new Error('Timed out waiting for BullMQ queues to become idle');
}

async function pauseQueue(queue: Queue): Promise<void> {
  await queue.waitUntilReady();
  await queue.pause();
}

async function resumeQueue(queue: Queue): Promise<void> {
  await queue.waitUntilReady();
  await queue.resume();
}

async function sleep(durationInMs: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, durationInMs));
}
