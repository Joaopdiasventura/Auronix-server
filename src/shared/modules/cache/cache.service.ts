import { CacheMessageHandler } from './types/cache-message-handler.type';

export abstract class ICacheService {
  public abstract get<T>(key: string): Promise<T | null>;
  public abstract set<T>(
    key: string,
    value: T,
    ttlInSeconds?: number,
  ): Promise<void>;
  public abstract delete(key: string): Promise<void>;
  public abstract increment(key: string): Promise<number>;
  public abstract appendToList<T>(key: string, value: T): Promise<void>;
  public abstract getList<T>(key: string): Promise<T[]>;
  public abstract trimList(
    key: string,
    start: number,
    stop: number,
  ): Promise<void>;
  public abstract expire(key: string, ttlInSeconds: number): Promise<void>;
  public abstract publish(channel: string, message: string): Promise<void>;
  public abstract subscribe(
    channel: string,
    handler: CacheMessageHandler,
  ): Promise<void>;
  public abstract unsubscribe(
    channel: string,
    handler: CacheMessageHandler,
  ): Promise<void>;
}
