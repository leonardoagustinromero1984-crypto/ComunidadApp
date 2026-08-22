# UI-01 — Screen definition of done (permanent)

Any **new or modified** production Android screen is not done until:

- [ ] Canonical background (`LeoVerTheme.colors.background` / `#FAFBF8`)
- [ ] Canonical surface/components (`LeoVerScaffold`, Leo cards/buttons/fields — no one-off palette)
- [ ] Typography roles respected (screen / section / card / body / secondary / caption / button / chip)
- [ ] Spacing system respected (XS 4 / S 8 / M 12 / L 16 / XL 24 / XXL 32)
- [ ] Loading state
- [ ] Empty state where applicable
- [ ] Friendly error state (`CanonicalUiErrorMapper` / `CanonicalUiErrorKind`)
- [ ] No raw backend errors (SQL, PostgREST, constraints, stack traces, technical enums)
- [ ] Correct back navigation
- [ ] Correct bottom/context navigation (`ContextNavigation` — same LeoVer chrome)
- [ ] Scroll works on small displays
- [ ] Form keyboard / IME behavior works
- [ ] No active runtime mock fallback
- [ ] No AccountType / AppMode authority
- [ ] No technical implementation labels exposed
- [ ] Uses canonical geographic terminology (provincia / localidad)
- [ ] Visual consistency with LeoVer (warm, clean, rounded, not enterprise-default)
- [ ] Relevant focused tests pass

## Development rule

**NEW SCREEN:** must use canonical LeoVer theme/components from creation.

**MODIFIED SCREEN:** must not regress an already canonical screen back to legacy styling.

**If touching a legacy screen substantially:** bring the touched portion into compliance.

## Community

Do not replace the canonical Community layout (category-first chips, filters after selection, Cerca mío, provider cards, Ver perfil, personal bottom nav).

Home is social-first (stories then feed). Do not restore Add pet / Lost / Found shortcuts on Inicio.

Settings must use `LeoVerSettingsSection` / `LeoVerSettingsRow`. Active context switching lives in Configuración → Usar LeoVer como.

## Tutorials

Use `LeoVerTutorialPager` + catalog data. Do not invent a new tutorial layout.

## Local gate (no emulator)

```
powershell -File scripts/ui-regression-gate.ps1
```

Full audit: [UI-01-canonical-design-system-and-screen-audit.md](./UI-01-canonical-design-system-and-screen-audit.md)
