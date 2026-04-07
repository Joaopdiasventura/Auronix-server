import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../../shared/modules/auth/auth.module';
import { DatabaseModule } from '../../shared/modules/database/database.module';
import { NotificationModule } from '../../shared/modules/notification/notification.module';
import { UserModule } from '../user/user.module';
import { TransferController } from './transfer.controller';
import { Transfer } from './entities/transfer.entity';
import { TransferQueueName } from './queue/transfer-queue-name.enum';
import { TransferProcessor } from './transfer.processor';
import { TransferQueue } from './transfer.queue';
import { TransferPostgresRepository } from './repositories/transfer.postgres.repository';
import { ITransferRepository } from './repositories/transfer.repository';
import { TransferService } from './transfer.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([Transfer]),
    BullModule.registerQueue({ name: TransferQueueName.Processing }),
    UserModule,
    AuthModule,
    DatabaseModule,
    NotificationModule,
  ],
  controllers: [TransferController],
  providers: [
    TransferService,
    TransferQueue,
    TransferProcessor,
    {
      provide: ITransferRepository,
      useClass: TransferPostgresRepository,
    },
  ],
  exports: [TransferService],
})
export class TransferModule {}
