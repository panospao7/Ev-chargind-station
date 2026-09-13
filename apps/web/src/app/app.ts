import { Component } from '@angular/core';
import { PublicShellComponent } from './layout/public-shell/public-shell.component';

/**
 * Application host — delegates to the public shell (header + outlet).
 */
@Component({
  selector: 'app-root',
  imports: [PublicShellComponent],
  template: `<app-public-shell />`,
})
export class App {}
