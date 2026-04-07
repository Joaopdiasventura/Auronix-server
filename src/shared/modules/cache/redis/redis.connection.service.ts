import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

@Injectable()
export class RedisConnectionService {
  private readonly url: string;

  public constructor(private readonly configService: ConfigService) {
    this.url = this.configService.get<string>('redis.url')!;
  }

  public createClient(): Redis {
    return new Redis(this.url, {
      lazyConnect: true,
      maxRetriesPerRequest: null,
    });
  }

  public createBullConnection(): { url: string } {
    return { url: this.url };
  }

  public async connect(client: Redis): Promise<void> {
    if (client.status == 'ready') return;
    await client.connect();
  }
}
