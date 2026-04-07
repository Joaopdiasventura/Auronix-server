import {
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import { ICacheService } from '../cache/cache.service';
import { NotificationEventDto } from './dto/notification-event.dto';
import { PublishNotificationEventDto } from './dto/publish-notification-event.dto';
import { NotificationEventType } from './enums/notification-event-type.enum';
import { NotificationGateway } from './notification.gateway';
import { NotificationQueue } from './notification.queue';

const NOTIFICATION_CHANNEL = 'notification:events';
const NOTIFICATION_REPLAY_LIMIT = 100;
const NOTIFICATION_REPLAY_TTL_SECONDS = 60 * 60 * 24;
const NOTIFICATION_SEQUENCE_KEY = 'notification:events:sequence';

@Injectable()
export class NotificationService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(NotificationService.name);

  public constructor(
    private readonly cacheService: ICacheService,
    private readonly notificationGateway: NotificationGateway,
    private readonly notificationQueue: NotificationQueue,
  ) {}

  public async onModuleInit(): Promise<void> {
    await this.cacheService.subscribe(NOTIFICATION_CHANNEL, this.handleMessage);
  }

  public async onModuleDestroy(): Promise<void> {
    await this.cacheService.unsubscribe(
      NOTIFICATION_CHANNEL,
      this.handleMessage,
    );
  }

  public async publish(event: PublishNotificationEventDto): Promise<boolean> {
    return (await this.publishMany([event])) == 1;
  }

  public async publishMany(
    events: PublishNotificationEventDto[],
  ): Promise<number> {
    if (events.length == 0) return 0;

    try {
      return await this.notificationQueue.enqueuePublishing(events);
    } catch (error) {
      this.logger.error('Failed to enqueue notification event', error);
      return 0;
    }
  }

  public async dispatch(
    event: PublishNotificationEventDto,
  ): Promise<NotificationEventDto> {
    const publishedEvent = this.createEventEnvelope(
      event,
      String(await this.cacheService.increment(NOTIFICATION_SEQUENCE_KEY)),
    );

    await this.cacheService.appendToList(
      this.getReplayKey(event.userId),
      publishedEvent,
    );
    await this.cacheService.trimList(
      this.getReplayKey(event.userId),
      -NOTIFICATION_REPLAY_LIMIT,
      -1,
    );
    await this.cacheService.expire(
      this.getReplayKey(event.userId),
      NOTIFICATION_REPLAY_TTL_SECONDS,
    );
    await this.cacheService.publish(
      NOTIFICATION_CHANNEL,
      JSON.stringify(publishedEvent),
    );

    return publishedEvent;
  }

  public async getReplay(
    userId: string,
    afterEventId?: string,
  ): Promise<NotificationEventDto[]> {
    if (!afterEventId) return [];

    const events = await this.cacheService.getList<NotificationEventDto>(
      this.getReplayKey(userId),
    );

    const afterId = this.parseEventId(afterEventId);
    return events
      .filter((event) => event.userId == userId)
      .filter((event) => {
        if (!afterId) return true;

        const eventId = this.parseEventId(event.id);
        return !!eventId && eventId > afterId;
      });
  }

  private createEventEnvelope<T extends NotificationEventType>(
    event: PublishNotificationEventDto<T>,
    id: string,
  ): NotificationEventDto<T> {
    return {
      id,
      userId: event.userId,
      type: event.type,
      data: event.data,
      occurredAt: new Date().toISOString(),
    };
  }

  private getReplayKey(userId: string): string {
    return `notification:user:${userId}:events`;
  }

  private parseEventId(eventId: string): bigint | null {
    try {
      return BigInt(eventId);
    } catch {
      return null;
    }
  }

  private readonly handleMessage = (payload: string): void => {
    try {
      const event = JSON.parse(payload) as NotificationEventDto;
      this.notificationGateway.emit(event);
    } catch {
      this.logger.warn('Discarding invalid notification event payload');
    }
  };
}
