import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '../../core/localization/translate.pipe';
import { LocaleService } from '../../core/localization/locale.service';
import { StationSearchQuery } from '../../api/adapters/discovery.types';
import { DiscoveryStore } from './discovery.store';
import { ResultsListComponent } from './results-list.component';
import { SkeletonListComponent } from '../../shared/ui/skeleton-list.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorPanelComponent } from '../../shared/ui/error-panel.component';
import { MapComponent } from './map/map.component';
import {
  Bounds,
  MAP_PRESENTATION_ADAPTER,
} from './map/map-presentation-adapter';
import { MaplibreAdapter } from './map/maplibre.adapter';
import {
  LOCAL_DEV_OSM_TILE_CONFIG,
  TILE_PROVIDER_CONFIG,
} from './map/tile-provider.config';

/**
 * Filter vocabulary bounds from the public-discovery-api-v1 contract:
 * connectorType is any string (UI suggestions: CCS/TYPE2); minPowerW is
 * an integer ≥ 1 → minimumPowerKw is an integer ≥ 1 (UI suggestions:
 * 22/50/100). Anything else is an invalid parameter (stripped+announced).
 */
const CONNECTOR_MAX_LENGTH = 24;
const MINIMUM_POWER_KW_MAX = 1000;

type ViewMode = 'list' | 'map';

const BOUND_KEYS = ['west', 'south', 'east', 'north'] as const;

/**
 * PUB-02 — /{locale}/stations search (ARC-023 §9). Search state lives in
 * URL query parameters (ARC-FE-08): connector, minimumPowerKw, view and
 * the map bounds west/south/east/north. Invalid values are stripped and
 * announced via aria-live. The map and the list derive from the same
 * store results.
 *
 * Map movement (ARC-023 §26/§16.4) never triggers a search: pan/zoom
 * only records the viewport internally; the explicit "Search this area"
 * button adopts the recorded bounds (URL write + one search).
 */
