import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
} from 'typeorm';
import { User } from '../../user/entities/user.entity';

@Entity('payment_requests')
export class PaymentRequest {
  @PrimaryGeneratedColumn('uuid')
  public id: string;

  @Column({ nullable: false, type: 'int' })
  public value: number;

  @Index()
  @Column({
    nullable: false,
    type: 'timestamptz',
    name: 'expires_at',
    default: () => "now() + interval '10 minutes'",
    select: false,
  })
  public expiresAt: Date;

  @Index()
  @JoinColumn({ name: 'fk_user_id' })
  @ManyToOne(() => User, { onDelete: 'NO ACTION' })
  public user: User;

  @CreateDateColumn({ type: 'timestamptz', name: 'created_at' })
  public createdAt: Date;
}
