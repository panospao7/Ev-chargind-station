/**
 * Hand-written type-safe mirrors of the public Discovery API contract
 * (owner decision: hand-written adapters this slice; openapi-generator
 * setup remains an EPIC-06 follow-up).
 *
 * Each interface mirrors exactly one schema:
 * contracts/openapi/public-discovery-api-v1.yaml#/components/schemas/X
 */

/** Mirrors contracts/openapi/public-discovery-api-v1.yaml#/components/schemas/StationSummary */
export interface StationSummary {
  ref: string;
  name: string;
  address?: string;
  latitude: number;
  longitude: number;
  totalEvses?: number;
}

/** Mirrors contracts/openapi/public-discovery-api-v1.yaml#/components/schemas/EvseView */
export interface EvseView {
  uid: string;
  connectors: ConnectorView[];
}

/** Mirrors contracts/openapi/public-discovery-api-v1.yaml#/components/schemas/ConnectorView */
export interface ConnectorView {
  type: string;
  maxPowerW: number;
}

/** Mirrors contracts/openapi/public-discovery-api-v1.yaml#/components/schemas/TariffView */
export interface TariffView {
  currency: string;
  components: ComponentView[];
}

/** Mirrors contracts/openapi/public-discovery-api-v1.yaml#/components/schemas/ComponentView */
export interface ComponentView {
  kind: string;
  unit: string;
  amountMinor: number;
}

/** Mirrors contracts/openapi/public-discovery-api-v1.yaml#/components/schemas/StationDetails */
export interface StationDetails {
  ref: string;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  city: string;
  postalCode: string;
  countryCode: string;
  updatedAt: string;
  sourceVersion: number;
  totalEvses: number;
  evses: EvseView[];
  tariff: TariffView | null;
}

/** RFC 9457 Problem Details (read-only view; mirrors contracts/schemas/common/problem-details.json). */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}

/** URL query contract for PUB-02 (ARC-023 §9.2). */
export interface StationSearchQuery {
  /** Map bounds west/south/east/north (decimal degrees, ≤5 decimals). */
  bounds?: { west: number; south: number; east: number; north: number };
  /** Connector depth filter (e.g. 'CCS', 'TYPE2'). */
  connector?: string;
  /** Minimum power in kW (URL unit) — sent as minPowerW = kW × 1000. */
  minimumPowerKw?: number;
  /** Presentation mode; never sent to the API. */
  view?: 'list' | 'map';
}
