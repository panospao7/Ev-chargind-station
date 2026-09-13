import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnChanges,
  SimpleChanges,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { StationSummary } from '../../../api/adapters/discovery.types';
import { LocaleService } from '../../../core/localization/locale.service';
import { TranslatePipe } from '../../../core/localization/translate.pipe';
import { MapFallbackPanelComponent } from '../shared/ui/map-fallback-panel.component';
import {
  Bounds,
  MAP_PRESENTATION_ADAPTER,
} from './map-presentation-adapter';

/**
 * Thin map host (ARC-023 §9.5): delegates everything to the
 * MapPresentationAdapter port. Initialization failure or runtime map
 * 'error' events degrade to the fallback panel — the list (a sibling
 * component) is never affected.
 */
@Component({
  selector: 'app-discovery-map',
  imports: [TranslatePipe, MapFallbackPanelComponent],
  template: `
    @if (mapDegraded()) {
      <app-map-fallback-panel />
    } @else {
      <div
        #container
        class="map-container"
        role="application"
        [attr.aria-label]="'view_map' | translate"
      ></div>
    }
  `,
  styles: `
    .map-container {
      min-height: 20rem;
      height: 24rem;
      border: 1px solid var(--border);
      border-radius: var(--radius);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapComponent implements AfterViewInit, OnChanges {
  readonly stations = input.required<StationSummary[]>();
  readonly bounds = input<Bounds | null>(null);

  readonly boundsChange = output<Bounds>();
  readonly stationSelect = output<string>();

  protected readonly mapDegraded = signal(false);

  private readonly adapter = inject(MAP_PRESENTATION_ADAPTER);
  private readonly localeService = inject(LocaleService);
  private readonly containerRef =
    viewChild.required<ElementRef<HTMLDivElement>>('container');

  constructor() {
    this.adapter.onBoundsChange((bounds) => this.boundsChange.emit(bounds));
    this.adapter.onStationClick((ref) => this.stationSelect.emit(ref));
    this.adapter.setLocale(this.localeService.current());
  }

  ngAfterViewInit(): void {
    if (this.mapDegraded()) {
      return;
    }
    this.adapter
      .initialize(this.containerRef().nativeElement, this.bounds() ?? undefined)
      .then(() => {
        this.adapter.setResults(this.stations());
      })
      .catch(() => this.mapDegraded.set(true));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.mapDegraded()) {
      return;
    }
    if (changes['stations'] && !changes['stations'].firstChange) {
      this.adapter.setResults(this.stations());
    }
    if (changes['bounds'] && !changes['bounds'].firstChange && this.bounds()) {
      this.adapter.fitBounds(this.bounds()!);
    }
  }
}
