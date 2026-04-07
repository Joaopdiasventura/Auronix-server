import {
  Body,
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { TransferService } from './transfer.service';
import { CreateTransferDto } from './dto/create-transfer.dto';
import { FindTransferDto } from './dto/find-transfer.dto';
import { Transfer } from './entities/transfer.entity';
import { AuthGuard } from '../../shared/guards/auth/auth.guard';
import { FindManyDto } from '../../shared/dto/find-many.dto';
import type { AuthenticatedRequest } from '../../shared/http/types/authenticated-request.type';

@UseGuards(AuthGuard)
@Controller('transfer')
export class TransferController {
  public constructor(private readonly transferService: TransferService) {}

  @Post()
  public create(
    @Req() { user }: AuthenticatedRequest,
    @Body() createTransferDto: CreateTransferDto,
  ): Promise<Transfer> {
    return this.transferService.create(user, createTransferDto);
  }

  @Get(':id')
  public findById(
    @Req() { user }: AuthenticatedRequest,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<Transfer> {
    return this.transferService.findById(id, user);
  }

  @Get()
  public findMany(
    @Req() { user }: AuthenticatedRequest,
    @Query() findTransferDto: FindTransferDto,
  ): Promise<FindManyDto<Transfer>> {
    return this.transferService.findMany(user, findTransferDto);
  }
}
