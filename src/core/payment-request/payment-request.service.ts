import {
  Injectable,
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { UserService } from '../user/user.service';
import { PaymentRequestQueue } from './payment-request.queue';
import { CreatePaymentRequestDto } from './dto/create-payment-request.dto';
import { PaymentRequest } from './entities/payment-request.entity';
import { IPaymentRequestRepository } from './repositories/payment-request.repository';

@Injectable()
export class PaymentRequestService {
  private readonly logger = new Logger(PaymentRequestService.name);

  public constructor(
    private readonly paymentRequestRepository: IPaymentRequestRepository,
    private readonly userService: UserService,
    private readonly paymentRequestQueue: PaymentRequestQueue,
  ) {}

  public async create(
    user: string,
    createPaymentRequestDto: CreatePaymentRequestDto,
  ): Promise<PaymentRequest> {
    await this.userService.findById(user);

    const paymentRequest = await this.paymentRequestRepository.create(
      this.createPaymentRequestData(user, createPaymentRequestDto),
    );

    try {
      await this.paymentRequestQueue.enqueueExpiration(paymentRequest.id);
    } catch (error) {
      try {
        await this.paymentRequestRepository.delete(paymentRequest.id);
      } catch (deleteError) {
        this.logger.error(
          `Failed to rollback payment request ${paymentRequest.id} after queue enqueue failure`,
          deleteError,
        );
      }

      this.logger.error(
        `Failed to enqueue expiration job for payment request ${paymentRequest.id}`,
        error,
      );
      throw new InternalServerErrorException(
        'Nao foi possivel criar a cobranca',
      );
    }

    return paymentRequest;
  }

  public async findById(id: string): Promise<PaymentRequest> {
    const paymentRequest = await this.paymentRequestRepository.findById(id);
    if (!paymentRequest) throw new NotFoundException('Cobranca nao encontrada');
    return paymentRequest;
  }

  public deleteExpired(id: string): Promise<void> {
    return this.paymentRequestRepository.deleteExpiredById(id);
  }

  private createPaymentRequestData(
    userId: string,
    createPaymentRequestDto: CreatePaymentRequestDto,
  ): CreatePaymentRequestDto {
    return {
      value: createPaymentRequestDto.value,
      userId,
    };
  }
}
