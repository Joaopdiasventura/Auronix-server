import { OnWorkerEvent, Processor, WorkerHost } from '@nestjs/bullmq';
import { Job, UnrecoverableError } from 'bullmq';
import { TransferProcessingError } from './errors/transfer-processing.error';
import { TransferJobName } from './queue/transfer-job-name.enum';
import { TransferQueueJobDataDto } from './queue/transfer-queue-job-data.dto';
import { TransferQueueName } from './queue/transfer-queue-name.enum';
import { TransferService } from './transfer.service';

@Processor(TransferQueueName.Processing, {
  concurrency: 10,
})
export class TransferProcessor extends WorkerHost {
  public constructor(private readonly transferService: TransferService) {
    super();
  }

  public async process(
    job: Job<TransferQueueJobDataDto, void, TransferJobName>,
  ): Promise<void> {
    if (job.name != TransferJobName.ProcessTransfer)
      throw new UnrecoverableError('Invalid transfer job');

    try {
      await this.transferService.processPendingTransfer(job.data.transferId);
    } catch (error) {
      if (error instanceof TransferProcessingError)
        throw new UnrecoverableError(error.message);
      throw error;
    }
  }

  @OnWorkerEvent('failed')
  public async onFailed(
    job: Job<TransferQueueJobDataDto, void, TransferJobName> | undefined,
    error: Error,
  ): Promise<void> {
    if (!job) return;

    const attempts = job.opts.attempts ?? 1;
    const isFinalFailure =
      error instanceof UnrecoverableError || job.attemptsMade >= attempts;

    if (!isFinalFailure) return;

    await this.transferService.markAsFailed(job.data.transferId, error.message);
  }
}
