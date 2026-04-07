import { OnWorkerEvent, Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';
import { Job, UnrecoverableError } from 'bullmq';
import { PaymentRequestService } from './payment-request.service';
import { PaymentRequestJobName } from './queue/payment-request-job-name.enum';
import { PaymentRequestQueueJobDataDto } from './queue/payment-request-queue-job-data.dto';
import { PaymentRequestQueueName } from './queue/payment-request-queue-name.enum';

@Processor(PaymentRequestQueueName.Expiration, {
  concurrency: 10,
})
export class PaymentRequestProcessor extends WorkerHost {
  private readonly logger = new Logger(PaymentRequestProcessor.name);

  public constructor(
    private readonly paymentRequestService: PaymentRequestService,
  ) {
    super();
  }

  public async process(
    job: Job<PaymentRequestQueueJobDataDto, void, PaymentRequestJobName>,
  ): Promise<void> {
    if (job.name != PaymentRequestJobName.ExpirePaymentRequest)
      throw new UnrecoverableError('Invalid payment request job');

    await this.paymentRequestService.deleteExpired(job.data.paymentRequestId);
  }

  @OnWorkerEvent('failed')
  public onFailed(
    job?: Job<PaymentRequestQueueJobDataDto, void, PaymentRequestJobName>,
    error?: Error,
  ): void {
    if (!job) return;

    const attempts = job.opts.attempts ?? 1;
    const isFinalFailure =
      error instanceof UnrecoverableError || job.attemptsMade >= attempts;

    if (!isFinalFailure) return;
    if (error)
      this.logger.error(
        `Failed to expire payment request job ${String(job.id ?? job.name)}`,
        error.stack,
      );
  }
}
