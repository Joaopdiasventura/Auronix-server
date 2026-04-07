import { NotificationEventType } from '../enums/notification-event-type.enum';
import { NotificationEventDataMap } from '../types/notification-event-data-map.type';

export interface NotificationSseMessageDto<
  T extends NotificationEventType = NotificationEventType,
> {
  type: T;
  data: NotificationEventDataMap[T];
}
