# I1-CON-003 — Registry alignment evidence

- **Task ID:** I1-CON-003 (L2; packet approved via owner merge of the planning PR)
- **Baseline commit:** 8475f5cd (origin/main after PR #20)
- **Date:** 2026-09-11
- **Result:** PASS — registry 38 → 49 messages; G3 exit 0

## ARC-020 §6 (lines 160–172) → registry cross-check

| ARC-020 event (W1) | Registry name | versionedType | routingKey | schema |
|---|---|---|---|---|
| StationPublished | StationPublished | com.evplatform.station.published.v1 | station.published | events/station-published-event.json |
| StationUpdated | StationUpdated | com.evplatform.station.updated.v1 | station.updated | events/station-updated-event.json |
| StationStatusChanged | StationStatusChanged | com.evplatform.station.status-changed.v1 | station.status-changed | events/station-status-changed-event.json |
| EVSEConfigurationChanged | EVSEConfigurationChanged | com.evplatform.station.evse-configuration-changed.v1 | station.evse-configuration-changed | events/evse-configuration-changed-event.json |
| EVSEAdministrativeStateChanged | EVSEAdministrativeStateChanged | com.evplatform.station.evse-administrative-state-changed.v1 | station.evse-administrative-state-changed | events/evse-administrative-state-changed-event.json |
| ConnectorConfigurationChanged | ConnectorConfigurationChanged | com.evplatform.station.connector-configuration-changed.v1 | station.connector-configuration-changed | events/connector-configuration-changed-event.json |
| TariffPublished | TariffPublished | com.evplatform.station.tariff-published.v1 | station.tariff-published | events/tariff-published-event.json |
| TariffRetired | TariffRetired | com.evplatform.station.tariff-retired.v1 | station.tariff-retired | events/tariff-retired-event.json |
| BookingPolicyChanged | BookingPolicyChanged | com.evplatform.station.booking-policy-changed.v1 | station.booking-policy-changed | events/booking-policy-changed-event.json |
| OperatorOrganizationCreated | OperatorOrganizationCreated | com.evplatform.organization.created.v1 | organization.created | events/operator-organization-created-event.json |
| OperatorOrganizationStatusChanged | OperatorOrganizationStatusChanged | com.evplatform.organization.status-changed.v1 | organization.status-changed | events/operator-organization-status-changed-event.json |

All: producer station-operations-service, consumers [discovery-insights-service]
(ARC-020 §9 line 319), exchange ev.domain.v1, releaseWave W1, classification
BUSINESS. No existing registry entry was modified.

## Validation results

- contracts:registries green (49 messages; policies 19; traceability 42, 0 open)
- contracts:schemas: 53 compile against 2020-12 (+11)
- contracts:asyncapi green (1 info-level note)
- Example payloads (SeedDataset fixture values): 11/11 valid via ajv 2020-12
- npm run contracts:verify exit 0; secretlint 0 findings

## Known pre-existing issue (flagged, not introduced)

The AsyncAPI document's existing channels use short exchange names
(booking/session/station/device) while the registry anchors declare
ev.domain.v1 / ev.device.*.v1 / com.platform command exchanges. New channels
follow the registry exchange semantics with the document's channel-key style.
The drift predates this task and is flagged for a future asyncapi alignment.

No secrets or personal data in this evidence.
