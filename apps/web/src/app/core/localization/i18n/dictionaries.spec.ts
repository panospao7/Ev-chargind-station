import { EL } from './el';
import { EN } from './en';

/**
 * Dictionary key parity (planner decision: signal dictionary i18n). Both
 * dictionaries must expose exactly the same keys — a missing key would
 * render the raw key (fail-visible) in one locale only.
 */
describe('i18n dictionary key parity', () => {
  it('EL and EN expose exactly the same keys', () => {
    const elKeys = Object.keys(EL).sort();
    const enKeys = Object.keys(EN).sort();
    expect(elKeys).toEqual(enKeys);
  });

  it('no dictionary value is empty', () => {
    for (const dictionary of [EL, EN]) {
      for (const [key, value] of Object.entries(dictionary)) {
        expect(value.trim().length).toBeGreaterThan(0);
        expect(key).not.toBe('');
      }
    }
  });
});
