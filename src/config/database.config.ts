import { DatabaseConfigType } from './types/database-config.type';

export class DatabaseConfig {
  public static load(this: void): DatabaseConfigType {
    return {
      postgres: {
        url:
          process.env.POSTGRES_URL ??
          'postgres://postgres:postgres@localhost:5432/auronix',
        synchronize: process.env.POSTGRES_SYNCHRONIZE
          ? process.env.POSTGRES_SYNCHRONIZE == 'true'
          : true,
      },
      redis: {
        url: process.env.REDIS_URL ?? 'redis://localhost:6379',
      },
    };
  }
}
