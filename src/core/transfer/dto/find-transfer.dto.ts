import { Transform, Type } from 'class-transformer';
import {
  IsDateString,
  IsInt,
  IsObject,
  IsOptional,
  IsUUID,
  Max,
  Min,
  ValidateNested,
} from 'class-validator';

export class TransferCursorDto {
  @IsDateString({}, { message: 'Selecione um cursor valido' })
  public completedAt: string;

  @IsUUID('4', { message: 'Selecione um cursor valido' })
  public id: string;
}

export class FindTransferDto {
  @IsOptional()
  @Transform(({ value }) => {
    if (value == undefined || value == null || value == '') return undefined;
    if (typeof value != 'string') {
      if (typeof value != 'object' || value == null || Array.isArray(value))
        return value;

      return Object.assign(new TransferCursorDto(), value);
    }

    try {
      const parsedValue = JSON.parse(value);

      if (
        typeof parsedValue != 'object' ||
        parsedValue == null ||
        Array.isArray(parsedValue)
      )
        return parsedValue;

      return Object.assign(new TransferCursorDto(), parsedValue);
    } catch {
      return value;
    }
  })
  @IsObject({ message: 'Selecione um cursor valido' })
  @ValidateNested()
  @Type(() => TransferCursorDto)
  public cursor?: TransferCursorDto;

  @IsInt({ message: 'Selecione um limite valido' })
  @Min(0, { message: 'Selecione um limite maior ou igual a zero' })
  @Max(99, { message: 'Selecione um limite menor que 100' })
  public limit: number;
}
