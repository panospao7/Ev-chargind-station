import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { DiscoveryApiAdapter } from '../../api/adapters/discovery-api.adapter';
import { StationDetails } from '../../api/adapters/discovery.types';
import { ApiError } from '../../core/http/api-error';
import { LocaleService } from '../../core/localization/locale.service';
import { TranslatePipe } from '../../core/localization/translate.pipe';
import { ErrorPanelComponent } from '../../shared/ui/error-panel.component';
import { SkeletonListComponent } from '../../shared/ui/skeleton-list.component';

type DetailsStatus = 'loading' | 'loaded' | 'not-found' | 'error';

const UNKNOWN_KIND_PREFIX = 'tariff_component_';

/**
 * PUB-03 — /{locale}/stations/:stationRef (ARC-023 §10): renders public
 * station details from getStationDetails — name, address, coordinates,
 * EVSEs with connectors (type + kW), tariff components formatted from
 * minor units via Intl, totalEvses and the freshness timestamp in
 * Europe/Athens. States: skeleton → loaded | not-found | error(retry).
 */
@Component({
  selector: 'app-pub03-station-details',
  imports: [TranslatePipe, RouterLink, ErrorPanelComponent, SkeletonListComponent],
  templateUrl: './pub-03-station-details.component.html',
  styleUrl: './pub-03-station-details.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pub03StationDetailsComponent implements OnInit {
  /** Bound from the route via withComponentInputBinding. */
  @Input({ required: true }) stationRef!: string;

  private readonly adapter = inject(DiscoveryApiAdapter);
  protected readonly localeService = inject(LocaleService);

  protected readonly status = signal<DetailsStatus>('loading');
  protected readonly station = signal<StationDetails | null>(null);
  protected readonly error = signal<ApiError | null>(null);

  /** Localized freshness timestamp (Europe/Athens, ARC-023 §10). */
  protected readonly freshness = computed(() => {
    const station = this.station();
    if (!station) {
      return '';
    }
    const localeTag = this.localeService.current() === 'el' ? 'el-GR' : 'en-GB';
    return new Intl.DateTimeFormat(localeTag, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'Europe/Athens',
    }).format(new Date(station.updatedAt));
  });

  constructor() {
    // load() runs in ngOnInit: the required stationRef input is bound by
    // withComponentInputBinding before the first lifecycle hook runs, so
    // the request always carries the real route parameter.
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.status.set('loading');
    this.station.set(null);
    this.error.set(null);
    this.adapter.getStationDetails(this.stationRef).subscribe({
      next: (details) => {
        this.station.set(details);
        this.status.set('loaded');
      },
      error: (apiError: ApiError) => {
        this.error.set(apiError);
        this.status.set(apiError.kind === 'not-found' ? 'not-found' : 'error');
      },
    });
  }

  /** Formats a tariff component amount from minor units via Intl. */
  protected formatAmount(amountMinor: number, currency: string): string {
    return new Intl.NumberFormat(
      this.localeService.current() === 'el' ? 'el-GR' : 'en-GB',
      { style: 'currency', currency },
    ).format(amountMinor / 100);
  }

  /** Localized label for a tariff component kind; unknown kinds fall back to the raw kind. */
  protected kindLabel(kind: string): string {
    const key = UNKNOWN_KIND_PREFIX + kind.toLowerCase();
    const localized = this.localeService.text(key);
    // Unknown kinds return the key itself → fall back to the raw kind text.
    return localized === key ? kind : localized;
  }

  protected connectorKw(maxPowerW: number): string {
    return String(maxPowerW / 1000);
  }
}
