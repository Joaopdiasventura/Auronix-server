import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  ParseUUIDPipe,
  UseGuards,
  Req,
} from '@nestjs/common';
import { PaymentRequestService } from './payment-request.service';
import { CreatePaymentRequestDto } from './dto/create-payment-request.dto';
import { PaymentRequest } from './entities/payment-request.entity';
import { AuthGuard } from '../../shared/guards/auth/auth.guard';
import type { AuthenticatedRequest } from '../../shared/http/types/authenticated-request.type';

@UseGuards(AuthGuard)
@Controller('payment-request')
export class PaymentRequestController {
  public constructor(
    private readonly paymentRequestService: PaymentRequestService,
  ) {}

  @Post()
  public create(
    @Req() { user }: AuthenticatedRequest,
    @Body() createPaymentRequestDto: CreatePaymentRequestDto,
  ): Promise<PaymentRequest> {
    return this.paymentRequestService.create(user, createPaymentRequestDto);
  }

  @Get(':id')
  public findById(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<PaymentRequest> {
    return this.paymentRequestService.findById(id);
  }
}
