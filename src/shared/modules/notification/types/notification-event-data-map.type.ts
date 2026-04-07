import { TransferCompletedNotificationDataDto } from '../dto/transfer-completed-notification-data.dto';
import { TransferFailedNotificationDataDto } from '../dto/transfer-failed-notification-data.dto';
import { TransferPendingNotificationDataDto } from '../dto/transfer-pending-notification-data.dto';
import { NotificationEventType } from '../enums/notification-event-type.enum';

export type NotificationEventDataMap = {
  [NotificationEventType.TransferPending]: TransferPendingNotificationDataDto;
  [NotificationEventType.TransferCompleted]: TransferCompletedNotificationDataDto;
  [NotificationEventType.TransferFailed]: TransferFailedNotificationDataDto;
};
