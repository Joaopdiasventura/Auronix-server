import {
  BadRequestException,
  ForbiddenException,
  InternalServerErrorException,
} from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { IDatabaseService } from '../../../shared/modules/database/database.service';
import { NotificationService } from '../../../shared/modules/notification/notification.service';
import { IUserRepository } from '../../user/repositories/user.repository';
import { UserService } from '../../user/user.service';
import { TransferStatus } from '../enums/transfer-status.enum';
import { ITransferRepository } from '../repositories/transfer.repository';
import { TransferQueue } from '../transfer.queue';
import { TransferService } from '../transfer.service';

describe('TransferService', () => {
  let service: TransferService;
  const transaction = { id: 'tx' };

  const transferRepository = {
    create: jest.fn(),
    findById: jest.fn(),
    findMany: jest.fn(),
    update: jest.fn(),
  };

  const userService = {
    findById: jest.fn(),
  };

  const userRepository = {
    findById: jest.fn(),
    findByIdsForUpdate: jest.fn(),
    saveMany: jest.fn(),
  };

  const databaseService = {
    transaction: jest.fn(),
  };

  const transferQueue = {
    enqueueProcessing: jest.fn(),
  };

  const notificationService = {
    publishMany: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    databaseService.transaction.mockImplementation(
      async <T>(callback: (transaction: object) => Promise<T>) =>
        callback(transaction),
    );

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        TransferService,
        {
          provide: ITransferRepository,
          useValue: transferRepository,
        },
        {
          provide: UserService,
          useValue: userService,
        },
        {
          provide: IUserRepository,
          useValue: userRepository,
        },
        {
          provide: IDatabaseService,
          useValue: databaseService,
        },
        {
          provide: TransferQueue,
          useValue: transferQueue,
        },
        {
          provide: NotificationService,
          useValue: notificationService,
        },
      ],
    }).compile();

    service = module.get<TransferService>(TransferService);
  });

  it('should create a pending transfer and enqueue processing', async () => {
    userService.findById
      .mockResolvedValueOnce({
        id: 'payer-id',
        balance: 1000,
      })
      .mockResolvedValueOnce({
        id: 'payee-id',
        balance: 100,
      });

    transferRepository.create.mockResolvedValue({
      id: 'transfer-id',
      createdAt: new Date('2026-03-25T12:00:00.000Z'),
    });

    const transfer = await service.create('payer-id', {
      payeeId: 'payee-id',
      value: 500,
      description: 'Pagamento',
    });

    expect(userService.findById).toHaveBeenNthCalledWith(
      1,
      'payer-id',
      transaction,
    );
    expect(userService.findById).toHaveBeenNthCalledWith(
      2,
      'payee-id',
      transaction,
    );
    expect(transferRepository.create).toHaveBeenCalledWith(
      {
        payerId: 'payer-id',
        payeeId: 'payee-id',
        value: 500,
        description: 'Pagamento',
        status: TransferStatus.Pending,
      },
      transaction,
    );
    expect(transferQueue.enqueueProcessing).toHaveBeenCalledWith('transfer-id');
    expect(transfer.id).toBe('transfer-id');
  });

  it('should reject transfer creation when payer has insufficient balance', async () => {
    userService.findById.mockResolvedValue({
      id: 'payer-id',
      balance: 100,
    });

    await expect(
      service.create('payer-id', {
        payeeId: 'payee-id',
        value: 500,
        description: 'Pagamento',
      }),
    ).rejects.toBeInstanceOf(BadRequestException);

    expect(transferRepository.create).not.toHaveBeenCalled();
    expect(transferQueue.enqueueProcessing).not.toHaveBeenCalled();
  });

  it('should reject transfer creation to the same account', async () => {
    await expect(
      service.create('payer-id', {
        payeeId: 'payer-id',
        value: 500,
        description: 'Pagamento',
      }),
    ).rejects.toBeInstanceOf(BadRequestException);

    expect(userService.findById).not.toHaveBeenCalled();
    expect(transferRepository.create).not.toHaveBeenCalled();
  });

  it('should mark the transfer as failed when the queue enqueue fails', async () => {
    userService.findById
      .mockResolvedValueOnce({
        id: 'payer-id',
        balance: 1000,
      })
      .mockResolvedValueOnce({
        id: 'payee-id',
        balance: 100,
      });
    transferRepository.create.mockResolvedValue({
      id: 'transfer-id',
    });
    transferQueue.enqueueProcessing.mockRejectedValue(new Error('queue error'));

    await expect(
      service.create('payer-id', {
        payeeId: 'payee-id',
        value: 500,
        description: 'Pagamento',
      }),
    ).rejects.toBeInstanceOf(InternalServerErrorException);

    expect(transferRepository.update).toHaveBeenCalledWith(
      'transfer-id',
      {
        status: TransferStatus.Failed,
        failureReason: 'queue_enqueue_failed',
      },
      transaction,
    );
  });

  it('should process a pending transfer atomically and notify after success', async () => {
    transferRepository.findById.mockResolvedValue({
      id: 'transfer-id',
      status: TransferStatus.Pending,
      value: 300,
      createdAt: new Date('2026-03-25T12:00:00.000Z'),
      description: 'Pagamento',
      payer: { id: 'payer-id' },
      payee: { id: 'payee-id' },
    });

    userRepository.findByIdsForUpdate.mockResolvedValue([
      { id: 'payer-id', balance: 1000 },
      { id: 'payee-id', balance: 200 },
    ]);

    notificationService.publishMany.mockResolvedValue(2);

    await service.processPendingTransfer('transfer-id');

    expect(userRepository.saveMany).toHaveBeenCalledWith(
      [
        { id: 'payer-id', balance: 700 },
        { id: 'payee-id', balance: 500 },
      ],
      transaction,
    );
    expect(transferRepository.update).toHaveBeenCalledWith(
      'transfer-id',
      expect.objectContaining({
        status: TransferStatus.Completed,
        failureReason: null,
      }),
      transaction,
    );
    expect(notificationService.publishMany).toHaveBeenCalledWith([
      expect.objectContaining({
        userId: 'payer-id',
      }),
      expect.objectContaining({
        userId: 'payee-id',
      }),
    ]);
  });

  it('should return many transfers for the authenticated user', async () => {
    transferRepository.findMany.mockResolvedValue({
      data: [
        {
          id: 'transfer-id',
        },
      ],
      next: null,
    });

    const transfers = await service.findMany('payer-id', {
      cursor: undefined,
      limit: 10,
    });

    expect(transferRepository.findMany).toHaveBeenCalledWith('payer-id', {
      cursor: undefined,
      limit: 10,
    });
    expect(transfers).toEqual({
      data: [
        {
          id: 'transfer-id',
        },
      ],
      next: null,
    });
  });

  it('should return a transfer for an authorized participant', async () => {
    transferRepository.findById.mockResolvedValue({
      id: 'transfer-id',
      status: TransferStatus.Pending,
      value: 300,
      description: 'Pagamento',
      createdAt: new Date('2026-03-25T12:00:00.000Z'),
      payer: {
        id: 'payer-id',
      },
      payee: {
        id: 'payee-id',
      },
    });

    const transfer = await service.findById('transfer-id', 'payer-id');

    expect(transferRepository.findById).toHaveBeenCalledWith(
      'transfer-id',
      undefined,
    );
    expect(transfer.payer).not.toHaveProperty('password');
    expect(transfer.payee).not.toHaveProperty('password');
  });

  it('should forbid access to a transfer for non participants', async () => {
    transferRepository.findById.mockResolvedValue({
      id: 'transfer-id',
      payer: {
        id: 'payer-id',
      },
      payee: {
        id: 'payee-id',
      },
    });

    await expect(
      service.findById('transfer-id', 'other-user'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('should mark a transfer as failed and notify the payer', async () => {
    transferRepository.findById.mockResolvedValue({
      id: 'transfer-id',
      status: TransferStatus.Pending,
      value: 300,
      description: 'Pagamento',
      createdAt: new Date('2026-03-25T12:00:00.000Z'),
      payer: {
        id: 'payer-id',
      },
      payee: {
        id: 'payee-id',
      },
    });
    userRepository.findById.mockResolvedValue({
      id: 'payer-id',
      balance: 1000,
    });
    notificationService.publishMany.mockResolvedValue(1);

    await service.markAsFailed('transfer-id', 'Saldo insuficiente');

    expect(transferRepository.update).toHaveBeenCalledWith(
      'transfer-id',
      {
        status: TransferStatus.Failed,
        failureReason: 'Saldo insuficiente',
        completedAt: null,
      },
      transaction,
    );
    expect(notificationService.publishMany).toHaveBeenCalledWith([
      expect.objectContaining({
        userId: 'payer-id',
        type: 'transfer.failed',
      }),
    ]);
  });

  it('should not publish notifications when a failed transfer cannot be found', async () => {
    transferRepository.findById.mockResolvedValue(null);

    await service.markAsFailed('transfer-id', 'failure');

    expect(transferRepository.update).not.toHaveBeenCalled();
    expect(notificationService.publishMany).not.toHaveBeenCalled();
  });

  it('should not update balances or notify when the transfer is already completed', async () => {
    transferRepository.findById.mockResolvedValue({
      id: 'transfer-id',
      status: TransferStatus.Completed,
      value: 300,
      createdAt: new Date('2026-03-25T12:00:00.000Z'),
      description: 'Pagamento',
      payer: { id: 'payer-id' },
      payee: { id: 'payee-id' },
    });

    await service.processPendingTransfer('transfer-id');

    expect(userRepository.findByIdsForUpdate).not.toHaveBeenCalled();
    expect(userRepository.saveMany).not.toHaveBeenCalled();
    expect(transferRepository.update).not.toHaveBeenCalled();
    expect(notificationService.publishMany).not.toHaveBeenCalled();
  });
});
