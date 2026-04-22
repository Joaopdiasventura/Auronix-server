import { Test, TestingModule } from '@nestjs/testing';
import { AuthService } from '../../../shared/modules/auth/auth.service';
import { UserController } from '../user.controller';
import { UserService } from '../user.service';

describe('UserController', () => {
  let controller: UserController;

  const userService = {
    create: jest.fn(),
    login: jest.fn(),
    getNewSession: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  };

  const authService = {
    decodeToken: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      controllers: [UserController],
      providers: [
        {
          provide: UserService,
          useValue: userService,
        },
        {
          provide: AuthService,
          useValue: authService,
        },
      ],
    }).compile();

    controller = module.get<UserController>(UserController);
  });

  it('should create a user and set the auth cookie', async () => {
    const response = {
      cookie: jest.fn(),
    };

    userService.create.mockResolvedValue({
      token: 'token',
      user: {
        id: 'user-id',
      },
    });

    const user = await controller.create(
      {
        email: 'john@example.com',
        name: 'John',
        password: 'Password@123',
      },
      response as never,
    );

    expect(userService.create).toHaveBeenCalledWith({
      email: 'john@example.com',
      name: 'John',
      password: 'Password@123',
    });
    expect(response.cookie).toHaveBeenCalledWith('auth_cookie', 'token', {
      maxAge: 108000000,
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
      path: '/',
    });
    expect(user).toEqual({ id: 'user-id' });
  });

  it('should login a user and set the auth cookie', async () => {
    const response = {
      cookie: jest.fn(),
    };

    userService.login.mockResolvedValue({
      token: 'token',
      user: {
        id: 'user-id',
      },
    });

    const user = await controller.login(
      {
        email: 'john@example.com',
        password: 'Password@123',
      },
      response as never,
    );

    expect(userService.login).toHaveBeenCalledWith({
      email: 'john@example.com',
      password: 'Password@123',
    });
    expect(response.cookie).toHaveBeenCalledWith('auth_cookie', 'token', {
      maxAge: 108000000,
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
      path: '/',
    });
    expect(user).toEqual({ id: 'user-id' });
  });

  it('should return the authenticated user and refresh the auth cookie', async () => {
    const response = {
      cookie: jest.fn(),
    };

    userService.getNewSession.mockResolvedValue({
      token: 'token',
      user: { id: 'user-id' },
    });

    const user = await controller.getNewSession(
      {
        user: 'user-id',
      } as never,
      response as never,
    );

    expect(userService.getNewSession).toHaveBeenCalledWith('user-id');
    expect(response.cookie).toHaveBeenCalledWith('auth_cookie', 'token', {
      maxAge: 108000000,
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
      path: '/',
    });
    expect(user).toEqual({ id: 'user-id' });
  });

  it('should clear the auth cookie on logout', () => {
    const response = {
      clearCookie: jest.fn(),
    };

    controller.logout(response as never);

    expect(response.clearCookie).toHaveBeenCalledWith('auth_cookie', {
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
      maxAge: 108000000,
      path: '/',
    });
  });

  it('should update the authenticated user', async () => {
    userService.update.mockResolvedValue(undefined);

    await controller.update(
      {
        user: 'user-id',
      } as never,
      {
        name: 'Updated Name',
      },
    );

    expect(userService.update).toHaveBeenCalledWith('user-id', {
      name: 'Updated Name',
    });
  });

  it('should delete the authenticated user', async () => {
    userService.delete.mockResolvedValue(undefined);

    await controller.delete({
      user: 'user-id',
    } as never);

    expect(userService.delete).toHaveBeenCalledWith('user-id');
  });
});
