import { Routes } from '@angular/router';
import { DEFAULT_LOCALE } from './core/localization/locale';
import { localeMatcher } from './core/localization/locale.matcher';
import { NotFoundPage } from './layout/not-found/not-found.component';

/**
 * Locale-prefixed routing (ARC-023 §9): /el (default) and /en with lazy
 * pub-01/pub-02/pub-03 children. The bare root redirects to the default
 * locale; everything unmatched lands on the localized not-found screen.
 */
export const routes: Routes = [
  {
    matcher: localeMatcher,
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/discovery/pub-01-entry.component').then(
            (m) => m.Pub01EntryComponent,
          ),
        data: { titleKey: 'page_title_entry' },
      },
      {
        path: 'stations',
        loadComponent: () =>
          import('./features/discovery/pub-02-search.component').then(
            (m) => m.Pub02SearchComponent,
          ),
        data: { titleKey: 'page_title_stations' },
      },
      {
        path: 'stations/:stationRef',
        loadComponent: () =>
          import('./features/station-details/pub-03-station-details.component').then(
            (m) => m.Pub03StationDetailsComponent,
          ),
        data: { titleKey: 'page_title_station_details' },
      },
    ],
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: DEFAULT_LOCALE,
  },
  {
    path: '**',
    component: NotFoundPage,
    data: { titleKey: 'page_title_not_found' },
  },
];
