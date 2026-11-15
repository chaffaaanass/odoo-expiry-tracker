import { TestBed } from '@angular/core/testing';

import { PoTrackerService } from './po-tracker-service';

describe('PoTrackerService', () => {
  let service: PoTrackerService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PoTrackerService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
