import { Test, TestingModule } from '@nestjs/testing';
import { AuthService } from '../../../shared/modules/auth/auth.service';
import { TransferController } from '../transfer.controller';
import { TransferService } from '../transfer.service';

describe('TransferController', () => {
  let controller: TransferController;

  const transferService = {
    create: jest.fn(),
    findById: jest.fn(),
    findMany: jest.fn(),
  };

  const authService = {
    decodeToken: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      controllers: [TransferController],
      providers: [
        {
          provide: TransferService,
          useValue: transferService,
        },
        {
          provide: AuthService,
          useValue: authService,
        },
      ],
    }).compile();

    controller = module.get<TransferController>(TransferController);
  });

  it('should create a transfer for the authenticated user', async () => {
    transferService.create.mockResolvedValue({
      id: 'transfer-id',
    });

    const transfer = await controller.create(
      {
        user: 'payer-id',
      } as never,
      {
        payeeId: 'payee-id',
        value: 500,
        description: 'Pagamento',
      },
    );

    expect(transferService.create).toHaveBeenCalledWith('payer-id', {
      payeeId: 'payee-id',
      value: 500,
      description: 'Pagamento',
    });
    expect(transfer).toEqual({ id: 'transfer-id' });
  });

  it('should return a transfer visible to the authenticated user', async () => {
    transferService.findById.mockResolvedValue({
      id: 'transfer-id',
    });

    const transfer = await controller.findById(
      {
        user: 'payer-id',
      } as never,
      'transfer-id',
    );

    expect(transferService.findById).toHaveBeenCalledWith(
      'transfer-id',
      'payer-id',
    );
    expect(transfer).toEqual({ id: 'transfer-id' });
  });

  it('should return many transfers visible to the authenticated user', async () => {
    transferService.findMany.mockResolvedValue({
      data: [
        {
          id: 'transfer-id',
        },
      ],
      next: null,
    });

    const transfers = await controller.findMany(
      {
        user: 'payer-id',
      } as never,
      {
        cursor: undefined,
        limit: 10,
      },
    );

    expect(transferService.findMany).toHaveBeenCalledWith('payer-id', {
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
});
