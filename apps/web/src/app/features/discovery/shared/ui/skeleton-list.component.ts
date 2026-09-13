import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '../../../../core/localization/translate.pipe';

/**
 * Loading skeleton list (ARC-023 §9.5 initial loading state). Announced as
 * busy via aria-busy and the localized loading text.
 */
@Component({
  selector: 'app-skeleton-list',
  imports: [TranslatePipe],
  template: `
    <div class="skeleton-list" aria-busy="true" role="status">
      <span class="visually-hidden">{{ 'loading' | translate }}</span>
      @for (i of [1, 2, 3]; track i) {
        <div class="card skeleton-card">
          <div class="skeleton skeleton-title"></div>
          <div class="skeleton skeleton-line"></div>
          <div class="skeleton skeleton-line skeleton-line--short"></div>
        </div>
      }
    </div>
  `,
  styles: `
    .skeleton-list {
      display: grid;
      gap: var(--space-3);
    }
    .skeleton-card {
      display: grid;
      gap: var(--space-2);
    }
    .skeleton-title {
      height: 1.25rem;
      width: 40%;
    }
    .skeleton-line {
      width: 80%;
    }
    .skeleton-line--short {
      width: 55%;
    }
    .visually-hidden {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkeletonListComponent {
  readonly lines = input(3);
}
