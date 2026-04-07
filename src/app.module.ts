import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppController } from './app.controller';
import { AppConfig } from './config/app.config';
import { DatabaseConfig } from './config/database.config';
import { BullModule } from '@nestjs/bullmq';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { CoreModule } from './core/core.module';
import { RedisConnectionService } from './shared/modules/cache/redis/redis.connection.service';
import { APP_GUARD } from '@nestjs/core';
import { CacheModule } from './shared/modules/cache/cache.module';

@Module({
  imports: [
    ConfigModule.forRoot({ load: [AppConfig.load, DatabaseConfig.load] }),
    ThrottlerModule.forRoot({ throttlers: [{ ttl: 60000, limit: 10 }] }),
    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        url: configService.get<string>('postgres.url'),
        synchronize: configService.get<boolean>('postgres.synchronize'),
        logging: configService.get<string>('env') == 'development',
        autoLoadEntities: true,
        type: 'postgres',
        entities: [__dirname + '/**/*.entity.{ts, js}'],
      }),
    }),
    BullModule.forRootAsync({
      imports: [CacheModule],
      inject: [RedisConnectionService],
      useFactory: (redisConnectionService: RedisConnectionService) => ({
        connection: redisConnectionService.createBullConnection(),
        defaultJobOptions: {
          attempts: 5,
          backoff: { type: 'exponential', delay: 1000 },
        },
      }),
    }),
    CoreModule,
  ],
  controllers: [AppController],
  providers: [
    {
      provide: APP_GUARD,
      useClass: ThrottlerGuard,
    },
  ],
})
export class AppModule {}
