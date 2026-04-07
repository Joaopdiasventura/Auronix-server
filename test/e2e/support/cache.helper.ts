import Redis from 'ioredis';
import { configureE2eEnvironment, getE2eRedisUrl } from './e2e-environment';

export async function flushE2eCache(): Promise<void> {
  configureE2eEnvironment();

  const client = new Redis(getE2eRedisUrl(), {
    lazyConnect: true,
    maxRetriesPerRequest: null,
  });

  await client.connect();

  try {
    await client.flushdb();
  } finally {
    await client.quit();
  }
}
