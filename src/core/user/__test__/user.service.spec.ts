import {
  BadRequestException,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { AuthService } from '../../../shared/modules/auth/auth.service';
import { CreateUserDto } from '../dto/create-user.dto';
import { LoginUserDto } from '../dto/login-user.dto';
import { UpdateUserDto } from '../dto/update-user.dto';
import { IUserRepository } from '../repositories/user.repository';
import { UserService } from '../user.service';

describe('UserService', () => {
  let service: UserService;

  const userRepository = {
    create: jest.fn(),
    findById: jest.fn(),
    findByEmail: jest.fn(),
    findByIdsForUpdate: jest.fn(),
    saveMany: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  };

  const authService = {
    hashPassword: jest.fn(),
    comparePassword: jest.fn(),
    generateToken: jest.fn(),
    decodeToken: jest.fn(),
  };

  const createUserDto: CreateUserDto = {
    email: 'john@example.com',
    name: 'John',
    password: 'Password@123',
  };

  const loginUserDto: LoginUserDto = {
    email: 'john@example.com',
    password: 'Password@123',
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        UserService,
        {
          provide: IUserRepository,
          useValue: userRepository,
        },
        {
          provide: AuthService,
          useValue: authService,
        },
      ],
    }).compile();

    service = module.get<UserService>(UserService);
  });

  it('should create a user, hash the password and return the auth payload', async () => {
    authService.hashPassword.mockResolvedValue('hashed-password');
    userRepository.findByEmail.mockResolvedValue(null);
    userRepository.create.mockResolvedValue({
      id: 'user-id',
      email: createUserDto.email,
      name: createUserDto.name,
      password: 'hashed-password',
      balance: 100000,
    });
    authService.generateToken.mockResolvedValue('token');

    const response = await service.create(createUserDto);

    expect(userRepository.create).toHaveBeenCalledWith({
      email: createUserDto.email,
      name: createUserDto.name,
      password: 'hashed-password',
    });
    expect(authService.generateToken).toHaveBeenCalledWith({ id: 'user-id' });
    expect(response).toEqual({
      token: 'token',
      user: {
        id: 'user-id',
        email: createUserDto.email,
        name: createUserDto.name,
        balance: 100000,
      },
    });
  });

  it('should reject user creation when the email is already in use', async () => {
    userRepository.findByEmail.mockResolvedValue({
      id: 'existing-user',
    });

    await expect(service.create(createUserDto)).rejects.toBeInstanceOf(
      BadRequestException,
    );

    expect(userRepository.create).not.toHaveBeenCalled();
    expect(authService.generateToken).not.toHaveBeenCalled();
  });

  it('should login an existing user and return the public user data', async () => {
    userRepository.findByEmail.mockResolvedValue({
      id: 'user-id',
      email: loginUserDto.email,
      name: 'John',
      password: 'hashed-password',
      balance: 100000,
    });
    authService.comparePassword.mockResolvedValue(true);
    authService.generateToken.mockResolvedValue('token');

    const response = await service.login(loginUserDto);

    expect(authService.generateToken).toHaveBeenCalledWith({ id: 'user-id' });
    expect(authService.comparePassword).toHaveBeenCalledWith(
      loginUserDto.password,
      'hashed-password',
    );
    expect(response).toEqual({
      token: 'token',
      user: {
        id: 'user-id',
        email: loginUserDto.email,
        name: 'John',
        balance: 100000,
      },
    });
  });

  it('should reject login when the password is invalid', async () => {
    userRepository.findByEmail.mockResolvedValue({
      id: 'user-id',
      password: 'hashed-password',
    });
    authService.comparePassword.mockResolvedValue(false);

    await expect(service.login(loginUserDto)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );

    expect(authService.generateToken).not.toHaveBeenCalled();
  });

  it('should return the authenticated user and refresh the auth token', async () => {
    userRepository.findById.mockResolvedValue({
      id: 'user-id',
      email: createUserDto.email,
      name: createUserDto.name,
      balance: 100000,
    });
    authService.generateToken.mockResolvedValue('token');

    const response = await service.getNewSession('user-id');

    expect(authService.generateToken).toHaveBeenCalledWith({ id: 'user-id' });
    expect(response).toEqual({
      token: 'token',
      user: {
        id: 'user-id',
        email: createUserDto.email,
        name: createUserDto.name,
        balance: 100000,
      },
    });
  });

  it('should throw when the requested user does not exist', async () => {
    userRepository.findById.mockResolvedValue(null);

    await expect(service.findById('missing-user')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });

  it('should update a user and hash the new password when provided', async () => {
    const updateUserDto: UpdateUserDto = {
      email: 'new@example.com',
      name: 'New Name',
      password: 'NewPassword@123',
    };

    userRepository.findById.mockResolvedValue({
      id: 'user-id',
      email: 'old@example.com',
      name: 'Old Name',
      balance: 100000,
    });
    userRepository.findByEmail.mockResolvedValue(null);
    authService.hashPassword.mockResolvedValue('new-hash');

    await service.update('user-id', updateUserDto);

    expect(userRepository.update).toHaveBeenCalledWith('user-id', {
      email: 'new@example.com',
      name: 'New Name',
      password: 'new-hash',
    });
  });

  it('should delete an existing user', async () => {
    userRepository.findById.mockResolvedValue({
      id: 'user-id',
      email: createUserDto.email,
    });

    await service.delete('user-id');

    expect(userRepository.delete).toHaveBeenCalledWith('user-id');
  });
});
