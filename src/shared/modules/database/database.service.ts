import { DatabaseTransaction } from './database.transaction';

export abstract class IDatabaseService {
  public abstract transaction<T>(
    callback: (transaction: DatabaseTransaction) => Promise<T>,
  ): Promise<T>;
}
