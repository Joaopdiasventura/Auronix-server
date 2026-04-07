export interface AppConfigType {
  env: string;
  port: number;
  jwt: { secret: string };
  cookie: { secret: string };
  argon2: { pepper: string };
  client: { urls: string[] };
}
