import { Logger } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { ICacheService } from '../../cache/cache.service';
import { NotificationEventDto } from '../dto/notification-event.dto';
import { PublishNotificationEventDto } from '../dto/publish-notification-event.dto';
import { NotificationEventType } from '../enums/notification-event-type.enum';
import { NotificationGateway } from '../notification.gateway';
import { NotificationQueue } from '../notification.queue';
import { NotificationService } from '../notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  const cacheService = {
    get: jest.fn(),
    set: jest.fn(),
    delete: jest.fn(),
    increment: jest.fn(),
    appendToList: jest.fn(),
    getList: jest.fn(),
    trimList: jest.fn(),
    expire: jest.fn(),
    publish: jest.fn(),
    subscribe: jest.fn(),
    unsubscribe: jest.fn(),
  };

  const notificationGateway = {
    emit: jest.fn(),
  };

  const notificationQueue = {
    enqueuePublishing: jest.fn(),
  };

  const event: PublishNotificationEventDto = {
    userId: 'user-id',
    type: NotificationEventType.TransferCompleted,
    data: {
      transferId: 'transfer-id',
      amount: 1000,
      createdAt: '2026-03-26T12:00:00.000Z',
      description: 'Pagamento',
    },
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        NotificationService,
        {
          provide: ICacheService,
          useValue: cacheService,
        },
        {
          provide: NotificationGateway,
          useValue: notificationGateway,
        },
        {
          provide: NotificationQueue,
          useValue: notificationQueue,
        },
      ],
    }).compile();

    service = module.get<NotificationService>(NotificationService);
  });

  it('should subscribe and unsubscribe from the notification channel', async () => {
    await service.onModuleInit();
    await service.onModuleDestroy();

    expect(cacheService.subscribe).toHaveBeenCalledTimes(1);
    expect(cacheService.unsubscribe).toHaveBeenCalledTimes(1);
    expect(cacheService.subscribe.mock.calls[0][0]).toBe('notification:events');
    expect(cacheService.unsubscribe.mock.calls[0][0]).toBe(
      'notification:events',
    );
    expect(cacheService.unsubscribe.mock.calls[0][1]).toBe(
      cacheService.subscribe.mock.calls[0][1],
    );
  });

  it('should enqueue notification events and report success', async () => {
    notificationQueue.enqueuePublishing.mockResolvedValue(1);

    await expect(service.publish(event)).resolves.toBe(true);
    await expect(service.publishMany([])).resolves.toBe(0);

    expect(notificationQueue.enqueuePublishing).toHaveBeenCalledWith([event]);
  });

  it('should return zero when the notification queue fails', async () => {
    const loggerErrorSpy = jest
      .spyOn(Logger.prototype, 'error')
      .mockImplementation();

    notificationQueue.enqueuePublishing.mockRejectedValue(
      new Error('queue error'),
    );

    await expect(service.publishMany([event])).resolves.toBe(0);

    expect(loggerErrorSpy).toHaveBeenCalled();
    loggerErrorSpy.mockRestore();
  });

  it('should dispatch a notification, persist replay data and publish it to the channel', async () => {
    cacheService.increment.mockResolvedValue(9);

    const dispatchedEvent = await service.dispatch(event);

    expect(dispatchedEvent).toMatchObject({
      id: '9',
      userId: 'user-id',
      type: NotificationEventType.TransferCompleted,
      data: event.data,
    });
    expect(cacheService.appendToList).toHaveBeenCalledWith(
      'notification:user:user-id:events',
      expect.objectContaining({
        id: '9',
      }),
    );
    expect(cacheService.trimList).toHaveBeenCalledWith(
      'notification:user:user-id:events',
      -100,
      -1,
    );
    expect(cacheService.expire).toHaveBeenCalledWith(
      'notification:user:user-id:events',
      86400,
    );
    expect(cacheService.publish).toHaveBeenCalledWith(
      'notification:events',
      JSON.stringify(dispatchedEvent),
    );
  });

  it('should return replay events newer than the provided event id', async () => {
    const replayEvents: NotificationEventDto[] = [
      {
        id: '10',
        userId: 'user-id',
        type: NotificationEventType.TransferPending,
        data: {
          transferId: 'transfer-id',
          amount: 1000,
          createdAt: '2026-03-26T12:00:00.000Z',
          description: 'Pagamento',
        },
        occurredAt: '2026-03-26T12:00:00.000Z',
      },
      {
        id: '11',
        userId: 'user-id',
        type: NotificationEventType.TransferCompleted,
        data: event.data,
        occurredAt: '2026-03-26T12:01:00.000Z',
      },
      {
        id: '12',
        userId: 'another-user',
        type: NotificationEventType.TransferCompleted,
        data: event.data,
        occurredAt: '2026-03-26T12:02:00.000Z',
      },
    ];

    cacheService.getList.mockResolvedValue(replayEvents);

    const result = await service.getReplay('user-id', '10');

    expect(cacheService.getList).toHaveBeenCalledWith(
      'notification:user:user-id:events',
    );
    expect(result).toEqual([replayEvents[1]]);
  });

  it('should emit valid payloads received from the cache subscription', async () => {
    await service.onModuleInit();

    const handler = cacheService.subscribe.mock.calls[0][1] as (
      payload: string,
    ) => void;

    const notificationEvent: NotificationEventDto = {
      id: '9',
      userId: 'user-id',
      type: NotificationEventType.TransferCompleted,
      data: event.data,
      occurredAt: '2026-03-26T12:00:00.000Z',
    };

    handler(JSON.stringify(notificationEvent));

    expect(notificationGateway.emit).toHaveBeenCalledWith(notificationEvent);
  });

  it('should discard invalid payloads received from the cache subscription', async () => {
    const loggerWarnSpy = jest
      .spyOn(Logger.prototype, 'warn')
      .mockImplementation();

    await service.onModuleInit();

    const handler = cacheService.subscribe.mock.calls[0][1] as (
      payload: string,
    ) => void;

    handler('{invalid-json');

    expect(notificationGateway.emit).not.toHaveBeenCalled();
    expect(loggerWarnSpy).toHaveBeenCalledWith(
      'Discarding invalid notification event payload',
    );
    loggerWarnSpy.mockRestore();
  });
});
