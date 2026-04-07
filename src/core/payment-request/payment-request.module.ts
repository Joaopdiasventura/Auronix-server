import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../../shared/modules/auth/auth.module';
import { UserModule } from '../user/user.module';
import { PaymentRequestController } from './payment-request.controller';
import { PaymentRequestProcessor } from './payment-request.processor';
import { PaymentRequestQueue } from './payment-request.queue';
import { PaymentRequestService } from './payment-request.service';
import { PaymentRequest } from './entities/payment-request.entity';
import { PaymentRequestQueueName } from './queue/payment-request-queue-name.enum';
import { PaymentRequestPostgresRepository } from './repositories/payment-request.postgres.repository';
import { IPaymentRequestRepository } from './repositories/payment-request.repository';

@Module({
  imports: [
    TypeOrmModule.forFeature([PaymentRequest]),
    BullModule.registerQueue({ name: PaymentRequestQueueName.Expiration }),
    UserModule,
    AuthModule,
  ],
  controllers: [PaymentRequestController],
  providers: [
    PaymentRequestService,
    PaymentRequestQueue,
    PaymentRequestProcessor,
    {
      provide: IPaymentRequestRepository,
      useClass: PaymentRequestPostgresRepository,
    },
  ],
  exports: [PaymentRequestService],
})
export class PaymentRequestModule {}
