import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { AuthService } from '../../auth/auth.service';
import { NotificationEventDto } from '../dto/notification-event.dto';
import { NotificationController } from '../notification.controller';
import { NotificationGateway } from '../notification.gateway';
import { NotificationService } from '../notification.service';

describe('NotificationController', () => {
  let controller: NotificationController;

  const notificationService = {
    getReplay: jest.fn(),
  };

  const notificationGateway = {
    connect: jest.fn(),
    disconnect: jest.fn(),
    emitToConnection: jest.fn(),
  };

  const authService = {
    decodeToken: jest.fn(),
  };

  const configService = {
    get: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    configService.get.mockReturnValue(['http://localhost:4200']);

    const module: TestingModule = await Test.createTestingModule({
      controllers: [NotificationController],
      providers: [
        {
          provide: NotificationService,
          useValue: notificationService,
        },
        {
          provide: NotificationGateway,
          useValue: notificationGateway,
        },
        {
          provide: ConfigService,
          useValue: configService,
        },
        {
          provide: AuthService,
          useValue: authService,
        },
      ],
    }).compile();

    controller = module.get<NotificationController>(NotificationController);
  });

  it('should open an SSE stream, connect the user and replay pending events', async () => {
    const replayEvents: NotificationEventDto[] = [
      {
        id: '9',
        userId: 'user-id',
        type: 'transfer.pending',
        data: {
          transferId: 'transfer-id',
          amount: 1000,
          createdAt: '2026-03-26T12:00:00.000Z',
          description: 'Pagamento',
        },
        occurredAt: '2026-03-26T12:00:00.000Z',
      },
      {
        id: '10',
        userId: 'user-id',
        type: 'transfer.completed',
        data: {
          transferId: 'transfer-id',
          amount: 1000,
          createdAt: '2026-03-26T12:00:00.000Z',
          description: 'Pagamento',
        },
        occurredAt: '2026-03-26T12:01:00.000Z',
      },
    ];

    let closeHandler: (() => void) | undefined;

    const request = {
      user: 'user-id',
      headers: {
        'last-event-id': '8',
      },
      raw: {
        once: jest.fn((eventName: string, callback: () => void) => {
          if (eventName == 'close') closeHandler = callback;
        }),
      },
    };

    const response = {
      hijack: jest.fn(),
      raw: {
        writeHead: jest.fn(),
        flushHeaders: jest.fn(),
      },
    };

    notificationGateway.connect.mockReturnValue('connection-id');
    notificationService.getReplay.mockResolvedValue(replayEvents);

    await controller.stream(request as never, response as never);

    expect(response.hijack).toHaveBeenCalled();
    expect(response.raw.writeHead).toHaveBeenCalledWith(200, {
      Connection: 'keep-alive',
      'Cache-Control': 'no-cache, no-transform',
      'Content-Type': 'text/event-stream',
      'X-Accel-Buffering': 'no',
    });
    expect(response.raw.flushHeaders).toHaveBeenCalled();
    expect(notificationGateway.connect).toHaveBeenCalledWith(
      'user-id',
      response,
      '8',
    );
    expect(notificationService.getReplay).toHaveBeenCalledWith('user-id', '8');
    expect(notificationGateway.emitToConnection).toHaveBeenNthCalledWith(
      1,
      'user-id',
      'connection-id',
      replayEvents[0],
    );
    expect(notificationGateway.emitToConnection).toHaveBeenNthCalledWith(
      2,
      'user-id',
      'connection-id',
      replayEvents[1],
    );

    closeHandler?.();

    expect(notificationGateway.disconnect).toHaveBeenCalledWith(
      'user-id',
      'connection-id',
    );
  });

  it('should use the first last-event-id header value when the request provides an array', async () => {
    const request = {
      user: 'user-id',
      headers: {
        'last-event-id': ['12', '11'],
      },
      raw: {
        once: jest.fn(),
      },
    };

    const response = {
      hijack: jest.fn(),
      raw: {
        writeHead: jest.fn(),
        flushHeaders: jest.fn(),
      },
    };

    notificationGateway.connect.mockReturnValue('connection-id');
    notificationService.getReplay.mockResolvedValue([]);

    await controller.stream(request as never, response as never);

    expect(notificationGateway.connect).toHaveBeenCalledWith(
      'user-id',
      response,
      '12',
    );
    expect(notificationService.getReplay).toHaveBeenCalledWith('user-id', '12');
    expect(notificationGateway.emitToConnection).not.toHaveBeenCalled();
  });

  it('should include cors headers for allowed origins when opening the stream', async () => {
    const request = {
      user: 'user-id',
      headers: {
        origin: 'http://localhost:4200',
      },
      raw: {
        once: jest.fn(),
      },
    };

    const response = {
      hijack: jest.fn(),
      raw: {
        writeHead: jest.fn(),
        flushHeaders: jest.fn(),
      },
    };

    notificationGateway.connect.mockReturnValue('connection-id');
    notificationService.getReplay.mockResolvedValue([]);

    await controller.stream(request as never, response as never);

    expect(response.raw.writeHead).toHaveBeenCalledWith(200, {
      Connection: 'keep-alive',
      'Cache-Control': 'no-cache, no-transform',
      'Content-Type': 'text/event-stream',
      'X-Accel-Buffering': 'no',
      'Access-Control-Allow-Credentials': 'true',
      'Access-Control-Allow-Origin': 'http://localhost:4200',
      Vary: 'Origin',
    });
  });
});
