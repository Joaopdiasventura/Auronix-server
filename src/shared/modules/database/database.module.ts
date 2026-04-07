import { Module } from '@nestjs/common';
import { IDatabaseService } from './database.service';
import { DatabasePostgresService } from './postgres/database.postgres.service';

@Module({
  providers: [
    {
      provide: IDatabaseService,
      useClass: DatabasePostgresService,
    },
  ],
  exports: [IDatabaseService],
})
export class DatabaseModule {}
