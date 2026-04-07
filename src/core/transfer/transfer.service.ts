import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { PublishNotificationEventDto } from '../../shared/modules/notification/dto/publish-notification-event.dto';
import { TransferPendingNotificationDataDto } from '../../shared/modules/notification/dto/transfer-pending-notification-data.dto';
import { NotificationEventType } from '../../shared/modules/notification/enums/notification-event-type.enum';
import { NotificationService } from '../../shared/modules/notification/notification.service';
import { IDatabaseService } from '../../shared/modules/database/database.service';
import { IUserRepository } from '../user/repositories/user.repository';
import { UserService } from '../user/user.service';
import { CreateTransferDto } from './dto/create-transfer.dto';
import { TransferStatus } from './enums/transfer-status.enum';
import { TransferProcessingError } from './errors/transfer-processing.error';
import { Transfer } from './entities/transfer.entity';
import { ITransferRepository } from './repositories/transfer.repository';
import { TransferQueue } from './transfer.queue';
import { FindTransferDto } from './dto/find-transfer.dto';
import { FindManyDto } from '../../shared/dto/find-many.dto';
import { DatabaseTransaction } from '../../shared/modules/database/database.transaction';
import { User } from '../user/entities/user.entity';

@Injectable()
export class TransferService {
  private readonly logger = new Logger(TransferService.name);

  public constructor(
    private readonly transferRepository: ITransferRepository,
    private readonly userService: UserService,
    private readonly userRepository: IUserRepository,
    private readonly databaseService: IDatabaseService,
    private readonly transferQueue: TransferQueue,
    private readonly notificationService: NotificationService,
  ) {}

  public async create(
    payerId: string,
    createTransferDto: CreateTransferDto,
  ): Promise<Transfer> {
    if (payerId == createTransferDto.payeeId)
      throw new BadRequestException(
        'Não é possível transferir para a própria conta',
      );

    const transfer = await this.databaseService.transaction(
      async (transaction) => {
        const payer = await this.userService.findById(payerId, transaction);
        await this.userService.findById(createTransferDto.payeeId, transaction);

        if (payer.balance < createTransferDto.value)
          throw new BadRequestException('Saldo insuficiente');

        return await this.transferRepository.create(
          this.createTransferData(payerId, createTransferDto),
          transaction,
        );
      },
    );

    try {
      await this.transferQueue.enqueueProcessing(transfer.id);
    } catch {
      await this.databaseService.transaction(async (transaction) => {
        await this.transferRepository.update(
          transfer.id,
          {
            status: TransferStatus.Failed,
            failureReason: 'queue_enqueue_failed',
          },
          transaction,
        );
      });
      throw new InternalServerErrorException(
        'Não foi possível processar a transferência',
      );
    }

    return transfer;
  }

  public async findById(
    id: string,
    userId: string,
    trasaction?: DatabaseTransaction,
  ): Promise<Transfer> {
    const transfer = await this.transferRepository.findById(id, trasaction);

    if (!transfer) throw new NotFoundException('Transferência não encontrada');

    if (transfer.payer.id != userId && transfer.payee.id != userId)
      throw new ForbiddenException('Você não pode acessar esta transferência');

    return transfer;
  }

  public async findMany(
    user: string,
    findTransferDto: FindTransferDto,
  ): Promise<FindManyDto<Transfer>> {
    return this.transferRepository.findMany(user, findTransferDto);
  }

  public async processPendingTransfer(id: string): Promise<void> {
    const notifications = await this.databaseService.transaction(
      async (transaction) => {
        const transfer = await this.transferRepository.findById(
          id,
          transaction,
        );

        if (!transfer)
          throw new TransferProcessingError('Transferência não encontrada');

        if (transfer.status == TransferStatus.Completed) return [];
        if (transfer.status == TransferStatus.Failed) return [];

        const users = await this.userRepository.findByIdsForUpdate(
          [transfer.payer.id, transfer.payee.id],
          transaction,
        );

        const payer = users.find((user) => user.id == transfer.payer.id);
        const payee = users.find((user) => user.id == transfer.payee.id);

        if (!payer || !payee)
          throw new TransferProcessingError('Conta não encontrada');

        if (payer.balance < transfer.value)
          throw new TransferProcessingError('Saldo insuficiente');

        payer.balance -= transfer.value;
        payee.balance += transfer.value;

        await this.userRepository.saveMany([payer, payee], transaction);
        await this.transferRepository.update(
          transfer.id,
          {
            status: TransferStatus.Completed,
            failureReason: null,
            completedAt: new Date(),
          },
          transaction,
        );

        return [
          {
            userId: transfer.payer.id,
            type: NotificationEventType.TransferCompleted,
            data: this.createNotificationData(transfer, payer.balance, payer),
          },
          {
            userId: transfer.payee.id,
            type: NotificationEventType.TransferCompleted,
            data: this.createNotificationData(transfer, payee.balance, payer),
          },
        ] satisfies PublishNotificationEventDto[];
      },
    );

    await this.publishNotifications(notifications);
  }

  public async markAsFailed(id: string, failureReason: string): Promise<void> {
    const notifications = await this.databaseService.transaction(
      async (transaction) => {
        const transfer = await this.transferRepository.findById(
          id,
          transaction,
        );

        if (!transfer || transfer.status == TransferStatus.Completed) return [];

        const payer = await this.userRepository.findById(
          transfer.payer.id,
          transaction,
        );
        if (!payer) return [];

        await this.transferRepository.update(
          id,
          {
            status: TransferStatus.Failed,
            failureReason,
            completedAt: null,
          },
          transaction,
        );

        return [
          {
            userId: transfer.payer.id,
            type: NotificationEventType.TransferFailed,
            data: {
              ...this.createNotificationData(transfer, payer.balance, payer),
              failureReason,
            },
          },
        ] satisfies PublishNotificationEventDto[];
      },
    );

    await this.publishNotifications(notifications);
  }

  private createTransferData(
    payerId: string,
    createTransferDto: CreateTransferDto,
  ): CreateTransferDto {
    return {
      payerId,
      payeeId: createTransferDto.payeeId,
      value: createTransferDto.value,
      description: createTransferDto.description,
      status: TransferStatus.Pending,
    };
  }

  private createNotificationData(
    transfer: Transfer,
    balance: number,
    payer: User,
  ): TransferPendingNotificationDataDto {
    return {
      transferId: transfer.id,
      amount: transfer.value,
      createdAt: transfer.createdAt.toISOString(),
      description: transfer.description,
      balance,
      payer,
    };
  }

  private async publishNotifications(
    notifications: PublishNotificationEventDto[],
  ): Promise<void> {
    if (notifications.length == 0) return;

    const publishedNotifications =
      await this.notificationService.publishMany(notifications);

    if (publishedNotifications == notifications.length) return;

    this.logger.warn('Some transfer notifications could not be enqueued');
  }
}
