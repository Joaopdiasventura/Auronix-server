import { Test, TestingModule } from '@nestjs/testing';
import { UnrecoverableError } from 'bullmq';
import { TransferProcessingError } from '../errors/transfer-processing.error';
import { TransferProcessor } from '../transfer.processor';
import { TransferJobName } from '../queue/transfer-job-name.enum';
import { TransferService } from '../transfer.service';

describe('TransferProcessor', () => {
  let processor: TransferProcessor;

  const transferService = {
    processPendingTransfer: jest.fn(),
    markAsFailed: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        TransferProcessor,
        {
          provide: TransferService,
          useValue: transferService,
        },
      ],
    }).compile();

    processor = module.get<TransferProcessor>(TransferProcessor);
  });

  it('should process a valid transfer job', async () => {
    await processor.process({
      data: {
        transferId: 'transfer-id',
      },
      id: 'job-id',
      name: TransferJobName.ProcessTransfer,
    } as never);

    expect(transferService.processPendingTransfer).toHaveBeenCalledWith(
      'transfer-id',
    );
  });

  it('should reject invalid job names', async () => {
    await expect(
      processor.process({
        data: {
          transferId: 'transfer-id',
        },
        id: 'job-id',
        name: 'invalid-job',
      } as never),
    ).rejects.toBeInstanceOf(UnrecoverableError);
  });

  it('should convert transfer processing errors into unrecoverable failures', async () => {
    transferService.processPendingTransfer.mockRejectedValue(
      new TransferProcessingError('Saldo insuficiente'),
    );

    await expect(
      processor.process({
        data: {
          transferId: 'transfer-id',
        },
        id: 'job-id',
        name: TransferJobName.ProcessTransfer,
      } as never),
    ).rejects.toBeInstanceOf(UnrecoverableError);
  });

  it('should mark a transfer as failed when the job reaches a final failure', async () => {
    await processor.onFailed(
      {
        data: {
          transferId: 'transfer-id',
        },
        opts: { attempts: 3 },
        attemptsMade: 3,
      } as never,
      new Error('queue error'),
    );

    expect(transferService.markAsFailed).toHaveBeenCalledWith(
      'transfer-id',
      'queue error',
    );
  });

  it('should not mark a transfer as failed on retryable worker failures', async () => {
    await processor.onFailed(
      {
        data: {
          transferId: 'transfer-id',
        },
        opts: { attempts: 3 },
        attemptsMade: 1,
      } as never,
      new Error('temporary error'),
    );

    expect(transferService.markAsFailed).not.toHaveBeenCalled();
  });
});
