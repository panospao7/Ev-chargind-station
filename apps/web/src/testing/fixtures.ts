import {
  StationDetails,
  StationSummary,
} from '../app/api/adapters/discovery.types';

/**
 * Shared 2-station search fixture (synthetic data — no personal data).
 * Values are API payload content, not UI strings; UI strings always come
 * from the i18n dictionaries.
 */
export const STATION_FIXTURE: StationSummary[] = [
  {
    ref: 'ST-001',
    name: 'Σταθμός Αθήνας',
    address: 'Οδός 1',
    latitude: 37.98,
    longitude: 23.72,
    totalEvses: 4,
  },
  {
    ref: 'ST-002',
    name: 'Σταθμός Πειραιά',
    latitude: 37.94,
    longitude: 23.65,
    totalEvses: 2,
  },
];

/**
 * Station details fixture: 2 EVSEs (CCS 150 kW + Type 2 22 kW), EUR tariff
 * with an energy component of 480 minor units, freshness 2026-09-12T10:00:00Z
 * (matches the verified Intl ground truth for el-GR / en-GB).
 */
export const DETAILS_FIXTURE: StationDetails = {
  ref: 'ST-001',
  name: 'Σταθμός Αθήνας',
  address: 'Οδός 1',
  latitude: 37.98,
  longitude: 23.72,
  city: 'Αθήνα',
  postalCode: '104 31',
  countryCode: 'GR',
  updatedAt: '2026-09-12T10:00:00Z',
  sourceVersion: 7,
  totalEvses: 2,
  evses: [
    { uid: 'EVSE-1', connectors: [{ type: 'CCS', maxPowerW: 150000 }] },
    { uid: 'EVSE-2', connectors: [{ type: 'TYPE2', maxPowerW: 22000 }] },
  ],
  tariff: {
    currency: 'EUR',
    components: [{ kind: 'energy_per_kwh', unit: 'kWh', amountMinor: 480 }],
  },
};
