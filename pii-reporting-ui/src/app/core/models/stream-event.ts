import { ConfluenceContentPersonallyIdentifiableInformationScanResult, StreamEventType } from './stream-event-type';

export interface StreamEvent {
  type: StreamEventType;
  data?: ConfluenceContentPersonallyIdentifiableInformationScanResult;
}
