import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../../user/entities/user.entity';
import { CreatePaymentRequestDto } from '../dto/create-payment-request.dto';
import { PaymentRequest } from '../entities/payment-request.entity';
import { IPaymentRequestRepository } from './payment-request.repository';

export class PaymentRequestPostgresRepository implements IPaymentRequestRepository {
  public constructor(
    @InjectRepository(PaymentRequest)
    private readonly repository: Repository<PaymentRequest>,
  ) {}

  public create(
    createPaymentRequestDto: CreatePaymentRequestDto,
  ): Promise<PaymentRequest> {
    return this.repository.save(
      this.repository.create({
        value: createPaymentRequestDto.value,
        user: this.createUserReference(createPaymentRequestDto.userId),
      }),
    );
  }

  public findById(id: string): Promise<PaymentRequest | null> {
    return this.repository
      .createQueryBuilder('paymentRequest')
      .leftJoinAndSelect('paymentRequest.user', 'user')
      .where('paymentRequest.id = :id', { id })
      .andWhere('paymentRequest.expires_at > NOW()')
      .select([
        'paymentRequest.id',
        'paymentRequest.value',
        'paymentRequest.createdAt',
        'user.id',
      ])
      .getOne();
  }

  public async delete(id: string): Promise<void> {
    await this.repository.delete(id);
  }

  public async deleteExpiredById(id: string): Promise<void> {
    await this.repository
      .createQueryBuilder()
      .delete()
      .from(PaymentRequest)
      .where('id = :id', { id })
      .andWhere('expires_at <= NOW()')
      .execute();
  }

  private createUserReference(id: string): User {
    const user = new User();
    user.id = id;
    return user;
  }
}
