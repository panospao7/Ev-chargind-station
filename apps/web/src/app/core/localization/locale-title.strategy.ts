import { inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  TitleStrategy,
} from '@angular/router';
import { LocaleService } from './locale.service';

/**
 * Per-route localized document titles (ARC-FE-15). Route data carries a
 * dictionary key (`titleKey`); the strategy localizes it via the
 * LocaleService and appends the localized app name.
 */
export class LocaleTitleStrategy extends TitleStrategy {
  private readonly localeService = inject(LocaleService);
  private readonly title = inject(Title);

  // TitleStrategy contract: the router passes the full RouterStateSnapshot.
  override updateTitle(state: RouterStateSnapshot): void {
    const key = this.resolveKey(this.deepestRoute(state.root));
    if (!key) {
      return;
    }
    const localized = this.localeService.text(key);
    const appName = this.localeService.text('app_name');
    this.title.setTitle(
      localized === appName ? localized : `${localized} · ${appName}`,
    );
  }

  /** Walk down from the root to the deepest activated route snapshot. */
  private deepestRoute(root: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
    let route = root;
    while (route.firstChild) {
      route = route.firstChild;
    }
    return route;
  }

  private resolveKey(snapshot: ActivatedRouteSnapshot): string | undefined {
    let route: ActivatedRouteSnapshot | null = snapshot;
    while (route) {
      const key = route.data?.['titleKey'] as string | undefined;
      if (key) {
        return key;
      }
      route = route.parent;
    }
    return undefined;
  }
}
