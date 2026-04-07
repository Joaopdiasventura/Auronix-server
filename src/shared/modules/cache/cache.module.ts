import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { RedisCacheService } from './redis/cache.redis.service';
import { RedisConnectionService } from './redis/redis.connection.service';
import { ICacheService } from './cache.service';

@Module({
  imports: [ConfigModule],
  providers: [
    RedisConnectionService,
    {
      provide: ICacheService,
      useClass: RedisCacheService,
    },
  ],
  exports: [RedisConnectionService, ICacheService],
})
export class CacheModule {}
