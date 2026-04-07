import { User } from '../../../../core/user/entities/user.entity';

export interface TransferPendingNotificationDataDto {
  transferId: string;
  amount: number;
  createdAt: string;
  description?: string;
  balance: number;
  payer: User;
}
