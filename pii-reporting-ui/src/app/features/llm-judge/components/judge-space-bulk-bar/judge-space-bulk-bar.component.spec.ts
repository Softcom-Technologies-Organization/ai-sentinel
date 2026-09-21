import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { describe, expect, it } from 'vitest';
import { JudgeSpaceBulkBarComponent } from './judge-space-bulk-bar.component';

describe('JudgeSpaceBulkBarComponent', () => {
  it('Should_DisableLaunch_When_ScanStartsAndEnableAfterItFinishes', async () => {
    await TestBed.configureTestingModule({ imports: [
      JudgeSpaceBulkBarComponent,
      TranslocoTestingModule.forRoot({
        translocoConfig: { defaultLang: 'en', availableLangs: ['en'] }, preloadLangs: true,
        langs: { en: { judge: { unavailable: { scanInProgress: 'Available after the scan finishes' } } } }
      })
    ] }).compileComponents();
    const fixture = TestBed.createComponent(JudgeSpaceBulkBarComponent);
    fixture.componentRef.setInput('spaces', [{ key: 'SPACE', name: 'Space' }]);
    fixture.componentRef.setInput('plan', { totalFindings: 3, findingsBySpace: { SPACE: 3 } });
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      `[data-testid="${fixture.componentInstance.testIds.launch}"]`
    );
    expect(button.disabled).toBe(false);

    fixture.componentRef.setInput('disabledReason', 'judge.unavailable.scanInProgress');
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Available after the scan finishes');

    fixture.componentRef.setInput('disabledReason', null);
    fixture.detectChanges();
    expect(button.disabled).toBe(false);

    fixture.componentRef.setInput('plan', { totalFindings: 0, findingsBySpace: {} });
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
  });
});
