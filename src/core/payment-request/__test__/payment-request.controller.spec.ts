import { Test, TestingModule } from '@nestjs/testing';
import { AuthService } from '../../../shared/modules/auth/auth.service';
import { PaymentRequestController } from '../payment-request.controller';
import { PaymentRequestService } from '../payment-request.service';

describe('PaymentRequestController', () => {
  let controller: PaymentRequestController;

  const paymentRequestService = {
    create: jest.fn(),
    findById: jest.fn(),
  };

  const authService = {
    decodeToken: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      controllers: [PaymentRequestController],
      providers: [
        {
          provide: PaymentRequestService,
          useValue: paymentRequestService,
        },
        {
          provide: AuthService,
          useValue: authService,
        },
      ],
    }).compile();

    controller = module.get<PaymentRequestController>(PaymentRequestController);
  });

  it('should create a payment request for the authenticated user', async () => {
    paymentRequestService.create.mockResolvedValue({
      id: 'request-id',
    });

    const paymentRequest = await controller.create(
      {
        user: 'user-id',
      } as never,
      {
        value: 1000,
      },
    );

    expect(paymentRequestService.create).toHaveBeenCalledWith('user-id', {
      value: 1000,
    });
    expect(paymentRequest).toEqual({ id: 'request-id' });
  });

  it('should return a payment request by id', async () => {
    paymentRequestService.findById.mockResolvedValue({
      id: 'request-id',
    });

    const paymentRequest = await controller.findById('request-id');

    expect(paymentRequestService.findById).toHaveBeenCalledWith('request-id');
    expect(paymentRequest).toEqual({ id: 'request-id' });
  });
});
