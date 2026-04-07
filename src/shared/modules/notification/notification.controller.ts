import { Controller, Get, Req, Res, UseGuards } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NotificationGateway } from './notification.gateway';
import { NotificationService } from './notification.service';
import { AuthGuard } from '../../guards/auth/auth.guard';
import type { AuthenticatedRequest } from '../../http/types/authenticated-request.type';
import type { HttpResponse } from '../../http/types/http-response.type';

@UseGuards(AuthGuard)
@Controller('notification')
export class NotificationController {
  public constructor(
    private readonly notificationService: NotificationService,
    private readonly notificationGateway: NotificationGateway,
    private readonly configService: ConfigService,
  ) {}

  @Get('stream')
  public async stream(
    @Req() request: AuthenticatedRequest,
    @Res() response: HttpResponse,
  ): Promise<void> {
    const lastEventId = this.resolveLastEventId(request);

    response.hijack();
    response.raw.writeHead(200, this.createStreamHeaders(request));
    response.raw.flushHeaders?.();

    const connectionId = this.notificationGateway.connect(
      request.user,
      response,
      lastEventId,
    );

    request.raw.once('close', () => {
      this.notificationGateway.disconnect(request.user, connectionId);
    });

    const replayEvents = await this.notificationService.getReplay(
      request.user,
      lastEventId,
    );

    for (const replayEvent of replayEvents)
      this.notificationGateway.emitToConnection(
        request.user,
        connectionId,
        replayEvent,
      );
  }

  private resolveLastEventId(
    request: AuthenticatedRequest,
  ): string | undefined {
    const header = request.headers['last-event-id'];

    if (Array.isArray(header)) return header[0];
    return header;
  }

  private createStreamHeaders(
    request: AuthenticatedRequest,
  ): Record<string, string> {
    const headers: Record<string, string> = {
      Connection: 'keep-alive',
      'Cache-Control': 'no-cache, no-transform',
      'Content-Type': 'text/event-stream',
      'X-Accel-Buffering': 'no',
    };

    const allowedOrigin = this.resolveAllowedOrigin(request);
    if (!allowedOrigin) return headers;

    return {
      ...headers,
      'Access-Control-Allow-Credentials': 'true',
      'Access-Control-Allow-Origin': allowedOrigin,
      Vary: 'Origin',
    };
  }

  private resolveAllowedOrigin(
    request: AuthenticatedRequest,
  ): string | undefined {
    const originHeader = request.headers.origin;
    const origin = Array.isArray(originHeader) ? originHeader[0] : originHeader;
    if (!origin) return undefined;

    const allowedOrigins =
      this.configService.get<string[]>('client.urls') ?? [];
    return allowedOrigins.includes(origin) ? origin : undefined;
  }
}
