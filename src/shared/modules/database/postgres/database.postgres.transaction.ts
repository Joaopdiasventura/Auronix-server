import { EntityManager } from 'typeorm';
import { DatabaseTransaction } from '../database.transaction';

export class DatabasePostgresTransaction extends DatabaseTransaction {
  public constructor(public readonly manager: EntityManager) {
    super();
  }
}
