import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { UserService } from './user.service';
import { UserController } from './user.controller';
import { TypeOrmModule } from '@nestjs/typeorm';
import { User } from './entities/user.entity';
import { IUserRepository } from './repositories/user.repository';
import { UserPostgresRepository } from './repositories/user.postgres.repository';
import { AuthModule } from '../../shared/modules/auth/auth.module';

@Module({
  imports: [ConfigModule, TypeOrmModule.forFeature([User]), AuthModule],
  controllers: [UserController],
  providers: [
    UserService,
    { provide: IUserRepository, useClass: UserPostgresRepository },
  ],
  exports: [UserService, IUserRepository],
})
export class UserModule {}
