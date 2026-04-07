import { Injectable } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { IDatabaseService } from '../database.service';
import { DatabaseTransaction } from '../database.transaction';
import { DatabasePostgresTransaction } from './database.postgres.transaction';

@Injectable()
export class DatabasePostgresService implements IDatabaseService {
  public constructor(private readonly dataSource: DataSource) {}

  public transaction<T>(
    callback: (transaction: DatabaseTransaction) => Promise<T>,
  ): Promise<T> {
    return this.dataSource.transaction(async (manager) =>
      callback(new DatabasePostgresTransaction(manager)),
    );
  }
}
