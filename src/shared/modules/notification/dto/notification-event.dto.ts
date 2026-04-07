import { NotificationEventType } from '../enums/notification-event-type.enum';
import { NotificationEventDataMap } from '../types/notification-event-data-map.type';

export interface NotificationEventDto<
  T extends NotificationEventType = NotificationEventType,
> {
  id: string;
  userId: string;
  type: T;
  data: NotificationEventDataMap[T];
  occurredAt: string;
}
