import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import type { HttpResponse } from '../../http/types/http-response.type';
import { NotificationEventDto } from './dto/notification-event.dto';
import { NotificationSseMessageDto } from './dto/notification-sse-message.dto';
import { NotificationConnectionType } from './types/notification-connection.type';

const NOTIFICATION_STREAM_RETRY_MS = 5000;
const NOTIFICATION_HEARTBEAT_INTERVAL_MS = 25000;

@Injectable()
export class NotificationGateway {
  private readonly connections = new Map<
    string,
    Map<string, NotificationConnectionType>
  >();

  public connect(
    userId: string,
    response: HttpResponse,
    lastEventId?: string,
  ): string {
    const connectionId = randomUUID();
    const userConnections =
      this.connections.get(userId) ??
      new Map<string, NotificationConnectionType>();

    const heartbeat = setInterval(() => {
      if (response.raw.destroyed || response.raw.writableEnded) {
        this.disconnect(userId, connectionId);
        return;
      }

      response.raw.write(': ping\n\n');
    }, NOTIFICATION_HEARTBEAT_INTERVAL_MS);

    userConnections.set(connectionId, {
      response,
      heartbeat,
      lastSentEventId: this.parseEventId(lastEventId),
    });
    this.connections.set(userId, userConnections);

    response.raw.write(`retry: ${NOTIFICATION_STREAM_RETRY_MS}\n\n`);
    response.raw.write(': connected\n\n');
    return connectionId;
  }

  public disconnect(userId: string, connectionId: string): void {
    const userConnections = this.connections.get(userId);
    if (!userConnections) return;

    const connection = userConnections.get(connectionId);
    if (!connection) return;

    clearInterval(connection.heartbeat);
    userConnections.delete(connectionId);

    if (userConnections.size == 0) this.connections.delete(userId);
  }

  public emit(event: NotificationEventDto): void {
    const userConnections = this.connections.get(event.userId);
    if (!userConnections) return;

    for (const connectionId of userConnections.keys())
      this.emitToConnection(event.userId, connectionId, event);
  }

  public emitToConnection(
    userId: string,
    connectionId: string,
    event: NotificationEventDto,
  ): void {
    const connection = this.connections.get(userId)?.get(connectionId);
    if (!connection) return;

    const nextEventId = this.parseEventId(event.id);
    if (
      connection.lastSentEventId &&
      nextEventId &&
      nextEventId <= connection.lastSentEventId
    )
      return;

    if (
      connection.response.raw.destroyed ||
      connection.response.raw.writableEnded
    ) {
      this.disconnect(userId, connectionId);
      return;
    }

    connection.response.raw.write(this.serializeEvent(event));
    connection.lastSentEventId = nextEventId;
  }

  private serializeEvent(event: NotificationEventDto): string {
    const payload: NotificationSseMessageDto = {
      type: event.type,
      data: event.data,
    };

    return `id: ${event.id}\nevent: ${event.type}\ndata: ${JSON.stringify(payload)}\n\n`;
  }

  private parseEventId(eventId?: string): bigint | null {
    if (!eventId) return null;

    try {
      return BigInt(eventId);
    } catch {
      return null;
    }
  }
}
