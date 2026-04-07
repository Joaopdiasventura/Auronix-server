import { TransferStatus } from '../enums/transfer-status.enum';

export interface UpdateTransferDto {
  status?: TransferStatus;
  failureReason?: string | null;
  completedAt?: Date | null;
}
