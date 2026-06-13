# Carnage3D – Verbesserungsvorschläge

Eine strukturierte Übersicht über identifizierte Schwachstellen und konkrete Maßnahmen zur Verbesserung des Projekts.

---

## Sofort umgesetzte Verbesserungen (dieser Branch)

### 1. GitHub Actions Workflow modernisiert
**Datei:** `.github/workflows/build-cmake.yml`

**Problem:** Der Workflow verwendete `actions/checkout@v2` (veraltet seit 2023) und die nicht mehr gewartete Third-Party-Action `ashutoshvarma/action-cmake-build@master`. Der `@master`-Pin ist ein Sicherheitsrisiko.

**Lösung:**
- `actions/checkout@v2` → `actions/checkout@v4`
- `ashutoshvarma/action-cmake-build@master` ersetzt durch direkte `cmake`/`cmake --build` Aufrufe
- macOS: `glfw3` → `glfw` (korrekter Homebrew-Paketname)
- Explizite Compiler-Flags über `CC`/`CXX` env-Variablen

### 2. `.clang-format` hinzugefügt
**Datei:** `.clang-format`

**Problem:** Kein Code-Formatter konfiguriert. Verschiedene Entwickler nutzen unterschiedliche Stile, was zu inkonsistentem Code und diff-Rauschen in PRs führt.

**Lösung:** Clang-Format-Konfiguration basierend auf dem bestehenden Projektstil:
- 4 Spaces Einrückung, kein Tab
- Zeilenlänge 120 Zeichen
- Google-Style als Basis, angepasst an Projektkonventionen

**Verwendung:**
```bash
# Einzelne Datei formatieren
clang-format -i src/CarnageGame.cpp

# Alle Quelldateien formatieren
find src -name "*.cpp" -o -name "*.h" | xargs clang-format -i

# Nur prüfen (kein Schreiben)
clang-format --dry-run --Werror src/CarnageGame.cpp
```

### 3. `.clang-tidy` hinzugefügt
**Datei:** `.clang-tidy`

**Problem:** Kein statisches Analysewerkzeug konfiguriert. Bugprone-Muster, veraltete C++-Idiome und Performance-Probleme werden nicht automatisch erkannt.

**Lösung:** Clang-Tidy-Konfiguration mit folgenden Checks:
- `bugprone-*`: Häufige C++-Fehlerquellen (Null-Dereferenz, Typ-Konversionen, etc.)
- `modernize-*`: Vorschläge für moderne C++17-Idiome (Smart Pointer, nullptr, override, etc.)
- `performance-*`: Ineffiziente Operationen (unnötige Kopien, suboptimale Algorithmen)
- `readability-*`: Lesbarkeitsverbesserungen (else-after-return, container-size-empty, etc.)
- Third-Party-Code (`third_party/`, `Box2D/`) ausgenommen

**Verwendung:**
```bash
# Einzelne Datei analysieren (Build-Verzeichnis muss existieren)
clang-tidy src/CarnageGame.cpp -- -std=c++17 -Isrc

# Mit compile_commands.json (empfohlen)
cmake -B build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
clang-tidy -p build src/CarnageGame.cpp
```

---

## Mittelfristige Verbesserungen

### 4. `stdc++fs` Linking prüfen
**Datei:** `cmake/Carnage3D.cmake`, Zeile 36

