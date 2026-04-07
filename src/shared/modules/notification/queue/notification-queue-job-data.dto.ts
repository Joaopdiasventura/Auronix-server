import { PublishNotificationEventDto } from '../dto/publish-notification-event.dto';

export interface NotificationQueueJobDataDto {
  event: PublishNotificationEventDto;
}
