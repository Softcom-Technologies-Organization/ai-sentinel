import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ScanProgressService } from '../../../core/services/scan-progress.service';
import { ScanProgressBarComponent } from './scan-progress-bar.component';

describe('ScanProgressBarComponent', () => {
  let fixture: ComponentFixture<ScanProgressBarComponent>;
  let progressService: ScanProgressService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScanProgressBarComponent],
    }).compileComponents();
    progressService = TestBed.inject(ScanProgressService);
  });

  async function render(status: string, progress: number): Promise<HTMLElement> {
    progressService.updateProgress('AS', { percent: progress });
    fixture = TestBed.createComponent(ScanProgressBarComponent);
    fixture.autoDetectChanges();
    fixture.componentRef.setInput('spaceKey', 'AS');
    fixture.componentRef.setInput('status', status);
    fixture.componentRef.setInput('ariaLabel', 'Progression du scan');
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('Should_ShowAccessibleProgress_When_ScanIsRunning', async () => {
    const element = await render('RUNNING', 42);
    const progressBar = element.querySelector('[role="progressbar"]');

    expect(progressBar?.getAttribute('aria-valuenow')).toBe('42');
    expect(progressBar?.getAttribute('aria-label')).toBe('Progression du scan');
  });

  it('Should_HideProgress_When_ScanIsCompleted', async () => {
    const element = await render('OK', 100);

    expect(element.querySelector('[role="progressbar"]')).toBeNull();
  });
});
