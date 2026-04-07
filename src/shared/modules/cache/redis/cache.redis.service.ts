import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import Redis from 'ioredis';
import { ICacheService } from '../cache.service';
import { CacheMessageHandler } from '../types/cache-message-handler.type';
import { RedisConnectionService } from './redis.connection.service';

@Injectable()
export class RedisCacheService
  implements ICacheService, OnModuleInit, OnModuleDestroy
{
  private readonly client: Redis;
  private readonly subscriber: Redis;
  private readonly handlers = new Map<string, Set<CacheMessageHandler>>();
  private readonly onMessage = (channel: string, message: string): void => {
    void this.handleMessage(channel, message);
  };

  public constructor(
    private readonly redisConnectionService: RedisConnectionService,
  ) {
    this.client = redisConnectionService.createClient();
    this.subscriber = redisConnectionService.createClient();
    this.subscriber.on('message', this.onMessage);
  }

  public async onModuleInit(): Promise<void> {
    await Promise.all([
      this.redisConnectionService.connect(this.client),
      this.redisConnectionService.connect(this.subscriber),
    ]);
  }

  public async onModuleDestroy(): Promise<void> {
    this.subscriber.off('message', this.onMessage);
    const channels = [...this.handlers.keys()];
    if (channels.length > 0) await this.subscriber.unsubscribe(...channels);
    this.handlers.clear();
    await Promise.all([this.client.quit(), this.subscriber.quit()]);
  }

  public async get<T>(key: string): Promise<T | null> {
    const value = await this.client.get(key);
    if (!value) return null;

    return JSON.parse(value) as T;
  }

  public async set<T>(
    key: string,
    value: T,
    ttlInSeconds?: number,
  ): Promise<void> {
    const serializedValue = JSON.stringify(value);

    if (ttlInSeconds) {
      await this.client.set(key, serializedValue, 'EX', ttlInSeconds);
      return;
    }

    await this.client.set(key, serializedValue);
  }

  public async delete(key: string): Promise<void> {
    await this.client.del(key);
  }

  public increment(key: string): Promise<number> {
    return this.client.incr(key);
  }

  public async appendToList<T>(key: string, value: T): Promise<void> {
    await this.client.rpush(key, JSON.stringify(value));
  }

  public async getList<T>(key: string): Promise<T[]> {
    const values = await this.client.lrange(key, 0, -1);
    return values.map((value) => JSON.parse(value) as T);
  }

  public async trimList(
    key: string,
    start: number,
    stop: number,
  ): Promise<void> {
    await this.client.ltrim(key, start, stop);
  }

  public async expire(key: string, ttlInSeconds: number): Promise<void> {
    await this.client.expire(key, ttlInSeconds);
  }

  public async publish(channel: string, message: string): Promise<void> {
    await this.client.publish(channel, message);
  }

  public async subscribe(
    channel: string,
    handler: CacheMessageHandler,
  ): Promise<void> {
    const channelHandlers = this.handlers.get(channel);
    if (channelHandlers) {
      channelHandlers.add(handler);
      return;
    }

    this.handlers.set(channel, new Set([handler]));
    await this.subscriber.subscribe(channel);
  }

  public async unsubscribe(
    channel: string,
    handler: CacheMessageHandler,
  ): Promise<void> {
    const channelHandlers = this.handlers.get(channel);
    if (!channelHandlers) return;

    channelHandlers.delete(handler);
    if (channelHandlers.size > 0) return;

    this.handlers.delete(channel);
    await this.subscriber.unsubscribe(channel);
  }

  private readonly handleMessage = async (
    channel: string,
    message: string,
  ): Promise<void> => {
    const channelHandlers = this.handlers.get(channel);
    if (!channelHandlers) return;

    for (const handler of channelHandlers) await handler(message);
  };
}
