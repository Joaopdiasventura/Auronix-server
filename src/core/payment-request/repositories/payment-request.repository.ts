import { CreatePaymentRequestDto } from '../dto/create-payment-request.dto';
import { PaymentRequest } from '../entities/payment-request.entity';

export abstract class IPaymentRequestRepository {
  public abstract create(
    createPaymentRequestDto: CreatePaymentRequestDto,
  ): Promise<PaymentRequest>;
  public abstract findById(id: string): Promise<PaymentRequest | null>;
  public abstract delete(id: string): Promise<void>;
  public abstract deleteExpiredById(id: string): Promise<void>;
}
