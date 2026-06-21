# Wzory matematyczne i fizyczne — Solar System Simulator 3D

Ten dokument opisuje **wszystkie wzory matematyczne i formuły fizyczne** użyte w symulatorze Układu Słonecznego. Każdy wzor jest podany wraz z:

- **lokalizacją w kodzie** (plik + metoda),
- **znaczeniem fizycznym**,
- **przykładem użycia** w symulacji.

Symulator wykorzystuje klasyczną mechanikę Newtona z całkowaniem Eulera oraz elementy orbitalne Keplera do wyznaczania orbit i stanu ciał niebieskich.

---

## Spis treści

1. [Stałe fizyczne](#1-stałe-fizyczne)
2. [Grawitacja Newtona](#2-grawitacja-newtona)
3. [Całkowanie numeryczne (Euler)](#3-całkowanie-numeryczne-euler)
4. [Elementy orbitalne Keplera](#4-elementy-orbitalne-keplera)
5. [Równanie Keplera (Newton-Raphson)](#5-równanie-keplera-newton-raphson)
6. [Równanie vis-viva](#6-równanie-vis-viva)
7. [Rotacja 3-1-3 Eulera](#7-rotacja-3-1-3-eulera)
8. [Wyznaczanie elementów orbitalnych ze stanu (inżynieria odwrotna)](#8-wyznaczanie-elementów-orbitalnych-ze-stanu)
9. [Rotacja osiowa (obrót wokół własnej osi)](#9-rotacja-osiowa)
10. [Skala czasu symulacji](#10-skala-czasu-symulacji)
11. [Transformacja układów współrzędnych (J2000 → symulator)](#11-transformacja-układów-współrzędnych)
12. [Unikanie osobliwości (MIN_DISTANCE)](#12-unikanie-osobliwości)

---

## 1. Stałe fizyczne

**Plik:** `src/main/java/com/physics3d/engine/PhysicsEngine.java`

| Stała                        | Wartość              | Jednostka | Znaczenie                                     |
| ---------------------------- | -------------------- | --------- | --------------------------------------------- |
| `GRAVITATIONAL_CONSTANT`     | `6.674e-11`          | N·m²/kg²  | Stała grawitacji Newtona (G)                  |
| `MIN_DISTANCE`               | `1e6` (1 000 000)    | m         | Minimalna odległość, by uniknąć osobliwości   |
| `DEFAULT_TIME_SCALE`         | `60`                 | –         | Domyślne przyspieszenie czasu (60×)           |
| `MIN_TIME_SCALE`             | `0`                  | –         | Pauza (czas nie płynie)                       |
| `MAX_TIME_SCALE`             | `31_536_000.0`       | –         | Maks. 1 rok symulowany na sekundę rzeczywistą |
| `SECONDS_PER_YEAR`           | `365.25 × 24 × 3600` | s         | Liczba sekund w roku zwrotnikowym             |
| `INITIAL_UNIVERSE_AGE_YEARS` | `13.823473323e9`     | lat       | Początkowy wiek Wszechświata (~13.82 mld lat) |

**Stałe konwersji (w `Main.java`):**

| Stała                   | Wartość                   | Znaczenie                           |
| ----------------------- | ------------------------- | ----------------------------------- |
| `AU_TO_METERS`          | `149_597_870_700.0`       | 1 jednostka astronomiczna w metrach |
| `AU_PER_DAY_TO_M_PER_S` | `AU_TO_METERS / 86_400.0` | Przelicznik AU/dzień → m/s          |

---

## 2. Grawitacja Newtona

**Metoda:** `PhysicsEngine.calculateGravitationalForces()`

### 2.1 Prawo powszechnego ciążenia Newtona

Siła grawitacji między dwoma ciałami o masach `m₁` i `m₂` oddalonymi o `r`:

```
F = G · (m₁ · m₂) / r²
```

gdzie:

- `G = 6.674 × 10⁻¹¹` N·m²/kg² (stała grawitacyjna),
- `r` — odległość między środkami mas ciał.

### 2.2 Wektor siły (składowe)

Dla wektora przemieszczenia `d⃗ = (dx, dy, dz)` od ciała 1 do ciała 2:

```
r = sqrt(dx² + dy² + dz²)
F_x = (dx / r) · |F|
F_y = (dy / r) · |F|
F_z = (dz / r) · |F|
```

### 2.3 Trzecia zasada dynamiki Newtona

Siły działające na parę ciał są **równe co do wartości i przeciwnie skierowane**:

```
F₁₂ = -F₂₁
```

W kodzie oznacza to, że jeśli ciało 1 doświadcza siły `+F⃗`, to ciało 2 doświadcza `-F⃗`. Implementacja dodaje siłę do `forces[i]` i odejmuje ją od `forces[j]` w tej samej iteracji pętli.

### 2.4 Filtr "tylko Słońce"

W symulatorze grawitacja działa **tylko między Słońcem a planetami** (nie między planetami). Filtr:

```java
if (!name1.equals("Sun") && !name2.equals("Sun")) continue;
```

To uproszczenie — w rzeczywistości planety też oddziałują na siebie, ale efekt jest pomijalny w porównaniu z przyciąganiem Słońca.

---

## 3. Całkowanie numeryczne (Euler)

**Metoda:** `PhysicsEngine.update(double deltaTime)`

Symulator używa **metody Eulera** (jawnej, pierwszego rzędu) do całkowania równań ruchu. To najprostsza metoda, ale wystarczająco dokładna dla wizualizacji.

### 3.1 Przyspieszenie z siły (II zasada Newtona)

```
a⃗ = F⃗ / m
```

Dla każdej osi:

```
a_x = F_x / m
a_y = F_y / m
a_z = F_z / m
```

### 3.2 Aktualizacja prędkości

```
v⃗(t + Δt) = v⃗(t) + a⃗ · Δt
```

Implementacja:

```java
vx = v.x + ax * dt;
vy = v.y + ay * dt;
vz = v.z + az * dt;
```

### 3.3 Aktualizacja pozycji

```
p⃗(t + Δt) = p⃗(t) + v⃗ · Δt
```

Implementacja:

```java
px = p.x + vx * dt;
py = p.y + vy * dt;
pz = p.z + vz * dt;
```

### 3.4 Skalowanie czasu

`Δt` w powyższych wzorach to **czas symulowany**, nie rzeczywisty:

```java
double dt = deltaTime * timeScale;
```

gdzie `deltaTime` to czas między klatkami (zwykle ~10 ms), a `timeScale` to mnożnik (domyślnie 60).

### 3.5 Ograniczenie kroku czasu

W `Main.java` `deltaTime` jest obcinane do max. 0.05 s, by uniknąć eksplozji integratora przy dużym lagu:

```java
if (deltaTime > 0.05) deltaTime = 0.05;
```

---

## 4. Elementy orbitalne Keplera

**Klasa:** `CelestialBody` (pola: `semiMajorAxis`, `eccentricity`, `inclination`, `ascendingNode`, `argOfPericenter`, `meanAnomaly`)

Sześć elementów Keplera jednoznacznie opisuje orbitę keplerowską (w przybliżeniu dwóch ciał):

| Symbol | Nazwa                | Znaczenie                                                          |
| ------ | -------------------- | ------------------------------------------------------------------ |
| `a`    | Półoś wielka         | Średnia odległość od centrum (m)                                   |
| `e`    | Mimośród             | Kształt orbity (0 = koło, 0–1 = elipsa)                            |
| `i`    | Inklinacja           | Nachylenie płaszczyzny orbity do płaszczyzny odniesienia (radiany) |
| `Ω`    | Węzeł wstępujący     | Gdzie orbita przecina płaszczyznę odniesienia "w górę" (radiany)   |
| `ω`    | Argument perycentrum | Orientacja elipsy w jej płaszczyźnie (radiany)                     |
| `M`    | Anomalia średnia     | Pozycja na orbicie w epoce (radiany)                               |

---

## 5. Równanie Keplera (Newton-Raphson)

**Metoda:** `PhysicsEngine.computeOrbitalState()`

### 5.1 Równanie Keplera

Anomalia średnia `M` i anomalia mimośrodowa `E` są powiązane:

```
M = E − e · sin(E)
```

To równanie **przestępne** (nie da się rozwiązać analitycznie dla `E`), więc używamy **metody Newtona-Raphsona**.

### 5.2 Iteracja Newtona-Raphsona

```
E_{k+1} = E_k − (E_k − e·sin(E_k) − M) / (1 − e·cos(E_k))
```

Implementacja (5 iteracji, więcej niż wystarczające dla `e ≤ 0.5`):

```java
double E = meanAnom;
for (int k = 0; k < 5; k++) {
    double dE = (E - e * Math.sin(E) - meanAnom) / (1.0 - e * Math.cos(E));
    E -= dE;
}
```

### 5.3 Anomalia prawdziwa z mimośrodowej

```
θ = 2 · atan2( sqrt(1 + e) · sin(E/2),  sqrt(1 − e) · cos(E/2) )
```

Implementacja:

```java
theta = 2.0 * Math.atan2(
    Math.sqrt(1 + e) * Math.sin(E / 2.0),
    Math.sqrt(1 - e) * Math.cos(E / 2.0)
);
```

### 5.4 Promień w funkcji anomalii prawdziwej

```
r = a · (1 − e²) / (1 + e · cos(θ))
```

Implementacja:

```java
double r = (a * (1 - e * e)) / (1 + e * Math.cos(theta));
```

---

## 6. Równanie vis-viva

**Metoda:** `PhysicsEngine.computeOrbitalState()`

### 6.1 Prędkość orbitalna (vis-viva)

Równanie vis-viva wiąże prędkość orbitalną `v` z promieniem `r` i półoś wielką `a`:

```
v² = G · M · (2/r − 1/a)
v = sqrt( G · M · (2/r − 1/a) )
```

gdzie `M` to masa ciała centralnego (Słońca).

### 6.2 Moment pędu właściwy (specific angular momentum)

Dla orbity keplerowskiej moment pędu jest zachowany:

```
h = sqrt( G · M · a · (1 − e²) )
```

### 6.3 Składowe prędkości w płaszczyźnie orbity

W płaszczyźnie orbity (XZ w symulatorze) prędkość ma składowe radialną `v_r` i tangencjalną `v_θ`:

```
v_r = (G·M / h) · e · sin(θ)
v_θ = (G·M / h) · (1 + e · cos(θ))
```

W formie kartezjańskiej (z `θ` mierzonym od perycentrum):

```
v_x = v_r · cos(θ) − v_θ · sin(θ) = −(G·M / h) · sin(θ)
v_z = v_r · sin(θ) + v_θ · cos(θ) =  (G·M / h) · (e + cos(θ))
```

Implementacja:

```java
double h = Math.sqrt(GRAVITATIONAL_CONSTANT * sunMass * a * (1.0 - e * e));
double vFactor = (GRAVITATIONAL_CONSTANT * sunMass) / h;
double vx_orb = -vFactor * Math.sin(theta);
double vz_orb =  vFactor * (e + Math.cos(theta));
```

**Uwaga:** Poprzednia implementacja mnożyła vis-viva przez niejednostkowy wektor `(−sin θ, e + cos θ)`, co przeszacowywało prędkość (np. o ~9% w apocentrum Marsa) i powodowało "ucieczkę" orbity na zewnątrz. Obecna wersja używa poprawnej formuły kartezjańskiej.

---

## 7. Rotacja 3-1-3 Eulera

**Metoda:** `PhysicsEngine.generateKeplerianOrbit()` i `computeOrbitalState()`

Aby przejść z płaszczyzny orbity do globalnego układu współrzędnych, stosujemy **trzy obroty Eulera** wokół osi Z, X, Z:

```
R = Rz(ω) · Rx(i) · Rz(Ω)
```

Kolejność:

1. `Rz(Ω)` — obrót do węzła wstępującego (wokół osi Z),
2. `Rx(i)` — pochylenie płaszczyzny orbity (wokół nowej osi X),
3. `Rz(ω)` — obrót do perycentrum w pochylonej płaszczyźnie (wokół nowej osi Z).

Implementacja (JOML):

```java
Matrix3f orbitalRotation = new Matrix3f()
    .rotationZ((float) argPeri)   // ω
    .rotateX((float) i)           // i
    .rotateZ((float) ascNode);    // Ω
```

Następnie transformujemy pozycję i prędkość:

```java
orbitalRotation.transform(pos);
orbitalRotation.transform(vel);
```

---

## 8. Wyznaczanie elementów orbitalnych ze stanu

**Metoda:** `PhysicsEngine.deriveOrbitalElementsFromState()`

To **inżynieria odwrotna** — mając wektory `r⃗` i `v⃗`, odtwarzamy sześć elementów Keplera. Używane po pobraniu danych z NASA JPL Horizons.

### 8.1 Półoś wielka (z vis-viva)

```
a = 1 / (2/r − v²/μ)
```

gdzie `μ = G · M` (parametr grawitacyjny Słońca).

### 8.2 Wektor momentu pędu właściwego

```
h⃗ = r⃗ × v⃗
```

Składowe:

```
h_x = r_y · v_z − r_z · v_y
h_y = r_z · v_x − r_x · v_z
h_z = r_x · v_y − r_y · v_x
```

### 8.3 Wektor mimośrodu

```
e⃗ = (v⃗ × h⃗) / μ − r̂
```

gdzie `r̂ = r⃗ / |r⃗|`. Wektor mimośrodu wskazuje na perycentrum.

### 8.4 Inklinacja

```
i = acos( h_y / |h⃗| )
```

W naszym układzie Y to "góra", więc `h_y` mówi, jak bardzo płaszczyzna orbity jest nachylona do XZ.

### 8.5 Węzeł wstępujący

Kierunek węzła wstępującego w płaszczyźnie odniesienia:

```
n⃗ = (−h_z, 0, h_x)   // ŷ × h⃗
```

Kąt węzła:

```
Ω = atan2( n_x, n_z )
```

### 8.6 Argument perycentrum

```
cos(ω) = (n⃗ · e⃗) / (|n⃗| · |e⃗|)
sin(ω) = (n⃗ × e⃗) · ĥ / (|n⃗| · |e⃗|)
ω = atan2( sin(ω), cos(ω) )
```

### 8.7 Anomalia prawdziwa

```
cos(θ) = (e⃗ · r⃗) / (|e⃗| · |r⃗|)
sin(θ) = (e⃗ × r⃗) · ĥ / (|e⃗| · |r⃗|)
θ = atan2( sin(θ), cos(θ) )
```

Znak `sin(θ)` jest korygowany na podstawie prędkości radialnej `r⃗ · v⃗` (jeśli `> 0`, ciało oddala się od perycentrum).

### 8.8 Anomalia mimośrodowa z prawdziwej

```
tan(E/2) = sqrt( (1 − e) / (1 + e) ) · tan(θ/2)
E = 2 · atan2( sinHalfE, cosHalfE )
```

gdzie:

```
sinHalfE = sqrt((1 − e)/(1 + e)) · sin(θ/2)
cosHalfE = cos(θ/2)
```

### 8.9 Anomalia średnia

```
M = E − e · sin(E)
```

Wartość jest normalizowana do `[0, 2π)`.

---

## 9. Rotacja osiowa

**Metoda:** `CelestialBody.advanceRotation(double deltaTime)`

Każde ciało obraca się wokół własnej osi z prędkością kątową:

```
ω = 2π / T
```

gdzie `T` to okres obrotu (w sekundach). Ujemny `T` oznacza **obrót wsteczny** (retrogradny) — Wenus i Uran.

Implementacja:

```java
double angularVelocity = 2.0 * Math.PI / rotationPeriod;
rotationAngle += angularVelocity * deltaTime;
```

### 9.1 Pochylenie osi (obliquity)

Oś obrotu jest nachylona do prostopadłej płaszczyzny orbity o kąt `axialTilt`. Wartości dla planet (z NASA fact sheets):

| Planeta | Pochylenie osi | Okres obrotu | Uwagi         |
| ------- | -------------- | ------------ | ------------- |
| Merkury | 0.034°         | 58.6 dnia    | Prawie zerowe |
| Wenus   | 177.4°         | −243 dni     | Retrogradny   |
| Ziemia  | 23.4393°       | 23h 56m      | –             |
| Mars    | 25.19°         | 24h 37m      | –             |
| Jowisz  | 3.13°          | 9h 55m       | –             |
| Saturn  | 26.73°         | 10h 33m      | + pierścienie |
| Uran    | 97.77°         | −17h 14m     | "Leżący"      |
| Neptun  | 28.32°         | 16h 6m       | –             |

---

## 10. Skala czasu symulacji

**Metody:** `PhysicsEngine.getTimeScale()`, `setTimeScale()`, `multiplyTimeScale()`

Symulacja używa mnożnika czasu, by przyspieszyć bieg:

```
Δt_symulowany = Δt_rzeczywisty · timeScale
```

| Wartość `timeScale` | Efekt                                                 |
| ------------------- | ----------------------------------------------------- |
| `0`                 | Pauza                                                 |
| `1`                 | Czas rzeczywisty                                      |
| `60`                | 1 minuta symulowana na sekundę rzeczywistą (domyślne) |
| `31_536_000`        | 1 rok symulowany na sekundę rzeczywistą (maks.)       |

Wiek Wszechświata rośnie wraz z czasem symulowanym:

```java
universeAgeSeconds += dt;
```

Początkowy wiek: `13.823473323 × 10⁹` lat.

---

## 11. Transformacja układów współrzędnych

**Metoda:** `Main.applyLiveEphemeris()`

Dane z NASA JPL Horizons są w układzie **J2000 ekliptycznym**:

- Płaszczyzna ekliptyki: XY
- Oś "w górę": Z (północny biegun ekliptyki)
- **Układ prawoskrętny** (X × Y = Z)

Symulator używa:

- Płaszczyzna odniesienia: XZ
- Oś "w górę": Y
- **Układ lewoskrętny** (X × Z = −Y)

### 11.1 Transformacja

Aby zachować zgodność, zamieniamy osie Y i Z oraz negujemy Z (zmiana skrętności):

```
X_sym = X_horizons         // kierunek punktu Barana (niezmieniony)
Y_sym = Z_horizons         // biegun ekliptyki → "w górę"
Z_sym = −Y_horizons        // flip skrętności (zachowuje ruch progradny)
```

Implementacja:

```java
Vector3f posMeters = new Vector3f(
    (float)(live.position.x * AU_TO_METERS),    // X → X
    (float)(live.position.z * AU_TO_METERS),    // Z → Y (up)
    (float)(-live.position.y * AU_TO_METERS));  // Y → −Z (handedness)
```

To samo dotyczy prędkości.

### 11.2 Konwersja jednostek

Horizons zwraca pozycję w AU i prędkość w AU/dzień. Symulator pracuje w metrach i m/s:

```
1 AU = 149 597 870 700 m
1 AU/dzień = 149 597 870 700 / 86 400 m/s ≈ 1 731 456.83 m/s
```

---

## 12. Unikanie osobliwości

**Stała:** `PhysicsEngine.MIN_DISTANCE = 1e6` (1 000 km)

Gdy dwa ciała zbliżą się na odległość mniejszą niż `MIN_DISTANCE`, pomijamy obliczenie siły, by uniknąć **osobliwości** (`1/r² → ∞`):

```java
if (distance < MIN_DISTANCE) continue;
```

To zabezpieczenie numeryczne — w praktyce planety nigdy nie zbliżają się tak bardzo do Słońca, ale jest to ważne dla stabilności integratora.

---

## Podsumowanie

Symulator łączy:

1. **Klasyczną mechanikę Newtona** — grawitacja i II zasada dynamiki.
2. **Całkowanie Eulera** — proste, wystarczające dla wizualizacji.
3. **Elementy orbitalne Keplera** — sześć parametrów opisujących orbitę.
4. **Równanie Keplera** — rozwiązywane iteracyjnie Newtonem-Raphsonem.
5. **Równanie vis-viva** — prędkość orbitalna w dowolnym punkcie orbity.
6. **Rotację 3-1-3 Eulera** — transformacja z płaszczyzny orbity do globalnego układu.
7. **Inżynierię odwrotną** — odtwarzanie elementów orbitalnych z danych Horizons.
8. **Rotację osiową** — obrót każdego ciała wokół własnej osi (z uwzględnieniem retrogradności).
9. **Skalowanie czasu** — przyspieszenie symulacji do 1 roku/sekundę.
10. **Transformację układów współrzędnych** — J2000 ekliptyczny → symulator (XZ/Y-up, lewoskrętny).

Wszystkie te elementy razem tworzą wierną (choć uproszczoną) symulację Układu Słonecznego w czasie rzeczywistym.
