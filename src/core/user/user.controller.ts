import {
  Controller,
  Post,
  Body,
  Patch,
  Delete,
  HttpCode,
  Res,
  Get,
  Req,
  UseGuards,
  Param,
} from '@nestjs/common';
import { UserService } from './user.service';
import { CreateUserDto } from './dto/create-user.dto';
import { UpdateUserDto } from './dto/update-user.dto';
import { LoginUserDto } from './dto/login-user.dto';
import { User } from './entities/user.entity';
import { AuthGuard } from '../../shared/guards/auth/auth.guard';
import type { AuthenticatedRequest } from '../../shared/http/types/authenticated-request.type';
import type { HttpResponse } from '../../shared/http/types/http-response.type';

@Controller('user')
export class UserController {
  private readonly expiresIn = 30 * 60 * 60 * 1000;

  public constructor(private readonly userService: UserService) {}

  @Post()
  public async create(
    @Body() createUserDto: CreateUserDto,
    @Res({ passthrough: true }) res: HttpResponse,
  ): Promise<User> {
    const { user, token } = await this.userService.create(createUserDto);
    this.setAuthCookie(token, res);
    return user;
  }

  @Post('login')
  @HttpCode(200)
  public async login(
    @Body() loginUserDto: LoginUserDto,
    @Res({ passthrough: true }) res: HttpResponse,
  ): Promise<User> {
    const { user, token } = await this.userService.login(loginUserDto);
    this.setAuthCookie(token, res);
    return user;
  }

  @Post('logout')
  @HttpCode(200)
  public logout(@Res({ passthrough: true }) res: HttpResponse): void {
    res.clearCookie('auth_cookie', {
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
      maxAge: this.expiresIn,
      path: '/',
    });
  }

  @Get()
  @UseGuards(AuthGuard)
  public async getNewSession(
    @Req() { user: id }: AuthenticatedRequest,
    @Res({ passthrough: true }) res: HttpResponse,
  ): Promise<User> {
    const { user, token } = await this.userService.getNewSession(id);
    this.setAuthCookie(token, res);
    return user;
  }

  @Get(':email')
  @UseGuards(AuthGuard)
  public findByEmail(@Param('email') email: string): Promise<User> {
    return this.userService.findByEmail(email);
  }

  @Patch()
  @HttpCode(200)
  @UseGuards(AuthGuard)
  public update(
    @Req() { user }: AuthenticatedRequest,
    @Body() updateUserDto: UpdateUserDto,
  ): Promise<void> {
    return this.userService.update(user, updateUserDto);
  }

  @Delete()
  @HttpCode(200)
  @UseGuards(AuthGuard)
  public delete(@Req() { user }: AuthenticatedRequest): Promise<void> {
    return this.userService.delete(user);
  }

  private setAuthCookie(token: string, res: HttpResponse): void {
    res.cookie('auth_cookie', token, {
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
      maxAge: this.expiresIn,
      path: '/',
    });
  }
}
