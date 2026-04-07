import { Client } from 'pg';
import { configureE2eEnvironment, getE2ePostgresUrl } from './e2e-environment';

export async function ensureE2eDatabase(): Promise<void> {
  configureE2eEnvironment();

  const connectionUrl = new URL(getE2ePostgresUrl());
  const databaseName = connectionUrl.pathname.replace(/^\//, '');
  const adminUrl = new URL(connectionUrl.toString());
  adminUrl.pathname = '/postgres';

  const client = new Client({
    connectionString: adminUrl.toString(),
    loggin: false,
  });

  await client.connect();

  try {
    const result = await client.query(
      'SELECT 1 FROM pg_database WHERE datname = $1',
      [databaseName],
    );

    if (result.rowCount == 0)
      await client.query(`CREATE DATABASE "${escapeIdentifier(databaseName)}"`);
  } finally {
    await client.end();
  }

  await ensureE2eSchema();
}

export async function truncateE2eDatabase(): Promise<void> {
  configureE2eEnvironment();

  const client = new Client({
    connectionString: getE2ePostgresUrl(),
  });

  await client.connect();

  try {
    const result = await client.query<{
      schemaname: string;
      tablename: string;
    }>(
      `SELECT schemaname, tablename
         FROM pg_tables
        WHERE schemaname = 'public'`,
    );

    if (result.rows.length > 0) {
      const tables = result.rows.map(({ schemaname, tablename }) => {
        return `"${escapeIdentifier(schemaname)}"."${escapeIdentifier(tablename)}"`;
      });

      await client.query(
        `TRUNCATE TABLE ${tables.join(', ')} RESTART IDENTITY CASCADE`,
      );
    }
  } finally {
    await client.end();
  }
}

function escapeIdentifier(identifier: string): string {
  return identifier.replace(/"/g, '""');
}

async function ensureE2eSchema(): Promise<void> {
  const client = new Client({
    connectionString: getE2ePostgresUrl(),
  });

  await client.connect();

  try {
    await client.query('CREATE EXTENSION IF NOT EXISTS pgcrypto');

    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        email varchar(255) NOT NULL,
        name varchar(100) NOT NULL,
        password varchar(255) NOT NULL,
        balance integer NOT NULL DEFAULT 100000,
        created_at timestamptz NOT NULL DEFAULT now(),
        update_at timestamptz NOT NULL DEFAULT now()
      )
    `);
    await client.query(`
      CREATE UNIQUE INDEX IF NOT EXISTS IDX_users_email
      ON users (email)
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS payment_requests (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        value integer NOT NULL,
        fk_user_id uuid NOT NULL,
        created_at timestamptz NOT NULL DEFAULT now(),
        expires_at timestamptz NOT NULL DEFAULT now() + interval '10 minutes',
        CONSTRAINT FK_payment_requests_user
          FOREIGN KEY (fk_user_id) REFERENCES users (id) ON DELETE NO ACTION
      )
    `);
    await client.query(`
      ALTER TABLE payment_requests
      ADD COLUMN IF NOT EXISTS expires_at timestamptz
    `);
    await client.query(`
      ALTER TABLE payment_requests
      ALTER COLUMN expires_at SET DEFAULT now() + interval '10 minutes'
    `);
    await client.query(`
      UPDATE payment_requests
      SET expires_at = created_at + interval '10 minutes'
      WHERE expires_at IS NULL
    `);
    await client.query(`
      ALTER TABLE payment_requests
      ALTER COLUMN expires_at SET NOT NULL
    `);
    await client.query(`
      CREATE INDEX IF NOT EXISTS IDX_payment_requests_fk_user_id
      ON payment_requests (fk_user_id)
    `);
    await client.query(`
      CREATE INDEX IF NOT EXISTS idx_payment_requests_expires_at
      ON payment_requests (expires_at)
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS transfers (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        value integer NOT NULL,
        description text NOT NULL,
        status varchar(20) NOT NULL DEFAULT 'pending',
        failure_reason text DEFAULT NULL,
        completed_at timestamptz DEFAULT NULL,
        fk_payer_id uuid NOT NULL,
        fk_payee_id uuid NOT NULL,
        created_at timestamptz NOT NULL DEFAULT now(),
        updated_at timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT FK_transfers_payer
          FOREIGN KEY (fk_payer_id) REFERENCES users (id) ON DELETE NO ACTION,
        CONSTRAINT FK_transfers_payee
          FOREIGN KEY (fk_payee_id) REFERENCES users (id) ON DELETE NO ACTION
      )
    `);
    await client.query(`
      CREATE INDEX IF NOT EXISTS IDX_transfers_status
      ON transfers (status)
    `);
    await client.query(`
      CREATE INDEX IF NOT EXISTS IDX_transfers_completed_at
      ON transfers (completed_at)
    `);
    await client.query(`
      CREATE INDEX IF NOT EXISTS IDX_transfers_fk_payer_id
      ON transfers (fk_payer_id)
    `);
    await client.query(`
      CREATE INDEX IF NOT EXISTS IDX_transfers_fk_payee_id
      ON transfers (fk_payee_id)
    `);
  } finally {
    await client.end();
  }
}
