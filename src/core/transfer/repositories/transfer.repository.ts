import { FindManyDto } from '../../../shared/dto/find-many.dto';
import { DatabaseTransaction } from '../../../shared/modules/database/database.transaction';
import { CreateTransferDto } from '../dto/create-transfer.dto';
import { FindTransferDto } from '../dto/find-transfer.dto';
import { UpdateTransferDto } from '../dto/update-transfer.dto';
import { Transfer } from '../entities/transfer.entity';

export abstract class ITransferRepository {
  public abstract create(
    createTransferData: CreateTransferDto,
    transaction?: DatabaseTransaction,
  ): Promise<Transfer>;
  public abstract findById(
    id: string,
    transaction?: DatabaseTransaction,
  ): Promise<Transfer | null>;
  public abstract findMany(
    user: string,
    findTransferDto: FindTransferDto,
  ): Promise<FindManyDto<Transfer>>;
  public abstract update(
    id: string,
    updateTransferData: UpdateTransferDto,
    transaction?: DatabaseTransaction,
  ): Promise<void>;
}
