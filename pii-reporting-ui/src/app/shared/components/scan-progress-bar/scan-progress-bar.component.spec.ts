import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ScanProgressBarComponent} from './scan-progress-bar.component';
import {ScanProgressService} from '../../../core/services/scan-progress.service';
import {signal} from '@angular/core';
import {ProgressBarModule} from 'primeng/progressbar';

describe('ScanProgressBarComponent', () => {
  let component: ScanProgressBarComponent;
  let fixture: ComponentFixture<ScanProgressBarComponent>;
  let progressServiceSpy: {
    getProgressPercent: jest.Mock;
    progress: any;
  };
  let progressSignal: any;

  beforeEach(async () => {
    progressSignal = signal(0);

    progressServiceSpy = {
      getProgressPercent: jest.fn().mockImplementation(() => progressSignal()),
      progress: signal({})
    };

    await TestBed.configureTestingModule({
      imports: [ScanProgressBarComponent, ProgressBarModule],
      providers: [
        { provide: ScanProgressService, useValue: progressServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ScanProgressBarComponent);
    component = fixture.componentInstance;
  });

  it('Should_CreateComponent_When_Instantiated', () => {
    expect(component).toBeTruthy();
  });

  it('Should_DisplayProgressBar_When_SpaceKeyProvided', () => {
    // Arrange
    component.spaceKey = 'TEST';
    progressServiceSpy.getProgressPercent.mockReturnValue(50);

    // Act
    fixture.detectChanges();

    // Assert
    const progressBar = fixture.nativeElement.querySelector('p-progressbar');
    expect(progressBar).toBeTruthy();
  });

  it('Should_ShowZeroPercent_When_NoProgressData', () => {
    // Arrange
    component.spaceKey = 'TEST';
    progressSignal.set(0);

    // Act
    fixture.detectChanges();

    // Assert
    expect(component.progressPercent()).toBe(0);
    expect(progressServiceSpy.getProgressPercent).toHaveBeenCalled();
  });

  it('Should_ShowCorrectPercent_When_ProgressExists', () => {
    // Arrange
    component.spaceKey = 'TEST';
    progressSignal.set(75);

    // Act
    fixture.detectChanges();

    // Assert
    expect(component.progressPercent()).toBe(75);
    expect(progressServiceSpy.getProgressPercent).toHaveBeenCalled();
  });

  it('Should_UpdateProgress_When_ServiceChanges', () => {
    // Arrange
    component.spaceKey = 'TEST';
    progressSignal.set(30);
    fixture.detectChanges();
    expect(component.progressPercent()).toBe(30);

    // Act - Simulate service update by changing the signal
    progressSignal.set(60);
    fixture.detectChanges();

    // Assert
    expect(component.progressPercent()).toBe(60);
  });
});
