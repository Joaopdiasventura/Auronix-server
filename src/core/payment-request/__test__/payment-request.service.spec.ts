import {
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { UserService } from '../../user/user.service';
import { PaymentRequestQueue } from '../payment-request.queue';
import { IPaymentRequestRepository } from '../repositories/payment-request.repository';
import { PaymentRequestService } from '../payment-request.service';

describe('PaymentRequestService', () => {
  let service: PaymentRequestService;

  const paymentRequestRepository = {
    create: jest.fn(),
    findById: jest.fn(),
    delete: jest.fn(),
    deleteExpiredById: jest.fn(),
  };

  const userService = {
    findById: jest.fn(),
  };

  const paymentRequestQueue = {
    enqueueExpiration: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PaymentRequestService,
        {
          provide: IPaymentRequestRepository,
          useValue: paymentRequestRepository,
        },
        {
          provide: UserService,
          useValue: userService,
        },
        {
          provide: PaymentRequestQueue,
          useValue: paymentRequestQueue,
        },
      ],
    }).compile();

    service = module.get<PaymentRequestService>(PaymentRequestService);
  });

  it('should create a payment request for an existing user', async () => {
    userService.findById.mockResolvedValue({
      id: 'user-id',
    });
    paymentRequestRepository.create.mockResolvedValue({
      id: 'request-id',
      value: 1000,
      user: {
        id: 'user-id',
      },
    });
    paymentRequestQueue.enqueueExpiration.mockResolvedValue(undefined);

    const paymentRequest = await service.create('user-id', {
      value: 1000,
    });

    expect(userService.findById).toHaveBeenCalledWith('user-id');
    expect(paymentRequestRepository.create).toHaveBeenCalledWith({
      userId: 'user-id',
      value: 1000,
    });
    expect(paymentRequestQueue.enqueueExpiration).toHaveBeenCalledWith(
      'request-id',
    );
    expect(paymentRequest).toEqual({
      id: 'request-id',
      value: 1000,
      user: {
        id: 'user-id',
      },
    });
  });

  it('should rollback the payment request when expiration enqueue fails', async () => {
    const loggerErrorSpy = jest
      .spyOn(Logger.prototype, 'error')
      .mockImplementation();

    userService.findById.mockResolvedValue({
      id: 'user-id',
    });
    paymentRequestRepository.create.mockResolvedValue({
      id: 'request-id',
      value: 1000,
      user: {
        id: 'user-id',
      },
    });
    paymentRequestQueue.enqueueExpiration.mockRejectedValue(
      new Error('queue error'),
    );

    await expect(
      service.create('user-id', {
        value: 1000,
      }),
    ).rejects.toBeInstanceOf(InternalServerErrorException);

    expect(paymentRequestRepository.delete).toHaveBeenCalledWith('request-id');
    loggerErrorSpy.mockRestore();
  });

  it('should return a payment request by id', async () => {
    paymentRequestRepository.findById.mockResolvedValue({
      id: 'request-id',
      value: 1000,
    });

    const paymentRequest = await service.findById('request-id');

    expect(paymentRequestRepository.findById).toHaveBeenCalledWith(
      'request-id',
    );
    expect(paymentRequest).toEqual({
      id: 'request-id',
      value: 1000,
    });
  });

  it('should delete an expired payment request idempotently', async () => {
    await service.deleteExpired('request-id');

    expect(paymentRequestRepository.deleteExpiredById).toHaveBeenCalledWith(
      'request-id',
    );
  });

  it('should throw when the payment request does not exist', async () => {
    paymentRequestRepository.findById.mockResolvedValue(null);

    await expect(service.findById('missing-id')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });
});
