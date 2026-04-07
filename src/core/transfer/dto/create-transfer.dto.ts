import {
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsUUID,
  Max,
  MaxLength,
  Min,
} from 'class-validator';
import { TransferStatus } from '../enums/transfer-status.enum';

export class CreateTransferDto {
  declare public payerId: string;

  @IsUUID('4', { message: 'Digite um usuário válido' })
  public payeeId: string;

  @IsInt({ message: 'Digite um valor válido' })
  @Min(1, { message: 'Digite um valor maior que zero' })
  @Max(1000000_00, { message: 'Digite um valor menor que um milhão de reais' })
  public value: number;

  @IsOptional()
  @IsNotEmpty({ message: 'Digite uma descrição' })
  @MaxLength(255, { message: 'Digite uma descrição com até 255 caracteres' })
  public description: string;

  declare public status: TransferStatus;
}
