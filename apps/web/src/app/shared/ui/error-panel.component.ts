import {
  ChangeDetectionStrategy,
  Component,
  output,
} from '@angular/core';
import { TranslatePipe } from '../../core/localization/translate.pipe';

/**
 * Error panel with a retry action (ARC-023 §9.5 "Search unavailable:
 * service warning and retry").
 */
@Component({
  selector: 'app-error-panel',
  imports: [TranslatePipe],
  template: `
    <section class="error-panel" role="alert">
      <h2>{{ 'error_title' | translate }}</h2>
      <button type="button" class="primary" (click)="retry.emit()">
        {{ 'error_retry' | translate }}
      </button>
    </section>
  `,
  styles: `
    .error-panel {
      padding: var(--space-6) var(--space-4);
      text-align: center;
      display: grid;
      gap: var(--space-3);
      justify-items: center;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErrorPanelComponent {
  readonly retry = output<void>();
}
