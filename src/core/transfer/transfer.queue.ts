import { InjectQueue } from '@nestjs/bullmq';
import { Injectable } from '@nestjs/common';
import { Queue } from 'bullmq';
import { TransferJobName } from './queue/transfer-job-name.enum';
import { TransferQueueJobDataDto } from './queue/transfer-queue-job-data.dto';
import { TransferQueueName } from './queue/transfer-queue-name.enum';

@Injectable()
export class TransferQueue {
  public constructor(
    @InjectQueue(TransferQueueName.Processing)
    private readonly queue: Queue<
      TransferQueueJobDataDto,
      void,
      TransferJobName
    >,
  ) {}

  public async enqueueProcessing(transferId: string): Promise<void> {
    await this.queue.add(
      TransferJobName.ProcessTransfer,
      { transferId },
      { jobId: transferId },
    );
  }
}
