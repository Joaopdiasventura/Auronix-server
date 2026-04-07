import { ForbiddenException, ExecutionContext } from '@nestjs/common';
import { AuthService } from '../../../modules/auth/auth.service';
import { AuthGuard } from '../auth.guard';

describe('AuthGuard', () => {
  const authService = {
    decodeToken: jest.fn(),
  };

  let guard: AuthGuard;

  beforeEach(() => {
    jest.clearAllMocks();
    guard = new AuthGuard(authService as unknown as AuthService);
  });

  it('should reject requests without the auth cookie', async () => {
    const request = {
      cookies: {},
    };

    const context = {
      switchToHttp: () => ({
        getRequest: () => request,
      }),
    } as ExecutionContext;

    await expect(guard.canActivate(context)).rejects.toBeInstanceOf(
      ForbiddenException,
    );
  });

  it('should decode the auth cookie and attach the user id to the request', async () => {
    const request = {
      cookies: {
        auth_cookie: 'token',
      },
      user: '',
    };

    const context = {
      switchToHttp: () => ({
        getRequest: () => request,
      }),
    } as ExecutionContext;

    authService.decodeToken.mockResolvedValue({ id: 'user-id' });

    await expect(guard.canActivate(context)).resolves.toBe(true);

    expect(authService.decodeToken).toHaveBeenCalledWith('token');
    expect(request.user).toBe('user-id');
  });

  it('should reject requests with an invalid auth cookie', async () => {
    const request = {
      cookies: {
        auth_cookie: 'invalid-token',
      },
    };

    const context = {
      switchToHttp: () => ({
        getRequest: () => request,
      }),
    } as ExecutionContext;

    authService.decodeToken.mockRejectedValue(new Error('jwt malformed'));

    await expect(guard.canActivate(context)).rejects.toBeInstanceOf(
      ForbiddenException,
    );
  });
});
