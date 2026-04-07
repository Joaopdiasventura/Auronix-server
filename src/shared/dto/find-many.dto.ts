export class FindManyDto<T> {
  public data: T[];
  public next: Partial<T> | null;
}
