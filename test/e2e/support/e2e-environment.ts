const DEFAULT_E2E_POSTGRES_URL =
  'postgres://postgres:postgres@localhost:5432/auronix_e2e';
const DEFAULT_E2E_REDIS_URL = 'redis://localhost:6379/1';

let configured = false;

export function configureE2eEnvironment(): void {
  if (configured) return;

  process.env.NODE_ENV = 'test';
  process.env.POSTGRES_URL ??= DEFAULT_E2E_POSTGRES_URL;
  process.env.POSTGRES_SYNCHRONIZE ??= 'false';
  process.env.REDIS_URL ??= DEFAULT_E2E_REDIS_URL;
  process.env.COOKIE_SECRET ??= 'auronix-e2e';
  process.env.ARGON2_PEPPER ??= 'auronix-e2e';
  process.env.CLIENT_URLS ??= 'http://localhost:4200';

  configured = true;
}

export function getE2ePostgresUrl(): string {
  configureE2eEnvironment();
  return process.env.POSTGRES_URL!;
}

export function getE2eRedisUrl(): string {
  configureE2eEnvironment();
  return process.env.REDIS_URL!;
}
