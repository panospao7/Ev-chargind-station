import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../../core/localization/translate.pipe';
import { LocaleService } from '../../core/localization/locale.service';
import { StationSummary } from '../../api/adapters/discovery.types';

/**
 * Shared station result list (ARC-023 §9.5: the map and the list derive
 * from the same result collection). Each card links to
 * /{locale}/stations/{ref}; labels are localized; coordinates are shown
 * with bounded precision.
 */
@Component({
  selector: 'app-results-list',
  imports: [RouterLink, TranslatePipe],
  templateUrl: './results-list.component.html',
  styleUrl: './results-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultsListComponent {
  readonly stations = input.required<StationSummary[]>();

  protected readonly localeService = inject(LocaleService);

  protected formatCoordinate(value: number): string {
    return value.toFixed(5);
  }
}
