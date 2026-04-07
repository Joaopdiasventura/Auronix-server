import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { DatabaseTransaction } from '../../../shared/modules/database/database.transaction';
import { DatabasePostgresTransaction } from '../../../shared/modules/database/postgres/database.postgres.transaction';
import { User } from '../../user/entities/user.entity';
import { CreateTransferDto } from '../dto/create-transfer.dto';
import { FindTransferDto } from '../dto/find-transfer.dto';
import { UpdateTransferDto } from '../dto/update-transfer.dto';
import { Transfer } from '../entities/transfer.entity';
import { ITransferRepository } from './transfer.repository';
import { FindManyDto } from '../../../shared/dto/find-many.dto';

export class TransferPostgresRepository implements ITransferRepository {
  public constructor(
    @InjectRepository(Transfer)
    private readonly repository: Repository<Transfer>,
  ) {}

  public create(
    createTransferData: CreateTransferDto,
    transaction?: DatabaseTransaction,
  ): Promise<Transfer> {
    return this.getRepository(transaction).save(
      this.repository.create({
        value: createTransferData.value,
        description: createTransferData.description,
        status: createTransferData.status,
        failureReason: null,
        completedAt: null,
        payer: this.createUserReference(createTransferData.payerId),
        payee: this.createUserReference(createTransferData.payeeId),
      }),
    );
  }

  public findById(
    id: string,
    transaction?: DatabaseTransaction,
  ): Promise<Transfer | null> {
    return this.getRepository(transaction).findOne({
      where: { id },
      relations: {
        payer: true,
        payee: true,
      },
      select: {
        payer: { id: true, email: true, name: true },
        payee: { id: true, email: true, name: true },
      },
    });
  }

  public async findMany(
    user: string,
    findTransferDto: FindTransferDto,
    transaction?: DatabaseTransaction,
  ): Promise<FindManyDto<Transfer>> {
    if (findTransferDto.limit == 0)
      return {
        data: [],
        next: null,
      };

    const queryBuilder = this.getRepository(transaction)
      .createQueryBuilder('t')
      .leftJoinAndSelect('t.payer', 'pr')
      .leftJoinAndSelect('t.payee', 'pe')
      .where('(t.fk_payer_id = :user OR t.fk_payee_id = :user)', { user })
      .andWhere('t.completed_at IS NOT NULL')
      .orderBy('t.completed_at', 'DESC')
      .addOrderBy('t.id', 'DESC')
      .limit(findTransferDto.limit + 1)
      .select([
        't.id',
        't.value',
        't.description',
        't.status',
        't.completedAt',
        'pr.id',
        'pr.email',
        'pr.name',
        'pe.id',
        'pe.email',
        'pe.name',
      ]);

    if (findTransferDto.cursor) {
      queryBuilder.andWhere(
        '(t.completed_at < :cursorCompletedAt OR (t.completed_at = :cursorCompletedAt AND t.id < :cursorId))',
        {
          cursorCompletedAt: findTransferDto.cursor.completedAt,
          cursorId: findTransferDto.cursor.id,
        },
      );
    }

    const transfers = await queryBuilder.getMany();
    const data = transfers.slice(0, findTransferDto.limit);
    const hasNextPage = transfers.length > findTransferDto.limit;

    return {
      data,
      next: hasNextPage
        ? {
            completedAt: data[data.length - 1].completedAt,
            id: data[data.length - 1].id,
          }
        : null,
    };
  }

  public async update(
    id: string,
    updateTransferData: UpdateTransferDto,
    transaction?: DatabaseTransaction,
  ): Promise<void> {
    await this.getRepository(transaction).update(id, updateTransferData);
  }

  private createUserReference(id: string): User {
    const user = new User();
    user.id = id;
    return user;
  }

  private getRepository(
    transaction?: DatabaseTransaction,
  ): Repository<Transfer> {
    if (!transaction) return this.repository;
    if (transaction instanceof DatabasePostgresTransaction)
      return transaction.manager.getRepository(Transfer);

    throw new Error('Unsupported database transaction');
  }
}
