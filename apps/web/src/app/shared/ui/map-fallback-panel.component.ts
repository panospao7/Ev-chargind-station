import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslatePipe } from '../../core/localization/translate.pipe';

/**
 * Map fallback panel (ARC-023 §9.5: map initialization failed / tile
 * provider unavailable — the list remains fully operational).
 */
@Component({
  selector: 'app-map-fallback-panel',
  imports: [TranslatePipe],
  template: `
    <section class="map-fallback card" role="status">
      <h2>{{ 'map_fallback_title' | translate }}</h2>
      <p class="muted">{{ 'map_fallback_hint' | translate }}</p>
    </section>
  `,
  styles: `
    .map-fallback {
      min-height: 16rem;
      display: grid;
      place-content: center;
      text-align: center;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapFallbackPanelComponent {}
