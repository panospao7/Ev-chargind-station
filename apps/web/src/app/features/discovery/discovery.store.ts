import { Injectable, inject, signal } from '@angular/core';
import { Subscription, tap, timer } from 'rxjs';

import { DiscoveryApiAdapter } from '../../api/adapters/discovery-api.adapter';
import {
  StationSearchQuery,
  StationSummary,
} from '../../api/adapters/discovery.types';
import { ApiError } from '../../core/http/api-error';

export type DiscoveryStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

/**
 * Signal-based discovery store (ARC-023 §9.5 states). Search requests are
 * serialized with a monotonic sequence counter: only the most recently
 * issued search may commit results, so rapid filter changes cannot let a
 * stale response overwrite newer state (switchMap-style cancellation
 * without operator complexity).
 */
@Injectable({ providedIn: 'root' })
export class DiscoveryStore {
  private readonly adapter = inject(DiscoveryApiAdapter);

  readonly results = signal<StationSummary[]>([]);
  readonly status = signal<DiscoveryStatus>('idle');
  readonly error = signal<ApiError | null>(null);

  private requestSeq = 0;
  private inFlight = new Map<number, Subscription>();

  search(query: StationSearchQuery): void {
    const seq = ++this.requestSeq;

    // Cancel any in-flight searches — only the newest may commit.
    for (const [s, sub] of this.inFlight) {
      if (s !== seq) {
        sub.unsubscribe();
      }
    }
    this.inFlight.clear();

    this.status.set('loading');
    this.error.set(null);

    const subscription = timer(0)
      .pipe(tap(() => undefined))
      .subscribe(() => {
        this.adapter.listStations(query).subscribe({
          next: (stations) => {
            if (seq !== this.requestSeq) {
              return; // stale response — ignore
            }
            this.inFlight.delete(seq);
            this.results.set(stations);
            this.status.set(stations.length === 0 ? 'empty' : 'ready');
          },
          error: (apiError: ApiError) => {
            if (seq !== this.requestSeq) {
              return; // stale error — ignore
            }
            this.inFlight.delete(seq);
            this.error.set(apiError);
            this.status.set('error');
          },
        });
      });

    this.inFlight.set(seq, subscription);
  }

  /** Test seam: reset the store between scenarios. */
  reset(): void {
    this.requestSeq++;
    for (const sub of this.inFlight.values()) {
      sub.unsubscribe();
    }
    this.inFlight.clear();
    this.results.set([]);
    this.status.set('idle');
    this.error.set(null);
  }
}
