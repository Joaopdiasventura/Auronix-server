import { TransferJobName } from '../queue/transfer-job-name.enum';
import { TransferQueue } from '../transfer.queue';

describe('TransferQueue', () => {
  const queue = {
    add: jest.fn(),
  };

  let service: TransferQueue;

  beforeEach(() => {
    jest.clearAllMocks();
    service = new TransferQueue(queue as never);
  });

  it('should enqueue the transfer processing job and log the action', async () => {
    await service.enqueueProcessing('transfer-id');

    expect(queue.add).toHaveBeenCalledWith(
      TransferJobName.ProcessTransfer,
      { transferId: 'transfer-id' },
      { jobId: 'transfer-id' },
    );
  });
});
