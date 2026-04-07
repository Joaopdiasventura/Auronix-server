import { E2eAppContext, createE2eAppContext } from './support/app.helper';
import {
  extractAuthCookie,
  loginUser,
  registerUser,
} from './support/auth.helper';

describe('User (e2e)', () => {
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

  it('POST /user creates an account and defines the auth cookie', async () => {
    const response = await context.request.post('/user').send({
      email: 'john@auronix.test',
      name: 'John Doe',
      password: 'Password@123',
    });

    expect(response.status).toBe(201);
    expect(extractAuthCookie(response)).toMatch(/^auth_cookie=/);
    expect(response.body).toEqual(
      expect.objectContaining({
        id: expect.any(String),
        email: 'john@auronix.test',
        name: 'John Doe',
        balance: 100000,
        createdAt: expect.any(String),
        updatedAt: expect.any(String),
      }),
    );
    expect(response.body.password).toBeUndefined();
  });

  it('POST /user rejects invalid payloads', async () => {
    const response = await context.request.post('/user').send({
      email: 'invalid-email',
      password: 'weak',
    });

    expect(response.status).toBe(400);
    expect(response.body).toEqual(
      expect.objectContaining({
        statusCode: 400,
        message: expect.any(Array),
      }),
    );
  });

  it('POST /user rejects non-whitelisted fields', async () => {
    const response = await context.request.post('/user').send({
      email: 'john@auronix.test',
      name: 'John Doe',
      password: 'Password@123',
      balance: 1,
    });

    expect(response.status).toBe(400);
    expect(response.body.message).toContain(
      'property balance should not exist',
    );
  });

  it('POST /user rejects duplicate e-mails', async () => {
    await registerUser(context.request, {
      email: 'john@auronix.test',
    });

    const response = await context.request.post('/user').send({
      email: 'john@auronix.test',
      name: 'John Duplicate',
      password: 'Password@123',
    });

    expect(response.status).toBe(400);
  });

  it('POST /user/login authenticates valid credentials and redefines the auth cookie', async () => {
    const fixture = await registerUser(context.request, {
      email: 'john@auronix.test',
    });

    const response = await context.request.post('/user/login').send({
      email: fixture.credentials.email,
      password: fixture.credentials.password,
    });

    expect(response.status).toBe(200);
    expect(extractAuthCookie(response)).toMatch(/^auth_cookie=/);
    expect(response.body).toEqual(
      expect.objectContaining({
        id: fixture.user.id,
        email: fixture.credentials.email,
        name: fixture.credentials.name,
        balance: 100000,
      }),
    );
    expect(response.body.password).toBeUndefined();
  });

  it('POST /user/login rejects invalid payloads', async () => {
    const response = await context.request.post('/user/login').send({
      email: 'invalid-email',
      password: 'weak',
    });

    expect(response.status).toBe(400);
  });

  it('POST /user/login rejects invalid credentials', async () => {
    const fixture = await registerUser(context.request);

    const response = await context.request.post('/user/login').send({
      email: fixture.credentials.email,
      password: 'WrongPassword@123',
    });

    expect(response.status).toBe(401);
  });

  it('GET /user returns the authenticated user', async () => {
    const fixture = await registerUser(context.request);

    const response = await context.request
      .get('/user')
      .set('Cookie', fixture.cookie);

    expect(response.status).toBe(200);
    expect(response.body).toEqual(
      expect.objectContaining({
        id: fixture.user.id,
        email: fixture.credentials.email,
        name: fixture.credentials.name,
      }),
    );
    expect(response.body.password).toBeUndefined();
  });

  it('GET /user rejects requests without authentication', async () => {
    const response = await context.request.get('/user');
    expect(response.status).toBe(403);
  });

  it('PATCH /user updates the authenticated user and keeps authentication functional', async () => {
    const fixture = await registerUser(context.request);

    const updateResponse = await context.request
      .patch('/user')
      .set('Cookie', fixture.cookie)
      .send({
        email: 'updated@auronix.test',
        name: 'Updated Name',
        password: 'NewPassword@123',
      });

    expect(updateResponse.status).toBe(200);

    const profileResponse = await context.request
      .get('/user')
      .set('Cookie', fixture.cookie);

    expect(profileResponse.status).toBe(200);
    expect(profileResponse.body).toEqual(
      expect.objectContaining({
        id: fixture.user.id,
        email: 'updated@auronix.test',
        name: 'Updated Name',
      }),
    );

    const oldLoginResponse = await context.request.post('/user/login').send({
      email: fixture.credentials.email,
      password: fixture.credentials.password,
    });
    expect(oldLoginResponse.status).toBe(401);

    const newLoginCookie = await loginUser(
      context.request,
      'updated@auronix.test',
      'NewPassword@123',
    );

    const newProfileResponse = await context.request
      .get('/user')
      .set('Cookie', newLoginCookie);

    expect(newProfileResponse.status).toBe(200);
    expect(newProfileResponse.body.email).toBe('updated@auronix.test');
  });

  it('PATCH /user rejects invalid payloads', async () => {
    const fixture = await registerUser(context.request);

    const response = await context.request
      .patch('/user')
      .set('Cookie', fixture.cookie)
      .send({
        email: 'invalid-email',
      });

    expect(response.status).toBe(400);
  });

  it('PATCH /user rejects an e-mail already used by another account', async () => {
    const firstUser = await registerUser(context.request);
    const secondUser = await registerUser(context.request);

    const response = await context.request
      .patch('/user')
      .set('Cookie', firstUser.cookie)
      .send({
        email: secondUser.credentials.email,
      });

    expect(response.status).toBe(400);
  });

  it('PATCH /user rejects requests without authentication', async () => {
    const response = await context.request.patch('/user').send({
      name: 'Updated Name',
    });

    expect(response.status).toBe(403);
  });

  it('DELETE /user removes the authenticated account', async () => {
    const fixture = await registerUser(context.request);

    const deleteResponse = await context.request
      .delete('/user')
      .set('Cookie', fixture.cookie);

    expect(deleteResponse.status).toBe(200);

    const profileResponse = await context.request
      .get('/user')
      .set('Cookie', fixture.cookie);

    expect(profileResponse.status).toBe(404);
  });

  it('DELETE /user rejects requests without authentication', async () => {
    const response = await context.request.delete('/user');
    expect(response.status).toBe(403);
  });
});
