import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { User } from '../../user/entities/user.entity';
import { TransferStatus } from '../enums/transfer-status.enum';

@Entity('transfers')
@Index('idx_transfers_payer_completed_id', ['payer', 'completedAt', 'id'])
@Index('idx_transfers_payee_completed_id', ['payee', 'completedAt', 'id'])
export class Transfer {
  @PrimaryGeneratedColumn('uuid')
  public id: string;

  @Column({ nullable: false, type: 'int' })
  public value: number;

  @Column({ nullable: true, type: 'text' })
  public description?: string;

  @Index()
  @Column({
    nullable: false,
    type: 'varchar',
    length: 20,
    default: TransferStatus.Pending,
  })
  public status: TransferStatus;

  @Column({ default: null, type: 'text', name: 'failure_reason' })
  public failureReason: string | null;

  @Column({
    default: null,
    type: 'timestamptz',
    name: 'completed_at',
  })
  public completedAt: Date | null;

  @JoinColumn({ name: 'fk_payer_id' })
  @ManyToOne(() => User, { onDelete: 'NO ACTION' })
  public payer: User;

  @JoinColumn({ name: 'fk_payee_id' })
  @ManyToOne(() => User, { onDelete: 'NO ACTION' })
  public payee: User;

  @CreateDateColumn({ type: 'timestamptz', name: 'created_at' })
  public createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz', name: 'updated_at' })
  public updatedAt: Date;
}
