import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '../../../../core/localization/translate.pipe';

/**
 * Empty-state panel (ARC-023 §9.5 "No stations": suggestions to widen
 * bounds / change filters).
 */
@Component({
  selector: 'app-empty-state',
  imports: [TranslatePipe],
  template: `
    <section class="empty-state" role="status">
      <h2>{{ 'empty_title' | translate }}</h2>
      <p class="muted">{{ 'empty_hint' | translate }}</p>
    </section>
  `,
  styles: `
    .empty-state {
      padding: var(--space-6) var(--space-4);
      text-align: center;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly compact = input(false);
}