@Component({
  selector: 'app-pub02-search',
  imports: [
    TranslatePipe,
    ResultsListComponent,
    SkeletonListComponent,
    EmptyStateComponent,
    ErrorPanelComponent,
    MapComponent,
  ],
  providers: [
    { provide: TILE_PROVIDER_CONFIG, useValue: LOCAL_DEV_OSM_TILE_CONFIG },
    {
      provide: MAP_PRESENTATION_ADAPTER,
      useFactory: () => {
        const config = inject(TILE_PROVIDER_CONFIG);
        return new MaplibreAdapter({
          style: config.style,
          attribution: config.attribution,
        });
      },
    },
  ],
  templateUrl: './pub-02-search.component.html',
  styleUrl: './pub-02-search.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pub02SearchComponent {
  protected readonly localeService = inject(LocaleService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly store = inject(DiscoveryStore);

  private readonly queryParams = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  // Parsed, validated filter state derived from the URL.
  protected readonly connector = signal<string>('');
  protected readonly minimumPowerKw = signal<number | ''>('');
  protected readonly view = signal<ViewMode>('list');
  protected readonly bounds = signal<Bounds | null>(null);
  protected readonly invalidNotice = signal(false);

  /** UI suggestions (the contract allows any valid connector/power value). */
  protected readonly connectorOptions: string[] = ['', 'CCS', 'TYPE2'];
  protected readonly powerOptions: (number | '')[] = ['', 22, 50, 100];

  /** The URL-derived query for the store (geo via bounds mapping). */
  private readonly currentQuery = computed<StationSearchQuery>(() => {
    const bounds = this.bounds();
    return {
      ...(bounds ? { bounds } : {}),
      ...(this.connector() ? { connector: this.connector() } : {}),
      ...(this.minimumPowerKw() ? { minimumPowerKw: this.minimumPowerKw() as number } : {}),
      ...(this.view() ? { view: this.view() } : {}),
    };
  });

  constructor() {
    // Parse URL → filters (invalid values stripped + announced).
    effect(() => {
      const params = this.queryParams();
      if (!params) {
        return;
      }
      this.parseParams(params);
    });

    // Filters → search. The store dedupes/cancels stale requests.
    effect(() => {
      const query = this.currentQuery();
      this.store.search(query);
    });
  }

  private parseParams(params: { get(name: string): string | null }): void {
    let invalid = false;

    // Contract: connectorType is any non-empty string (≤ 24 chars here).
    const connectorRaw = params.get('connector');
    let connector = '';
    if (connectorRaw !== null && connectorRaw !== '') {
      if (connectorRaw.length <= CONNECTOR_MAX_LENGTH) {
        connector = connectorRaw;
      } else {
        invalid = true;
      }
    }

    // Contract: minPowerW ≥ 1 (integer) → minimumPowerKw integer ≥ 1.
    const powerRaw = params.get('minimumPowerKw');
    let power: number | '' = '';
    if (powerRaw !== null && powerRaw !== '') {
      const parsed = Number(powerRaw);
      if (
        Number.isInteger(parsed) &&
        parsed >= 1 &&
        parsed <= MINIMUM_POWER_KW_MAX
      ) {
        power = parsed;
      } else {
        invalid = true;
      }
    }

    const viewRaw = params.get('view');
    const view: ViewMode = viewRaw === 'map' ? 'map' : viewRaw === 'list' ? 'list' : 'list';
    if (viewRaw !== null && viewRaw !== 'list' && viewRaw !== 'map' && viewRaw !== '') {
      invalid = true;
    }

    const boundsParsed: Record<string, number> = {};
    let boundsInvalid = false;
    for (const key of BOUND_KEYS) {
      const raw = params.get(key);
      if (raw === null) {
        continue;
      }
      const value = Number(raw);
      if (Number.isFinite(value) && value >= -180 && value <= 180) {
        boundsParsed[key] = value;
      } else {
        boundsInvalid = true;
      }
    }
    const allBoundsPresent = BOUND_KEYS.every((key) => key in boundsParsed);
    const anyBoundsPresent = BOUND_KEYS.some((key) => key in boundsParsed);
    let bounds: Bounds | null = null;
    if (allBoundsPresent && !boundsInvalid) {
      const candidate: Bounds = {
        west: boundsParsed['west'],
        south: boundsParsed['south'],
        east: boundsParsed['east'],
        north: boundsParsed['north'],
      };
      // Ordering: west<east and south<north; inverted boxes are invalid.
      if (candidate.west < candidate.east && candidate.south < candidate.north) {
        bounds = candidate;
      } else {
        invalid = true;
      }
    }
    if (boundsInvalid || (anyBoundsPresent && !allBoundsPresent)) {
      invalid = true;
    }

    this.connector.set(connector);
    this.minimumPowerKw.set(power);
    this.view.set(view);
    this.bounds.set(bounds);

    // Strip navigation echo: once the URL has been sanitized, the
    // re-parse sees valid params. Keep the announcement visible so the
    // aria-live region actually announces the removal (ARC-023 §9.2:
    // invalid values are removed *and announced*) — clearing it in the
    // same stability window would remove it before it can be perceived.
    if (!invalid && this.invalidNotice() && this.stripNavigation) {
      this.stripNavigation = false;
      return;
    }
    this.invalidNotice.set(invalid);

    // Strip invalid values from the URL (replace, no history spam).
    if (invalid) {
      this.stripNavigation = true;
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: this.urlParams(),
        queryParamsHandling: '',
        replaceUrl: true,
      });
    }
  }

  /** Current filters as URL query params (contract names, bounded precision). */
  protected urlParams(): Record<string, string> {
    const params: Record<string, string> = {};
    if (this.connector()) {
      params['connector'] = this.connector();
    }
    if (this.minimumPowerKw()) {
      params['minimumPowerKw'] = String(this.minimumPowerKw());
    }
    if (this.view() !== 'list') {
      params['view'] = this.view();
    }
    const bounds = this.bounds();
    if (bounds) {
      params['west'] = bounds.west.toFixed(5);
      params['south'] = bounds.south.toFixed(5);
      params['east'] = bounds.east.toFixed(5);
      params['north'] = bounds.north.toFixed(5);
    }
    return params;
  }

  protected onConnectorChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.connector.set(value);
    void this.writeUrl();
  }

  protected onPowerChange(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    const value = raw === '' ? '' : Number(raw);
    this.minimumPowerKw.set(value);
    void this.writeUrl();
  }

  protected onViewChange(mode: ViewMode): void {
    this.view.set(mode);
    void this.writeUrl();
  }

  /**
   * "Search this area": adopt the map's recorded viewport as the bounds
   * filter (URL write + exactly one search). Map movement alone never
   * reaches here (ARC-023 §26/§16.4 no automatic search while panning).
   */
  protected searchThisArea(): void {
    this.bounds.set(this.lastMapBounds);
    void this.writeUrl();
  }

  protected onMapBoundsChange(bounds: Bounds): void {
    // Map movement only records the viewport internally: no URL write and
    // no search while panning/zooming (ARC-023 §26/§16.4). The recorded
    // bounds are adopted explicitly via "Search this area".
    this.lastMapBounds = bounds;
  }

  protected retrySearch(): void {
    this.store.search(this.currentQuery());
  }

  private lastMapBounds: Bounds | null = null;

  /**
   * True while the pending strip navigation (replaceUrl) is in flight;
   * its queryParamMap echo must not clear the invalid-params notice.
   */
  private stripNavigation = false;

  private async writeUrl(): Promise<void> {
    await this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.urlParams(),
      queryParamsHandling: '',
      replaceUrl: true,
    });
  }
}
