import { User } from '../entities/user.entity';

export interface AuthResponseDto {
  token: string;
  user: User;
}
