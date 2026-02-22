import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetectorTagComponent } from './detector-tag.component';

describe('DetectorTagComponent', () => {
  let fixture: ComponentFixture<DetectorTagComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetectorTagComponent],
    }).compileComponents();
  });

  it('Should_RenderGlinerTag_When_DetectorIsGliner', () => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', 'GLINER');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.textContent.trim()).toBe('GLINER');
    expect(el.classList).toContain('detector-gliner');
  });

  it('Should_RenderPresidioTag_When_DetectorIsPresidio', () => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', 'PRESIDIO');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.textContent.trim()).toBe('PRESIDIO');
    expect(el.classList).toContain('detector-presidio');
  });

  it('Should_RenderRegexTag_When_DetectorIsRegex', () => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', 'REGEX');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.textContent.trim()).toBe('REGEX');
    expect(el.classList).toContain('detector-regex');
  });

  it('Should_ApplySmallClass_When_SmallIsTrue', () => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', 'GLINER');
    fixture.componentRef.setInput('small', true);
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.classList).toContain('detector-tag--small');
  });

  it('Should_RenderUnknownTag_When_DetectorIsUnknownSource', () => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', 'UNKNOWN_SOURCE');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.textContent.trim()).toBe('UNKNOWN_SOURCE');
    expect(el.classList).toContain('detector-unknown');
  });

  it('Should_HaveAriaLabel_When_DetectorProvided', () => {
    fixture = TestBed.createComponent(DetectorTagComponent);
    fixture.componentRef.setInput('detector', 'PRESIDIO');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.detector-tag');
    expect(el.getAttribute('aria-label')).toBe('PRESIDIO');
  });
});
