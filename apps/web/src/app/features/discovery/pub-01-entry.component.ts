import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '../../core/localization/translate.pipe';
import { DiscoveryStore } from './discovery.store';
import { ResultsListComponent } from './results-list.component';
import { SkeletonListComponent } from './shared/ui/skeleton-list.component';
import { EmptyStateComponent } from './shared/ui/empty-state.component';
import { ErrorPanelComponent } from './shared/ui/error-panel.component';

/**
 * PUB-01 — /{locale} discovery entry (ARC-023 §6.2): localized hero plus
 * the default station list via the shared discovery store. Reuses the
 * same results list component as PUB-02.
 */
@Component({
  selector: 'app-pub01-entry',
  imports: [
    TranslatePipe,
    ResultsListComponent,
    SkeletonListComponent,
    EmptyStateComponent,
    ErrorPanelComponent,
  ],
  template: `
    <section class="entry-hero card">
      <h1>{{ 'entry_hero_title' | translate }}</h1>
      <p class="muted">{{ 'entry_hero_hint' | translate }}</p>
    </section>
    <h2>{{ 'results_title' | translate }}</h2>
    @switch (store.status()) {
      @case ('idle') {
        <app-skeleton-list />
      }
      @case ('loading') {
        <app-skeleton-list />
      }
      @case ('empty') {
        <app-empty-state />
      }
      @case ('error') {
        <app-error-panel (retry)="search()" />
      }
      @case ('ready') {
        <app-results-list [stations]="store.results()" />
      }
    }
  `,
  styles: `
    .entry-hero {
      margin-bottom: var(--space-4);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pub01EntryComponent {
  protected readonly store = inject(DiscoveryStore);

  constructor() {
    this.search();
  }

  protected search(): void {
    this.store.search({});
  }
}
