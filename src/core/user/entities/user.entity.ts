import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('users')
export class User {
  @PrimaryGeneratedColumn('uuid')
  public id: string;

  @Index({ unique: true })
  @Column({ nullable: false, type: 'varchar', length: 255 })
  public email: string;

  @Column({ nullable: false, type: 'varchar', length: 100 })
  public name: string;

  @Column({ nullable: false, type: 'varchar', length: 255 })
  public password?: string;

  @Column({ nullable: false, type: 'int', default: 1000_00 })
  public balance: number;

  @CreateDateColumn({ type: 'timestamptz', name: 'created_at' })
  public createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz', name: 'update_at' })
  public updatedAt: Date;
}
