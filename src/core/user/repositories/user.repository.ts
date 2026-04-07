import { CreateUserDto } from '../dto/create-user.dto';
import { UpdateUserDto } from '../dto/update-user.dto';
import { User } from '../entities/user.entity';
import { DatabaseTransaction } from '../../../shared/modules/database/database.transaction';

export abstract class IUserRepository {
  public abstract create(createUserDto: CreateUserDto): Promise<User>;
  public abstract findById(
    id: string,
    transaction?: DatabaseTransaction,
  ): Promise<User | null>;
  public abstract findByEmail(
    email: string,
    transaction?: DatabaseTransaction,
  ): Promise<User | null>;
  public abstract findByIdsForUpdate(
    ids: string[],
    transaction: DatabaseTransaction,
  ): Promise<User[]>;
  public abstract saveMany(
    users: User[],
    transaction?: DatabaseTransaction,
  ): Promise<User[]>;
  public abstract update(
    id: string,
    updateUserDto: UpdateUserDto,
    transaction?: DatabaseTransaction,
  ): Promise<void>;
  public abstract delete(
    id: string,
    transaction?: DatabaseTransaction,
  ): Promise<void>;
}
