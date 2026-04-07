import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { AuthService } from '../auth.service';

describe('AuthService', () => {
  let service: AuthService;

  const configService = {
    get: jest.fn(),
  };

  const jwtService = {
    signAsync: jest.fn(),
    verifyAsync: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    configService.get.mockReturnValue('auronix-pepper');

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        {
          provide: ConfigService,
          useValue: configService,
        },
        {
          provide: JwtService,
          useValue: jwtService,
        },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
  });

  it('should generate a jwt token', async () => {
    jwtService.signAsync.mockResolvedValue('signed-token');

    const token = await service.generateToken({ id: 'user-id' });

    expect(jwtService.signAsync).toHaveBeenCalledWith({ id: 'user-id' });
    expect(token).toBe('signed-token');
  });

  it('should decode a jwt token', async () => {
    jwtService.verifyAsync.mockResolvedValue({ id: 'user-id' });

    const payload = await service.decodeToken('signed-token');

    expect(jwtService.verifyAsync).toHaveBeenCalledWith('signed-token');
    expect(payload).toEqual({ id: 'user-id' });
  });

  it('should hash and validate passwords using the configured pepper', async () => {
    const hashedPassword = await service.hashPassword('Password@123');

    expect(hashedPassword).not.toBe('Password@123');
    await expect(
      service.comparePassword('Password@123', hashedPassword),
    ).resolves.toBe(true);
    await expect(
      service.comparePassword('WrongPassword@123', hashedPassword),
    ).resolves.toBe(false);
  });
});
