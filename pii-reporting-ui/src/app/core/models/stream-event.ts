import { RawStreamPayload, StreamEventType } from './stream-event-type';

export interface StreamEvent {
  type: StreamEventType;
  dataRaw: string;
  data?: RawStreamPayload;
}
