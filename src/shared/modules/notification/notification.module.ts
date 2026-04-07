import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthModule } from '../auth/auth.module';
import { CacheModule } from '../cache/cache.module';
import { NotificationController } from './notification.controller';
import { NotificationGateway } from './notification.gateway';
import { NotificationProcessor } from './notification.processor';
import { NotificationQueueName } from './queue/notification-queue-name.enum';
import { NotificationQueue } from './notification.queue';
import { NotificationService } from './notification.service';

@Module({
  imports: [
    CacheModule,
    ConfigModule,
    AuthModule,
    BullModule.registerQueue({ name: NotificationQueueName.Publishing }),
  ],
  controllers: [NotificationController],
  providers: [
    NotificationService,
    NotificationGateway,
    NotificationQueue,
    NotificationProcessor,
  ],
  exports: [NotificationService],
})
export class NotificationModule {}
