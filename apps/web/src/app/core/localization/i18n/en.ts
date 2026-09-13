/**
 * English UI dictionary. Keys must match the Greek dictionary exactly —
 * the key-parity test proves completeness. Every rendered UI string must
 * come from a dictionary.
 */
export const EN: Record<string, string> = {
  app_name: 'EV Charging',
  nav_find_charging: 'Find charging',
  nav_language: 'Language',
  skip_to_content: 'Skip to content',

  // PUB-01 entry
  entry_hero_title: 'Find charging stations for your electric vehicle',
  entry_hero_hint: 'Search with a list or a map and see availability.',

  // PUB-02 search
  stations_title: 'Charging stations',
  stations_subtitle: 'Filter and find stations near you.',
  filter_connector: 'Connector type',
  filter_min_power: 'Minimum power',
  connector_any: 'All connectors',
  connector_ccs: 'CCS',
  connector_type2: 'Type 2',
  min_power_any: 'No minimum power',
  min_power_22: '22 kW',
  min_power_50: '50 kW',
  min_power_100: '100 kW',
  view_list: 'List',
  view_map: 'Map',
  search_this_area: 'Search this area',
  results_title: 'Results',
  loading: 'Loading…',
  empty_title: 'No stations found',
  empty_hint: 'Try widening the area or removing filters.',
  error_title: 'Search failed',
  error_retry: 'Retry',
  map_fallback_title: 'Map is not available',
  map_fallback_hint: 'The results list remains available.',
  invalid_params_notice: 'Invalid search parameters were removed.',

  // PUB-03 station details
  details_title: 'Station details',
  details_not_found: 'Station not found',
  details_evses: 'EVSEs',
  details_connectors: 'Connectors',
  details_tariff: 'Tariff',
  details_total_evses: 'Total EVSEs',
  details_freshness: 'Data updated',
  connector_power_kw: 'power',
  tariff_component_energy_per_kwh: 'Energy per kWh',
  tariff_component_occupancy_per_minute: 'Occupancy per minute',

  // Generic
  not_found_title: 'Page not found',
  not_found_hint: 'The link may be wrong or the page was removed.',
  back_to_search: 'Back to search',

  // Page titles per route
  page_title_entry: 'Home',
  page_title_stations: 'Charging stations',
  page_title_station_details: 'Station details',
  page_title_not_found: 'Not found',
};
