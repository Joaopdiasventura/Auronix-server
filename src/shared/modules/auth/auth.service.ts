import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { argon2id, hash, verify } from 'argon2';

@Injectable()
export class AuthService {
  private readonly pepper: Buffer;

  public constructor(
    private readonly configService: ConfigService,
    private readonly jwtService: JwtService,
  ) {
    this.pepper = Buffer.from(
      this.configService.get<string>('argon2.pepper')!,
      'utf8',
    );
  }

  public generateToken(payload: object): Promise<string> {
    return this.jwtService.signAsync(payload);
  }

  public decodeToken<T extends object>(token: string): Promise<T> {
    return this.jwtService.verifyAsync<T>(token);
  }

  public async hashPassword(password: string): Promise<string> {
    return hash(password, {
      type: argon2id,
      secret: this.pepper,
      memoryCost: 65536,
      timeCost: 3,
      parallelism: 1,
      hashLength: 32,
    });
  }

  public async comparePassword(
    password: string,
    hash: string,
  ): Promise<boolean> {
    return verify(hash, password, { secret: this.pepper });
  }
}
