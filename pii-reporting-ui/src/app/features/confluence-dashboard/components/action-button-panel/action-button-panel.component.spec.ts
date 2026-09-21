import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ActionButtonPanelComponent } from './action-button-panel.component';

@Component({
  standalone: true,
  imports: [ActionButtonPanelComponent],
  template: `
    <app-action-button-panel [accent]="accent" [pending]="pending">
      <button type="button" data-testid="projected">go</button>
    </app-action-button-panel>
  `
})
class HostComponent {
  accent: 'scan' | 'judge' = 'scan';
  pending = false;
}

describe('ActionButtonPanelComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  function panel(): HTMLElement {
    return fixture.nativeElement.querySelector('.action-button-panel');
  }

  it('Should_ProjectButtonsWithoutJudgeAccent_When_AccentIsScan', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="projected"]')).not.toBeNull();
    expect(panel().classList.contains('accent-judge')).toBe(false);
  });

  it('Should_MarkTheJudgeAccent_When_AccentIsJudge', () => {
    fixture.componentInstance.accent = 'judge';
    fixture.detectChanges();

    expect(panel().classList.contains('accent-judge')).toBe(true);
  });

  it('Should_CoverTheButtonsWithASpinner_When_AnActionIsPending', () => {
    expect(fixture.nativeElement.querySelector('.action-button-panel-overlay')).toBeNull();

    fixture.componentInstance.pending = true;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.action-button-panel-overlay')).not.toBeNull();
  });
});
