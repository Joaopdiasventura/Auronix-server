export interface DatabaseConfigType {
  postgres: {
    url: string;
    synchronize: boolean;
  };
  redis: { url: string };
}
