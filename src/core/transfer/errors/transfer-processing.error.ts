export class TransferProcessingError extends Error {
  public constructor(message: string) {
    super(message);
    this.name = TransferProcessingError.name;
  }
}
