import { AppConfigType } from './types/app-config.type';

export class AppConfig {
  public static load(this: void): AppConfigType {
    return {
      env: process.env.NODE_ENV || 'development',
      port: process.env.PORT ? parseInt(process.env.PORT) : 3000,
      jwt: { secret: process.env.JWT_SECRET || 'auronix' },
      cookie: { secret: process.env.COOKIE_SECRET || 'auronix' },
      argon2: { pepper: process.env.ARGON2_PEPPER || 'auronix' },
      client: {
        urls: process.env.CLIENT_URLS
          ? process.env.CLIENT_URLS.split(';')
          : ['http://localhost:4200'],
      },
    };
  }
}
