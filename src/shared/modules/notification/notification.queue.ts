import { InjectQueue } from '@nestjs/bullmq';
import { Injectable, Logger } from '@nestjs/common';
import { createHash } from 'crypto';
import { Queue } from 'bullmq';
import { PublishNotificationEventDto } from './dto/publish-notification-event.dto';
import { NotificationJobName } from './queue/notification-job-name.enum';
import { NotificationQueueJobDataDto } from './queue/notification-queue-job-data.dto';
import { NotificationQueueName } from './queue/notification-queue-name.enum';

@Injectable()
export class NotificationQueue {
  private readonly logger = new Logger(NotificationQueue.name);

  public constructor(
    @InjectQueue(NotificationQueueName.Publishing)
    private readonly queue: Queue<
      NotificationQueueJobDataDto,
      void,
      NotificationJobName
    >,
  ) {}

  public async enqueuePublishing(
    events: PublishNotificationEventDto[],
  ): Promise<number> {
    let enqueuedCount = 0;

    for (const event of events) {
      try {
        await this.queue.add(
          NotificationJobName.PublishNotification,
          { event },
          { jobId: this.createJobId(event) },
        );

        enqueuedCount++;
      } catch (error) {
        this.logger.error('Failed to enqueue notification job', error);
      }
    }

    return enqueuedCount;
  }

  private createJobId(event: PublishNotificationEventDto): string {
    return createHash('sha256').update(JSON.stringify(event)).digest('hex');
  }
}
