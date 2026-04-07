export interface TransferFailedNotificationDataDto {
  transferId: string;
  amount: number;
  createdAt: string;
  description: string;
  balance: number;
  failureReason: string;
}
