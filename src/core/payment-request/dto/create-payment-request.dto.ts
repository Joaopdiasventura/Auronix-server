import { IsInt, Max, Min } from 'class-validator';

export class CreatePaymentRequestDto {
  @IsInt({ message: 'Digite um valor válido' })
  @Min(10, { message: 'Digite um valor de pelo menos dez centavos' })
  @Max(1000000_00, {
    message: 'Digite um valor menor que um milhão de reais',
  })
  public value: number;

  declare public userId: string;
}
