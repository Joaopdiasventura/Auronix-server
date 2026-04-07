import { InjectRepository } from '@nestjs/typeorm';
import { CreateUserDto } from '../dto/create-user.dto';
import { UpdateUserDto } from '../dto/update-user.dto';
import { User } from '../entities/user.entity';
import { IUserRepository } from './user.repository';
import { Repository } from 'typeorm';
import { DatabaseTransaction } from '../../../shared/modules/database/database.transaction';
import { DatabasePostgresTransaction } from '../../../shared/modules/database/postgres/database.postgres.transaction';

export class UserPostgresRepository implements IUserRepository {
  public constructor(
    @InjectRepository(User) private readonly repository: Repository<User>,
  ) {}

  public create(createUserDto: CreateUserDto): Promise<User> {
    return this.repository.save(createUserDto);
  }

  public findById(
    id: string,
    transaction?: DatabaseTransaction,
  ): Promise<User | null> {
    return this.getRepository(transaction).findOne({ where: { id } });
  }

  public findByEmail(
    email: string,
    transaction?: DatabaseTransaction,
  ): Promise<User | null> {
    return this.getRepository(transaction).findOne({ where: { email } });
  }

  public findByIdsForUpdate(
    ids: string[],
    transaction: DatabaseTransaction,
  ): Promise<User[]> {
    return this.getRepository(transaction).find({
      where: ids.map((id) => ({ id })),
      order: { id: 'ASC' },
      lock: {
        mode: 'pessimistic_write',
        tables: ['users'],
      },
    });
  }

  public saveMany(
    users: User[],
    transaction?: DatabaseTransaction,
  ): Promise<User[]> {
    return this.getRepository(transaction).save(users);
  }

  public async update(
    id: string,
    updateUserDto: UpdateUserDto,
    transaction?: DatabaseTransaction,
  ): Promise<void> {
    await this.getRepository(transaction).update(id, updateUserDto);
  }

  public async delete(
    id: string,
    transaction?: DatabaseTransaction,
  ): Promise<void> {
    await this.getRepository(transaction).delete(id);
  }

  private getRepository(transaction?: DatabaseTransaction): Repository<User> {
    if (!transaction) return this.repository;
    if (transaction instanceof DatabasePostgresTransaction)
      return transaction.manager.getRepository(User);

    throw new Error('Unsupported database transaction');
  }
}
