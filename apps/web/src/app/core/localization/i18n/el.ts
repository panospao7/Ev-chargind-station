/**
 * Greek UI dictionary (default locale). Keys are snake_case and shared
 * with the English dictionary — the key-parity test proves completeness.
 * Every rendered UI string must come from a dictionary.
 */
export const EL: Record<string, string> = {
  app_name: 'Φόρτιση EV',
  nav_find_charging: 'Αναζήτηση φόρτισης',
  nav_language: 'Γλώσσα',
  skip_to_content: 'Μετάβαση στο περιεχόμενο',

  // PUB-01 entry
  entry_hero_title: 'Βρείτε σταθμούς φόρτισης για το ηλεκτρικό σας όχημα',
  entry_hero_hint: 'Αναζητήστε με λίστα ή χάρτη και δείτε διαθεσιμότητα.',

  // PUB-02 search
  stations_title: 'Σταθμοί φόρτισης',
  stations_subtitle: 'Φιλτράρετε και βρείτε σταθμούς κοντά σας.',
  filter_connector: 'Τύπος υποδοχής',
  filter_min_power: 'Ελάχιστη ισχύς',
  connector_any: 'Όλες οι υποδοχές',
  connector_ccs: 'CCS',
  connector_type2: 'Τύπος 2',
  min_power_any: 'Καμία ελάχιστη ισχύς',
  min_power_22: '22 kW',
  min_power_50: '50 kW',
  min_power_100: '100 kW',
  view_list: 'Λίστα',
  view_map: 'Χάρτης',
  search_this_area: 'Αναζήτηση σε αυτή την περιοχή',
  results_title: 'Αποτελέσματα',
  loading: 'Φόρτωση…',
  empty_title: 'Δεν βρέθηκαν σταθμοί',
  empty_hint: 'Δοκιμάστε να μεγεθύνετε την περιοχή ή να αφαιρέσετε φίλτρα.',
  error_title: 'Η αναζήτηση απέτυχε',
  error_retry: 'Δοκιμή ξανά',
  map_fallback_title: 'Ο χάρτης δεν είναι διαθέσιμος',
  map_fallback_hint: 'Η λίστα αποτελεσμάτων παραμένει διαθέσιμη.',
  invalid_params_notice: 'Μη έγκυρες παράμετροι αναζήτησης αφαιρέθηκαν.',

  // PUB-03 station details
  details_title: 'Λεπτομέρειες σταθμού',
  details_not_found: 'Ο σταθμός δεν βρέθηκε',
  details_evses: 'Παροχές (EVSE)',
  details_connectors: 'Υποδοχές',
  details_tariff: 'Τιμολόγηση',
  details_total_evses: 'Σύνολο παροχών',
  details_freshness: 'Ενημέρωση δεδομένων',
  connector_power_kw: 'ισχύς',
  tariff_component_energy_per_kwh: 'Ενέργεια ανά kWh',
  tariff_component_occupancy_per_minute: 'Κατοχή ανά λεπτό',

  // Generic
  not_found_title: 'Η σελίδα δεν βρέθηκε',
  not_found_hint: 'Ο σύνδεσμος μπορεί να είναι λανθασμένος ή η σελίδα να αφαιρέθηκε.',
  back_to_search: 'Πίσω στην αναζήτηση',

  // Page titles per route
  page_title_entry: 'Αρχική',
  page_title_stations: 'Σταθμοί φόρτισης',
  page_title_station_details: 'Λεπτομέρειες σταθμού',
  page_title_not_found: 'Δεν βρέθηκε',
};
