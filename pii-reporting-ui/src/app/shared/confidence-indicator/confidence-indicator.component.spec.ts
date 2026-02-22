import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfidenceIndicatorComponent } from './confidence-indicator.component';

describe('ConfidenceIndicatorComponent', () => {
  let fixture: ComponentFixture<ConfidenceIndicatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfidenceIndicatorComponent],
    }).compileComponents();
  });

  it('Should_DisplayGreenAt95Percent_When_ConfidenceIsHigh', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.95);
    fixture.detectChanges();
    const pct = fixture.nativeElement.querySelector('.confidence-pct');
    expect(pct.textContent.trim()).toBe('95%');
    expect(pct.classList).toContain('confidence--high');
  });

  it('Should_DisplayYellowAt78Percent_When_ConfidenceIsMedium', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.78);
    fixture.detectChanges();
    const pct = fixture.nativeElement.querySelector('.confidence-pct');
    expect(pct.textContent.trim()).toBe('78%');
    expect(pct.classList).toContain('confidence--medium');
  });

  it('Should_DisplayRedAt55Percent_When_ConfidenceIsLow', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.55);
    fixture.detectChanges();
    const pct = fixture.nativeElement.querySelector('.confidence-pct');
    expect(pct.textContent.trim()).toBe('55%');
    expect(pct.classList).toContain('confidence--low');
  });

  it('Should_SetBarWidth_When_ValueProvided', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.82);
    fixture.detectChanges();
    const bar = fixture.nativeElement.querySelector('.confidence-bar-fill') as HTMLElement;
    expect(bar.style.width).toBe('82%');
  });

  it('Should_DisplayGreenAtExactly90Percent_When_ConfidenceIsOnBoundary', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.90);
    fixture.detectChanges();
    const pct = fixture.nativeElement.querySelector('.confidence-pct');
    expect(pct.textContent.trim()).toBe('90%');
    expect(pct.classList).toContain('confidence--high');
  });

  it('Should_DisplayYellowAtExactly70Percent_When_ConfidenceIsOnBoundary', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.70);
    fixture.detectChanges();
    const pct = fixture.nativeElement.querySelector('.confidence-pct');
    expect(pct.textContent.trim()).toBe('70%');
    expect(pct.classList).toContain('confidence--medium');
  });

  it('Should_HaveAriaAttributes_When_ValueProvided', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.85);
    fixture.detectChanges();
    const indicator = fixture.nativeElement.querySelector('.confidence-indicator');
    expect(indicator.getAttribute('role')).toBe('meter');
    expect(indicator.getAttribute('aria-valuenow')).toBe('85');
    expect(indicator.getAttribute('aria-valuemin')).toBe('0');
    expect(indicator.getAttribute('aria-valuemax')).toBe('100');
  });
});
