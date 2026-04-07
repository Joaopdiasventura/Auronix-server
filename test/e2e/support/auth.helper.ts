import { randomUUID } from 'crypto';
import type { Response, SuperTest, Test as SupertestTest } from 'supertest';

export interface UserCredentials {
  email: string;
  name: string;
  password: string;
}

export interface AuthenticatedUserFixture {
  cookie: string;
  credentials: UserCredentials;
  user: {
    id: string;
    email: string;
    name: string;
    balance: number;
  };
}

export function buildUserCredentials(
  overrides: Partial<UserCredentials> = {},
): UserCredentials {
  const id = randomUUID();

  return {
    email: `${id}@auronix.test`,
    name: `User ${id.slice(0, 8)}`,
    password: 'Password@123',
    ...overrides,
  };
}

export async function registerUser(
  client: SuperTest<SupertestTest>,
  overrides: Partial<UserCredentials> = {},
): Promise<AuthenticatedUserFixture> {
  const credentials = buildUserCredentials(overrides);
  const response = await client.post('/user').send(credentials);

  if (response.status != 201)
    throw new Error(
      `Failed to register user: ${response.status} ${JSON.stringify(response.body)}`,
    );

  return {
    cookie: extractAuthCookie(response),
    credentials,
    user: response.body as AuthenticatedUserFixture['user'],
  };
}

export async function loginUser(
  client: SuperTest<SupertestTest>,
  email: string,
  password: string,
): Promise<string> {
  const response = await client.post('/user/login').send({ email, password });

  if (response.status != 200)
    throw new Error(
      `Failed to login user: ${response.status} ${JSON.stringify(response.body)}`,
    );

  return extractAuthCookie(response);
}

export function extractAuthCookie(response: Response): string {
  const setCookie = response.headers['set-cookie'];

  if (!setCookie || setCookie.length == 0)
    throw new Error('auth_cookie was not returned by the API');

  const authCookie = setCookie.find((cookie) =>
    cookie.startsWith('auth_cookie='),
  );
  if (!authCookie) throw new Error('auth_cookie was not returned by the API');

  return authCookie.split(';')[0];
}
