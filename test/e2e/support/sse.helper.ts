export interface SseEvent {
  id: string;
  event: string;
  data: unknown;
}

export interface SseCollector {
  events: SseEvent[];
  waitFor(
    predicate: (events: SseEvent[]) => boolean,
    timeoutMs?: number,
  ): Promise<SseEvent[]>;
  close(): Promise<void>;
}

export async function createSseCollector(options: {
  baseUrl: string;
  cookie: string;
  lastEventId?: string;
}): Promise<SseCollector> {
  const controller = new AbortController();
  const headers = new Headers({ Cookie: options.cookie });

  if (options.lastEventId) headers.set('Last-Event-ID', options.lastEventId);

  const response = await fetch(`${options.baseUrl}/notification/stream`, {
    headers,
    signal: controller.signal,
  });

  if (!response.ok)
    throw new Error(`Failed to connect to SSE stream: ${response.status}`);
  if (!response.body)
    throw new Error('SSE stream did not return a readable body');

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const events: SseEvent[] = [];

  let buffer = '';
  let streamError: Error | null = null;

  const readerPromise = (async (): Promise<void> => {
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) return;

        buffer += decoder.decode(value, { stream: true });
        const chunks = drainChunks();

        for (const chunk of chunks) {
          const event = parseSseChunk(chunk);
          if (event) events.push(event);
        }
      }
    } catch (error) {
      if (controller.signal.aborted) return;
      streamError =
        error instanceof Error
          ? error
          : new Error('Unknown SSE stream processing error');
    }
  })();

  return {
    events,
    async waitFor(
      predicate: (streamEvents: SseEvent[]) => boolean,
      timeoutMs = 15000,
    ): Promise<SseEvent[]> {
      const deadline = Date.now() + timeoutMs;

      while (Date.now() < deadline) {
        if (streamError) throw streamError;
        if (predicate(events)) return [...events];
        await sleep(50);
      }

      throw new Error('Timed out waiting for SSE events');
    },
    async close(): Promise<void> {
      controller.abort();

      try {
        await reader.cancel();
      } catch (error) {
        void error;
      }

      await readerPromise;
    },
  };

  function drainChunks(): string[] {
    const chunks: string[] = [];

    while (true) {
      const separatorIndex = buffer.indexOf('\n\n');
      if (separatorIndex < 0) return chunks;

      chunks.push(buffer.slice(0, separatorIndex));
      buffer = buffer.slice(separatorIndex + 2);
    }
  }
}

function parseSseChunk(chunk: string): SseEvent | null {
  const normalizedChunk = chunk.replace(/\r\n/g, '\n');
  const lines = normalizedChunk.split('\n');

  let id = '';
  let event = '';
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith(':')) continue;
    if (line.startsWith('retry:')) continue;
    if (line.startsWith('id:')) {
      id = line.slice(3).trim();
      continue;
    }
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
      continue;
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }

  if (id == '' || event == '' || dataLines.length == 0) return null;

  return {
    id,
    event,
    data: JSON.parse(dataLines.join('\n')),
  };
}

async function sleep(durationInMs: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, durationInMs));
}
