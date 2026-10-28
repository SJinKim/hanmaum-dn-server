# Design-QA-Checkliste

Implementierung gegen Figma-Frame vergleichen (Screenshot der laufenden App neben Figma-Screenshot).

- [ ] Layout und Abstände entsprechen dem Raster (Spacing-Tokens, keine freien Pixelwerte)
- [ ] Farben, Typografie, Radius aus Design-Tokens, Light und Dark Mode
- [ ] Richtige Komponenten (PrimeNG / Material 3), keine Nachbauten
- [ ] Alle Zustände: Laden, leer, Fehler, Erfolg, deaktiviert, Hover/Fokus/Pressed
- [ ] Responsiv: Desktop, Tablet, Mobile (Web) · kleine und große Geräte (App)
- [ ] Texte vollständig, lange Texte/Umbrüche, Übersetzungen
- [ ] Barrierefreiheit: Kontrast (WCAG AA), Fokusreihenfolge, Tastaturbedienung, Screenreader-Labels, Touch-Ziele ≥ 44 pt / 48 dp
- [ ] Plattform-Konventionen (Zurück-Navigation, Safe Areas, Systemschrift)
- [ ] Abweichungen vom Design sind im PR begründet
