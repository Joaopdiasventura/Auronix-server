import { Test, TestingModule } from '@nestjs/testing';
import { UnrecoverableError } from 'bullmq';
import { PaymentRequestProcessor } from '../payment-request.processor';
import { PaymentRequestService } from '../payment-request.service';
import { PaymentRequestJobName } from '../queue/payment-request-job-name.enum';

describe('PaymentRequestProcessor', () => {
  let processor: PaymentRequestProcessor;

  const paymentRequestService = {
    deleteExpired: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PaymentRequestProcessor,
        {
          provide: PaymentRequestService,
          useValue: paymentRequestService,
        },
      ],
    }).compile();

    processor = module.get<PaymentRequestProcessor>(PaymentRequestProcessor);
  });

  it('should process a payment request expiration job', async () => {
    await processor.process({
      data: {
        paymentRequestId: 'payment-request-id',
      },
      name: PaymentRequestJobName.ExpirePaymentRequest,
    } as never);

    expect(paymentRequestService.deleteExpired).toHaveBeenCalledWith(
      'payment-request-id',
    );
  });

  it('should reject invalid job names', async () => {
    await expect(
      processor.process({
        data: {
          paymentRequestId: 'payment-request-id',
        },
        name: 'invalid-job',
      } as never),
    ).rejects.toBeInstanceOf(UnrecoverableError);
  });
});
