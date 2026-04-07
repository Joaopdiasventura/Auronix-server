import { IsEmail, IsStrongPassword } from 'class-validator';

export class LoginUserDto {
  @IsEmail({}, { message: 'Digite um e-mail válido' })
  public email: string;

  @IsStrongPassword({}, { message: 'Digite uma senha válida' })
  public password: string;
}
