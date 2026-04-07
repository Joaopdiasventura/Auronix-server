import {
  BadRequestException,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { AuthService } from '../../shared/modules/auth/auth.service';
import { AuthResponseDto } from './dto/auth-response.dto';
import { CreateUserDto } from './dto/create-user.dto';
import { LoginUserDto } from './dto/login-user.dto';
import { UpdateUserDto } from './dto/update-user.dto';
import { User } from './entities/user.entity';
import { IUserRepository } from './repositories/user.repository';
import { DatabaseTransaction } from '../../shared/modules/database/database.transaction';

@Injectable()
export class UserService {
  public constructor(
    private readonly userRepository: IUserRepository,
    private readonly authService: AuthService,
  ) {}

  public async create(createUserDto: CreateUserDto): Promise<AuthResponseDto> {
    await this.validateEmail(createUserDto.email);

    const user = await this.userRepository.create(
      await this.createCreateUserData(createUserDto),
    );
    const token = await this.authService.generateToken({ id: user.id });

    delete user.password;
    return { token, user };
  }

  public async login(loginUserDto: LoginUserDto): Promise<AuthResponseDto> {
    const user = await this.userRepository.findByEmail(loginUserDto.email);
    if (!user) throw new UnauthorizedException('E-mail e/ou senha inválidos');

    const passwordMatch = await this.authService.comparePassword(
      loginUserDto.password,
      user.password!,
    );

    if (!passwordMatch)
      throw new UnauthorizedException('E-mail e/ou senha inválidos');

    const token = await this.authService.generateToken({ id: user.id });

    delete user.password;
    return { token, user };
  }

  public async getNewSession(id: string): Promise<AuthResponseDto> {
    const user = await this.findById(id);
    const newToken = await this.authService.generateToken({ id: user.id });
    delete user.password;
    return { user, token: newToken };
  }

  public async findById(
    id: string,
    transaction?: DatabaseTransaction,
  ): Promise<User> {
    const user = await this.userRepository.findById(id, transaction);
    if (!user) throw new NotFoundException('Conta não encontrada');
    return user;
  }

  public async findByEmail(email: string): Promise<User> {
    const user = await this.userRepository.findByEmail(email);
    if (!user) throw new NotFoundException('Conta não encontrada');
    delete user.password;
    return user;
  }

  public async update(id: string, updateUserDto: UpdateUserDto): Promise<void> {
    const user = await this.findById(id);

    if (updateUserDto.email && updateUserDto.email != user.email)
      await this.validateEmail(updateUserDto.email);

    await this.userRepository.update(
      id,
      await this.createUpdateUserData(updateUserDto),
    );
  }

  public async delete(id: string): Promise<void> {
    await this.findById(id);
    await this.userRepository.delete(id);
  }

  private async validateEmail(email: string): Promise<void> {
    const user = await this.userRepository.findByEmail(email);
    if (user)
      throw new BadRequestException('Esse e-mail já está sendo utilizado');
  }

  private async createCreateUserData(
    createUserDto: CreateUserDto,
  ): Promise<CreateUserDto> {
    return {
      email: createUserDto.email,
      name: createUserDto.name,
      password: await this.authService.hashPassword(createUserDto.password),
    };
  }

  private async createUpdateUserData(
    updateUserDto: UpdateUserDto,
  ): Promise<UpdateUserDto> {
    return {
      email: updateUserDto.email,
      name: updateUserDto.name,
      password: updateUserDto.password
        ? await this.authService.hashPassword(updateUserDto.password)
        : undefined,
    };
  }
}
