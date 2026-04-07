import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { AuthService } from '../../modules/auth/auth.service';
import { AuthenticatedRequest } from '../../http/types/authenticated-request.type';

@Injectable()
export class AuthGuard implements CanActivate {
  public constructor(private readonly authService: AuthService) {}

  public async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();

    const token = request.cookies.auth_cookie;
    if (!token) throw new ForbiddenException('Faca login novamente');

    try {
      const { id } = await this.authService.decodeToken<{ id: string }>(token);
      request.user = id;
    } catch {
      throw new ForbiddenException('Faca login novamente');
    }

    return true;
  }
}
