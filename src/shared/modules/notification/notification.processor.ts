import { OnWorkerEvent, Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';
import { Job, UnrecoverableError } from 'bullmq';
import { NotificationJobName } from './queue/notification-job-name.enum';
import { NotificationQueueJobDataDto } from './queue/notification-queue-job-data.dto';
import { NotificationQueueName } from './queue/notification-queue-name.enum';
import { NotificationService } from './notification.service';

@Processor(NotificationQueueName.Publishing, {
  concurrency: 20,
})
export class NotificationProcessor extends WorkerHost {
  private readonly logger = new Logger(NotificationProcessor.name);

  public constructor(
    private readonly notificationService: NotificationService,
  ) {
    super();
  }

  public async process(
    job: Job<NotificationQueueJobDataDto, void, NotificationJobName>,
  ): Promise<void> {
    if (job.name != NotificationJobName.PublishNotification)
      throw new UnrecoverableError('Invalid notification job');

    await this.notificationService.dispatch(job.data.event);
  }

  @OnWorkerEvent('failed')
  public onFailed(
    job?: Job<NotificationQueueJobDataDto, void, NotificationJobName>,
    error?: Error,
  ): void {
    if (!job) return;

    const attempts = job.opts.attempts ?? 1;
    const isFinalFailure =
      error instanceof UnrecoverableError || job.attemptsMade >= attempts;

    if (!isFinalFailure) return;
    if (error)
      this.logger.error(
        `Failed to publish notification job ${String(job.id ?? job.name)}`,
        error.stack,
      );
  }
}
