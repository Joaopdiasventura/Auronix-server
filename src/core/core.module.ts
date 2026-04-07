import { Module } from '@nestjs/common';
import { UserModule } from './user/user.module';
import { PaymentRequestModule } from './payment-request/payment-request.module';
import { TransferModule } from './transfer/transfer.module';

@Module({
  imports: [UserModule, PaymentRequestModule, TransferModule],
})
export class CoreModule {}
