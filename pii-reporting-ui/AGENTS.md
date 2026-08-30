<!-- bmad:context -->
<!-- Verified 2026-08-26 against 6e22133b (working tree had uncommitted changes). Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## pii-reporting-ui

Angular 21 dashboard: scan monitoring, PII settings, obfuscation review. PrimeNG plus PrimeFlex for
the UI, Transloco for i18n. Routes in `src/app/app.routes.ts`.

## Running and verifying

- Unit tests run on Vitest, not Karma or Jasmine: `pnpm test`, `pnpm test:coverage`. End-to-end runs
  on Playwright through `pnpm e2e`.

## Known pitfalls

- On `p-multiSelect` and `p-select`, pre-translate the label into the view model and bind
  `optionLabel` and `optionGroupLabel` to that string, as `space-filters.component.html` does. An
  item template does not feed the selected chips, the internal filter or the group header: without
  `optionLabel` the chips render `[object Object]`, the search matches nothing, and a group header
  bound to an i18n key prints the key.

<!-- /bmad:context -->
