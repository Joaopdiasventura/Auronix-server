import { NotificationEventType } from '../enums/notification-event-type.enum';
import { NotificationEventDataMap } from '../types/notification-event-data-map.type';

export interface PublishNotificationEventDto<
  T extends NotificationEventType = NotificationEventType,
> {
  userId: string;
  type: T;
  data: NotificationEventDataMap[T];
}
