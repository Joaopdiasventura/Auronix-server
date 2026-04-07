import { Controller, Get } from '@nestjs/common';

@Controller()
export class AppController {
  @Get('health')
  public getHealth(): string {
    return 'ok';
  }
}
