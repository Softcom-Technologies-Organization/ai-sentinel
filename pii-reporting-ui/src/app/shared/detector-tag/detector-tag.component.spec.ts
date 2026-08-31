import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetectorTagComponent } from './detector-tag.component';

describe('DetectorTagComponent', () => {
  let fixture: ComponentFixture<DetectorTagComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetectorTagComponent],
    }).compileComponents();
  });

  it.each([
    { name: 'Should_RenderPresidioTag_When_DetectorIsPresidio', detector: 'PRESIDIO', detectorClass: 'detector-presidio' },
    { name: 'Should_RenderRegexTag_When_DetectorIsRegex', detector: 'REGEX', detectorClass: 'detector-regex' },
    { name: 'Should_RenderUnknownTag_When_DetectorIsUnknownSource', detector: 'UNKNOWN_SOURCE', detectorClass: 'detector-unknown' },
  ])('$name', ({ detector, detectorClass }) => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', detector);
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.textContent.trim()).toBe(detector);
    expect(el.classList).toContain(detectorClass);
  });

  it('Should_ApplySmallClass_When_SmallIsTrue', () => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', 'PRESIDIO');
    fixture.componentRef.setInput('small', true);
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.classList).toContain('detector-tag--small');
  });

  it('Should_HaveAriaLabel_When_DetectorProvided', () => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', 'PRESIDIO');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.getAttribute('aria-label')).toBe('PRESIDIO');
  });
});
