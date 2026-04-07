import { HttpResponse } from '../../../http/types/http-response.type';

export interface NotificationConnectionType {
  response: HttpResponse;
  heartbeat: NodeJS.Timeout;
  lastSentEventId: bigint | null;
}
