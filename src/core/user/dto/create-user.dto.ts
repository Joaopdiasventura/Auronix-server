import {
  IsEmail,
  IsNotEmpty,
  IsStrongPassword,
  MaxLength,
} from 'class-validator';

export class CreateUserDto {
  @IsEmail({}, { message: 'Digite um e-mail válido' })
  public email: string;

  @IsNotEmpty({ message: 'Digite um nome' })
  @MaxLength(100, { message: 'Digite um nome com no máximo 100 caracteres' })
  public name: string;

  @IsStrongPassword({}, { message: 'Digite uma senha válida' })
  public password: string;
}
