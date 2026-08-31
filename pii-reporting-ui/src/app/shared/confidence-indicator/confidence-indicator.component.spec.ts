import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfidenceIndicatorComponent } from './confidence-indicator.component';

describe('ConfidenceIndicatorComponent', () => {
  let fixture: ComponentFixture<ConfidenceIndicatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfidenceIndicatorComponent],
    }).compileComponents();
  });

  it.each([
    { name: 'Should_DisplayGreenAt95Percent_When_ConfidenceIsHigh', value: 0.95, percent: '95%', levelClass: 'confidence--high' },
    { name: 'Should_DisplayYellowAt78Percent_When_ConfidenceIsMedium', value: 0.78, percent: '78%', levelClass: 'confidence--medium' },
    { name: 'Should_DisplayRedAt55Percent_When_ConfidenceIsLow', value: 0.55, percent: '55%', levelClass: 'confidence--low' },
    { name: 'Should_DisplayGreenAtExactly90Percent_When_ConfidenceIsOnBoundary', value: 0.9, percent: '90%', levelClass: 'confidence--high' },
    { name: 'Should_DisplayYellowAtExactly70Percent_When_ConfidenceIsOnBoundary', value: 0.7, percent: '70%', levelClass: 'confidence--medium' },
  ])('$name', ({ value, percent, levelClass }) => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    const pct = fixture.nativeElement.querySelector('.confidence-pct');
    expect(pct.textContent.trim()).toBe(percent);
    expect(pct.classList).toContain(levelClass);
  });

  it('Should_SetBarWidth_When_ValueProvided', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.82);
    fixture.detectChanges();
    const bar = fixture.nativeElement.querySelector('.confidence-bar-fill') as HTMLElement;
    expect(bar.style.width).toBe('82%');
  });

  it('Should_HaveNativeMeterElement_When_ValueProvided', () => {
    fixture = TestBed.createComponent(ConfidenceIndicatorComponent);
    fixture.componentRef.setInput('value', 0.85);
    fixture.detectChanges();
    const meter = fixture.nativeElement.querySelector('meter');
    expect(meter).toBeTruthy();
    expect(meter.getAttribute('value')).toBe('85');
    expect(meter.getAttribute('min')).toBe('0');
    expect(meter.getAttribute('max')).toBe('100');
  });
});
