import { InjectQueue } from '@nestjs/bullmq';
import { Injectable } from '@nestjs/common';
import { Queue } from 'bullmq';
import { PaymentRequestJobName } from './queue/payment-request-job-name.enum';
import { PaymentRequestQueueJobDataDto } from './queue/payment-request-queue-job-data.dto';
import { PaymentRequestQueueName } from './queue/payment-request-queue-name.enum';

@Injectable()
export class PaymentRequestQueue {
  private readonly expiresIn = 10 * 60 * 1000;

  public constructor(
    @InjectQueue(PaymentRequestQueueName.Expiration)
    private readonly queue: Queue<
      PaymentRequestQueueJobDataDto,
      void,
      PaymentRequestJobName
    >,
  ) {}

  public async enqueueExpiration(paymentRequestId: string): Promise<void> {
    await this.queue.add(
      PaymentRequestJobName.ExpirePaymentRequest,
      { paymentRequestId },
      {
        jobId: paymentRequestId,
        delay: this.expiresIn,
      },
    );
  }
}