**Problem:**
```cmake
if(NOT(APPLE))
    target_link_libraries(carnage3d stdc++fs)
endif()
```
`libstdc++fs` ist seit GCC 9 in `libstdc++` integriert. Auf modernen Linux-Systemen (Ubuntu 20.04+, GCC 9+) verursacht das explizite Linken einen Link-Fehler oder ist unnötig. Dies könnte Ursache der gemeldeten Ubuntu-Kompilierungsprobleme sein (Issues #41, #45).

**Lösung:**
```cmake
# In CMakeLists.txt: Mindestversion prüfen und bedingt linken
if(NOT APPLE)
    include(CheckCXXSourceCompiles)
    check_cxx_source_compiles(
        "#include <filesystem>\nint main(){std::filesystem::path p; return 0;}"
        HAS_FILESYSTEM_WITHOUT_LIB
    )
    if(NOT HAS_FILESYSTEM_WITHOUT_LIB)
        target_link_libraries(carnage3d stdc++fs)
    endif()
endif()
```

### 5. Speicherverwaltung – Smart Pointer Migration
**Betroffene Dateien:** `src/Vehicle.h`, `src/Pedestrian.h`, `src/PhysicsBody.h`, `src/GameObjectsManager.h`

**Problem:** Das Projekt verwendet ausschließlich rohe Zeiger für alle Objekte. Besitzverhältnisse sind undokumentiert:
```cpp
// In Vehicle.h – wer besitzt mCarInfo?
StyleData* mCarInfo = nullptr;
// In Pedestrian.h – wer besitzt mController?
CharacterController* mController = nullptr;
```

**Empfehlung (schrittweise Migration):**
1. Besitzende Zeiger → `std::unique_ptr<T>`
2. Nicht-besitzende Referenzen → rohe Zeiger *mit Kommentar* oder `T*` (non-owning)
3. Optionale Referenzen → `std::optional<std::reference_wrapper<T>>`

**Beispiel:**
```cpp
// Vorher
CharacterController* mController = nullptr;

// Nachher (besitzend)
std::unique_ptr<CharacterController> mController;

// Nachher (nicht-besitzend, explizit dokumentiert)
CharacterController* mController = nullptr; // non-owning, managed by AiManager
```

### 6. Fehlerbehandlung vereinheitlichen
**Betroffene Dateien:** Alle `Initialize()`/`Deinit()`-Methoden

**Problem:** Inkonsistente Fehlerbehandlung – Mix aus:
- `bool Initialize()` (ignorierbar)
- `debug_assert()` (nur in Debug-Builds)
- Null-Pointer-Rückgaben ohne Fehlerbeschreibung

**Empfehlung:**
```cpp
// Option A: std::optional für fallible Getter
std::optional<DistrictInfo> GetDistrictAtPosition2(glm::vec2 position) const;

// Option B: enum class für Fehlercodes bei Init
enum class InitResult { Success, MissingGameData, GraphicsInitFailed };
InitResult Initialize();
```

### 7. Statische Analyse in CI integrieren
**Datei:** `.github/workflows/build-cmake.yml`

Einen separaten CI-Job hinzufügen:
```yaml
lint:
  name: "clang-tidy"
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
      with:
        submodules: true
    - name: Install dependencies
      run: sudo apt install --yes clang clang-tidy cmake libglew-dev libglfw3-dev libglm-dev libopenal-dev xorg-dev
    - name: Configure (with compile_commands.json)
      run: cmake -B build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON -DWITH_BOX2D=Yes
    - name: Run clang-tidy
      run: |
        find src -name "*.cpp" ! -path "*/Box2D/*" | \
        xargs clang-tidy -p build --warnings-as-errors='*'
```

---

## Langfristige Verbesserungen

### 8. Unit-Test-Framework einführen
**Problem:** Kein einziger automatisierter Test. Refaktorierungen sind blind.

**Empfehlung:** [Catch2](https://github.com/catchorg/Catch2) via CMake FetchContent:
```cmake
include(FetchContent)
FetchContent_Declare(
    Catch2
    GIT_REPOSITORY https://github.com/catchorg/Catch2.git
    GIT_TAG v3.4.0
)
FetchContent_MakeAvailable(Catch2)

enable_testing()
add_subdirectory(tests)
```

Erste Testkandidaten:
- `PhysicsBody` – Kollisionsberechnungen
- `GameObjectsManager` – Object-Pool-Lifecycle
- AI-Zustandsmaschine in `PedestrianStatesManager`

### 9. macOS-Support vervollständigen
**Problem:** Issue #52 – macOS-Support ist im Workflow konfiguriert (Build läuft), aber Audio (OpenAL) und einige Render-Pfade sind ungetestet.

**Aktion:** OpenAL-Soft via Homebrew testen, `GL_SILENCE_DEPRECATION` ist bereits gesetzt.

### 10. Verkehr- und Fahrradsystem (offene Issues)
- **Issue: RC-Autos** – Subklasse von `Vehicle` mit ferngesteuertem Controller
- **Issue: Stadtverkehr** – AI-Fahrzeuge als Hintergrundprozess mit einfachem Wegfindungsalgorithmus
- **Issue: Fahrrad-Lenkung** – 90°-Wendeproblem in `PhysicsManager` beim Fahrradtyp

### 11. Logging-System
**Problem:** Fehler-/Debug-Ausgaben gehen verloren oder sind nur mit `debug_assert` sichtbar.

**Empfehlung:** [spdlog](https://github.com/gabime/spdlog) – header-only, sehr schnell:
```cpp
#include <spdlog/spdlog.h>
spdlog::info("Loading map: {}", mapName);
spdlog::error("Failed to initialize audio device");
```

### 12. Dependency-Pinning via vcpkg oder Conan
**Problem:** Abhängigkeiten werden ohne Versionspins installiert (`apt install libglew-dev`). Builds können bei Paket-Updates brechen.

**Empfehlung:** `vcpkg.json` manifest erstellen:
```json
{
  "name": "carnage3d",
  "version": "0.1",
  "dependencies": [
    { "name": "glew", "version>=": "2.2.0" },
    { "name": "glfw3", "version>=": "3.3.8" },
    { "name": "glm", "version>=": "0.9.9.8" },
    { "name": "openal-soft", "version>=": "1.23.0" }
  ]
}
```

---

## Zusammenfassung

| Priorität | Verbesserung | Aufwand | Risiko |
|---|---|---|---|
| Sofort | GitHub Actions modernisieren | Gering | Keins |
| Sofort | `.clang-format` einführen | Gering | Keins |
| Sofort | `.clang-tidy` einführen | Gering | Keins |
| Mittel | `stdc++fs`-Link-Problem beheben | Gering | Niedrig |
| Mittel | Smart Pointer Migration (schrittweise) | Hoch | Mittel |
| Mittel | Fehlerbehandlung vereinheitlichen | Mittel | Niedrig |
| Mittel | clang-tidy in CI | Gering | Keins |
| Lang | Unit-Tests (Catch2) | Sehr hoch | Niedrig |
| Lang | macOS vollständig testen | Mittel | Niedrig |
| Lang | Logging-System (spdlog) | Mittel | Niedrig |
| Lang | vcpkg/Conan Dependency-Pinning | Mittel | Niedrig |
