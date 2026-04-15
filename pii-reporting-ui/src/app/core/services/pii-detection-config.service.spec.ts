import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PiiDetectionConfigService } from './pii-detection-config.service';
import {
  PiiDetectionConfig,
  PiiTypeConfig,
  UpdatePiiDetectionConfigRequest,
  UpdatePiiTypeConfigRequest,
} from '../models/pii-detection-config.model';

describe('PiiDetectionConfigService', () => {
  const API_CONFIG = '/api/v1/pii-detection/config';
  const API_TYPES = '/api/v1/pii-detection/pii-types';

  let service: PiiDetectionConfigService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PiiDetectionConfigService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PiiDetectionConfigService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getConfig', () => {
    it('should GET the detection config', () => {
      const expected: PiiDetectionConfig = {
        glinerEnabled: true,
        presidioEnabled: false,
        regexEnabled: true,
        defaultThreshold: 0.5,
        nbOfLabelByPass: 10,
      };

      service.getConfig().subscribe((config) => {
        expect(config).toEqual(expected);
      });

      const req = httpMock.expectOne(API_CONFIG);
      expect(req.request.method).toBe('GET');
      req.flush(expected);
    });
  });

  describe('updateConfig', () => {
    it('should PUT the updated config', () => {
      const request: UpdatePiiDetectionConfigRequest = {
        glinerEnabled: true,
        presidioEnabled: true,
        regexEnabled: false,
        defaultThreshold: 0.75,
        nbOfLabelByPass: 5,
      };

      service.updateConfig(request).subscribe();

      const req = httpMock.expectOne(API_CONFIG);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(request);
      req.flush(request);
    });
  });

  describe('getAllPiiTypeConfigs', () => {
    it('should GET all PII type configs', () => {
      const configs: PiiTypeConfig[] = [
        { id: 1, piiType: 'EMAAIL', detector: 'REGEX', enabled: true, threshold: 0.5, category: 'Contact' },
      ];

      service.getAllPiiTypeConfigs().subscribe((result) => {
        expect(result).toEqual(configs);
      });

      const req = httpMock.expectOne(API_TYPES);
      expect(req.request.method).toBe('GET');
      req.flush(configs);
    });
  });

  describe('getPiiTypeConfigsByDetector', () => {
    it('should GET configs filtered by detector', () => {
      service.getPiiTypeConfigsByDetector('GLINER').subscribe();

      const req = httpMock.expectOne(`${API_TYPES}/GLINER`);
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('updatePiiTypeConfig', () => {
    it('should PUT to detector+piiType endpoint', () => {
      const request: UpdatePiiTypeConfigRequest = {
        piiType: 'EMAIL',
        detector: 'REGEX',
        enabled: true,
        threshold: 0.9,
      };

      service.updatePiiTypeConfig('REGEX', 'EMAIL', request).subscribe();

      const req = httpMock.expectOne(`${API_TYPES}/REGEX/EMAIL`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(request);
      req.flush(null);
    });
  });

  describe('bulkUpdatePiiTypeConfigs', () => {
    it('should PUT an array of updates to /bulk', () => {
      const updates: UpdatePiiTypeConfigRequest[] = [
        { piiType: 'EMAIL', detector: 'REGEX', enabled: true, threshold: 0.5 },
        { piiType: 'PHONE', detector: 'REGEX', enabled: false, threshold: 0.8 },
      ];

      service.bulkUpdatePiiTypeConfigs(updates).subscribe();

      const req = httpMock.expectOne(`${API_TYPES}/bulk`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updates);
      req.flush([]);
    });
  });

  describe('getPiiTypesGroupedForUI', () => {
    it('should GET grouped PII types for UI', () => {
      service.getPiiTypesGroupedForUI().subscribe();

      const req = httpMock.expectOne(`${API_TYPES}/grouped`);
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });
});
