# Paint Mixer — Development Plan

Implementation plan for the app described in `IDEA.md`. Read `EVALUATION.md` first for the reasoning
behind the non-obvious decisions; this file is the buildable spec.

## The three rules that decide whether this app works

1. **Never mix or average colours in sRGB.** Linearise first, mix with Kubelka-Munk, convert back.
   Averaging RGB models mixing *light*, not *paint*, and produces mud for every mix.
2. **Never measure colour distance in RGB.** Convert to CIELAB and use Delta-E.
3. **Capture is controlled, not adaptive.** Manual exposure / white balance / tone curve, the exact
   settings persisted with the palette and replayed for every target shot, plus a white reference
   card in frame that is tapped and divided out. See section 4.

## Scope: single user, controlled conditions

This is a personal tool for one user, one phone, one room, fixed lighting and fixed camera position.
That is a deliberate constraint and the plan leans on it hard:

- **No** onboarding, guidance overlays, multi-user handling, or "uncalibrated" fallback paths. If
  capture preconditions are not met, refuse and say why — do not degrade gracefully.
- Capture settings are **locked to stored numeric values**, not merely locked-at-shutter.
- Paint tinting strengths are **empirically calibrated once** (Phase 5) and then trusted, because
  the paint set and the lighting do not change.

Do not add generality that the single-user scope does not require.

---

## 1. Stack

| Concern | Choice | Note |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose + Material 3 | |
| minSdk / targetSdk | 26 / current | |
| Camera | CameraX + `Camera2Interop` | needed for manual exposure/WB/tonemap; a camera Intent cannot |
| DB | Room (KSP) | |
| Images | Coil | |
| DI | Manual (a simple `AppContainer`) | app is too small to justify Hilt's build overhead |
| Async | Coroutines + Flow | |

**Modules — this split matters:**

```
:core-color     Pure Kotlin/JVM. No Android imports. All colour maths + the solver.
                Unit-tested on the JVM. This is where all the project risk lives.
:app            Compose UI, CameraX, Room. Depends on :core-color.
```

Keeping `:core-color` free of Android dependencies means the risky part runs in fast JVM unit tests
with no emulator. Do not let an `android.graphics.Color` import leak into it.

---

## 2. The colour engine (`:core-color`) — exact formulas

Implement in this order. Each gets a unit test before moving on.

### 2.1 sRGB <-> linear

```
toLinear(c):  c in [0,1]  ->  c / 12.92                 if c <= 0.04045
                              ((c + 0.055)/1.055)^2.4    otherwise

toSrgb(c):    c in [0,1]  ->  c * 12.92                  if c <= 0.0031308
                              1.055 * c^(1/2.4) - 0.055  otherwise
```

### 2.2 Linear sRGB -> XYZ (D65)

```
X = 0.4124564*R + 0.3575761*G + 0.1804375*B
Y = 0.2126729*R + 0.7151522*G + 0.0721750*B
Z = 0.0193339*R + 0.1191920*G + 0.9503041*B
```

### 2.3 XYZ -> CIELAB (D65 white: Xn=0.95047, Yn=1.00000, Zn=1.08883)

```
f(t) = t^(1/3)                if t > 216/24389   (0.008856)
       (841/108)*t + 4/29     otherwise

L* = 116 * f(Y/Yn) - 16
a* = 500 * (f(X/Xn) - f(Y/Yn))
b* = 200 * (f(Y/Yn) - f(Z/Zn))
```

### 2.4 Delta-E

Ship **CIE76** first — `sqrt(dL^2 + da^2 + db^2)` — it is three lines and unblocks the whole
pipeline. Then upgrade to **CIEDE2000**, which meaningfully changes ranking in saturated regions.
CIEDE2000 is easy to get subtly wrong: verify the implementation against the published Sharma et al.
CIEDE2000 test dataset (34 pairs) before trusting it. Keep CIE76 behind the same interface so the
upgrade is a one-line swap.

Interpretation bands, for UI copy:

| Delta-E | Verdict |
|---|---|
| < 1 | indistinguishable |
| 1-2 | very close |
| 2-3.5 | close (good mix) |
| 3.5-6 | noticeable difference |
| > 6 | poor — likely a gamut problem, see 2.7 |

### 2.5 Kubelka-Munk mixing — the core

Applied **per channel** on linear RGB. This is a practical approximation of a spectral model, not
physically exact, and that is fine (see `EVALUATION.md`, assumption 2).

```
# forward: reflectance -> K/S
ks(R)  = (1 - R)^2 / (2 * R)        with R clamped to [1e-3, 1 - 1e-6]

# inverse: K/S -> reflectance
inv(x) = 1 + x - sqrt(x*x + 2*x)

mix(colors[], parts[], strengths[]):
    w[i] = parts[i] * strengths[i]
    w    = w / sum(w)                        # normalise to concentrations
    for each channel c in (r, g, b):
        out[c] = inv( sum_i( w[i] * ks(colors[i].linear[c]) ) )
    return out
```

**`strengths` is the tinting-strength scalar and must be in the model from day one.** Default 1.0.
Single-constant Kubelka-Munk under-weights white and black; without this field, "colour + white"
recipes come out visibly too dark. Suggested defaults when a colour is flagged white: ~8.0; black:
~2.0. Expose as a slider in Phase 6.

**Required unit tests** — assert hue direction, not exact values:

```
yellow + ultramarine   -> green   (G dominant channel)
yellow + cyan          -> green
magenta + yellow       -> red
cyan + magenta         -> blue
X + X at any ratio     -> X       (idempotence)
mix(parts = [1, 0])    -> exactly the first colour
```

Also assert that a naive linear average **fails** these, so that nobody "simplifies" the mixer later
and silently breaks the product.

### 2.6 White-balance normalisation (von Kries)

Given the linear RGB of the tapped white reference `w = (rw, gw, bw)` and its assumed reflectance
`refl` (0.90 for white paper, 0.18 for an 18% grey card):

```
normalise(c)[ch] = clamp( c.linear[ch] * (refl / w[ch]), 0.0, 4.0 )
```

Store the result as the canonical linear value. The upper clamp is above 1.0 **on purpose** —
values brighter than the reference are legal and must not be crushed to white.

The white reference is mandatory (see Scope) — there is no unnormalised path. Note that
`toLinear()` is skipped when `CaptureSettings.linearTonemap` is true, since such images are already
linear; normalisation is otherwise identical.

### 2.7 The solver

```
solve(palette, targetLab, maxColors = 2):
    candidates = []
    for each unordered pair (i, j) in palette:
        for each (a, b) in PRACTICAL_RATIOS:
            predicted = mix([i, j], [a, b], strengths)
            candidates += Recipe(i, j, a, b, predicted,
                                 deltaE(toLab(predicted), targetLab))
    for each single colour i:                  # a pure colour may be the best answer
        candidates += Recipe(i, null, 1, 0, ...)
    return candidates.sortedBy(deltaE).dedupeByColorPair().take(5)
```

`PRACTICAL_RATIOS` — ratios a person can mix by eye, not a 1% grid:

```
1:1, 2:1, 3:1, 4:1, 5:1, 6:1, 8:1, 3:2, 5:2, 7:2, 4:3, 5:3, 5:4, 7:3   (plus each reversed)
```

Cost at 20 colours: 190 pairs x ~30 ratios = ~5,700 evaluations — sub-millisecond. Run it on
`Dispatchers.Default` regardless; never block the main thread.

**Gamut diagnosis — required, not optional.** When the best Delta-E > 6, do not just show the
least-bad mix. Compare the target Lab against the palette's range and emit a specific reason:

- `targetL > max(paletteL) + 3` -> "Nothing on your palette is light enough. Add white."
- `targetL < min(paletteL) - 3` -> "Nothing on your palette is dark enough. Add black."
- target chroma > max palette chroma -> "Your palette can't reach a colour this saturated."
- otherwise -> "Closest possible with this palette" + offer the 3-colour search.

The 3-colour search is the same loop over `C(n,3)` with 3-part ratios; still trivially fast. Offer it
behind a button rather than by default — a 2-colour recipe is much easier to mix by hand.

---

## 3. Data model (Room)

```kotlin
// Exact capture settings, captured once for the palette and REPLAYED for every
// target shot against it. Embedded in both Palette and TargetShot.
data class CaptureSettings(
  val exposureTimeNs: Long,
  val iso: Int,
  val awbGainR: Float, val awbGainGEven: Float,
  val awbGainGOdd: Float, val awbGainB: Float,
  val focusDistance: Float?,
  val linearTonemap: Boolean,   // true -> image is NOT sRGB-encoded, skip toLinear()
  val manualControlUsed: Boolean
)

@Entity data class Palette(
  @PrimaryKey val id: String,           // UUID
  val name: String,
  val imagePath: String,                // app-private internal storage
  val createdAt: Long,
  val whiteRefX: Float, val whiteRefY: Float,          // normalised image coords 0..1
  val whiteRefReflectance: Float = 0.90f,
  @Embedded val capture: CaptureSettings               // replayed for target shots
)

@Entity data class PaletteColor(
  @PrimaryKey val id: String,
  val paletteId: String,
  val orderIndex: Int,                  // pick order; default name "Color ${orderIndex + 1}"
  val name: String,                     // user-editable
  val sampleX: Float, val sampleY: Float,               // normalised 0..1, re-samplable
  val srgbHex: String,                  // display + export only
  val linR: Float, val linG: Float, val linB: Float,    // CANONICAL: normalised, linear
  val labL: Float, val labA: Float, val labB: Float,    // cached, derived
  val strength: Float = 1.0f,           // tinting strength
  val sampleStdDev: Float               // patch variance -> glare / unreliability flag
)

@Entity data class TargetShot(
  @PrimaryKey val id: String,
  val paletteId: String,
  val imagePath: String,
  val pickX: Float, val pickY: Float,
  val srgbHex: String,
  val linR: Float, val linG: Float, val linB: Float,
  val labL: Float, val labA: Float, val labB: Float,
  val whiteRefX: Float, val whiteRefY: Float,
  @Embedded val capture: CaptureSettings,   // must equal the palette's, else warn
  val createdAt: Long
)
```

On loading a target shot, assert its `CaptureSettings` matches the palette's. A mismatch means the
two images are not comparable — surface it rather than silently solving against bad data.

Recipes are **computed on demand, never stored** — they are cheap, and a cache would go stale the
moment a strength slider moves.

Store normalised (0..1) sample coordinates, never pixel coordinates, so samples survive image
resizing and rotation and can be re-derived if the colour model changes.

---

## 4. Capture and sampling

### 4.0 Device capability probe — build this first, before any capture code

The available level of manual control is hardware-dependent. Since there is exactly one target
device, establish the facts once instead of designing around the unknown. Build a debug screen that
reads `CameraCharacteristics` and displays:

```
INFO_SUPPORTED_HARDWARE_LEVEL           LEGACY | LIMITED | FULL | LEVEL_3
REQUEST_AVAILABLE_CAPABILITIES contains:
    MANUAL_SENSOR            -> manual exposure time + ISO available
    MANUAL_POST_PROCESSING   -> manual colour gains + tone curve available
    RAW                      -> DNG capture available
SENSOR_INFO_EXPOSURE_TIME_RANGE
SENSOR_INFO_SENSITIVITY_RANGE
TONEMAP_AVAILABLE_TONE_MAP_MODES
```

**Branch the capture implementation on the result:**

| Probe result | Capture path |
|---|---|
| `MANUAL_SENSOR` + `MANUAL_POST_PROCESSING` | full manual JPEG (4.1) — the expected case |
| `RAW` also present | optionally DNG (4.2) — better, more work |
| Neither | fall back to AE/AWB *lock* at shutter; white card does the heavy lifting |

Record the probe output in the repo once it is known, so later phases stop treating it as unknown.

### 4.1 Capture — full manual (CameraX + `Camera2Interop.Extender`)

CameraX does not expose these directly; reach them with `Camera2Interop.Extender` on the
`ImageCapture` / `Preview` builder (`androidx.camera.camera2.interop`, opt-in
`@ExperimentalCamera2Interop`). This is the reason for CameraX over a camera Intent.

Set on the capture request:

```
CONTROL_MODE              = OFF          # disable 3A entirely
CONTROL_AE_MODE           = OFF
SENSOR_EXPOSURE_TIME      = <stored ns>
SENSOR_SENSITIVITY        = <stored ISO>
CONTROL_AWB_MODE          = OFF
COLOR_CORRECTION_MODE     = TRANSFORM_MATRIX
COLOR_CORRECTION_GAINS    = <stored RggbChannelVector>
TONEMAP_MODE              = CONTRAST_CURVE
TONEMAP_CURVE             = identity (linear) curve
NOISE_REDUCTION_MODE      = OFF
EDGE_MODE                 = OFF
CONTROL_EFFECT_MODE       = OFF
CONTROL_SCENE_MODE        = DISABLED
JPEG_QUALITY              = 100
AF: fixed focus distance, or AF locked once and reused
```

**Leave `SHADING_MODE` ON (`HIGH_QUALITY`).** This is the one auto-correction to keep. It removes
vignetting; without it the frame edges are darker, so a white card near an edge reads darker than it
is and every normalised sample is systematically wrong — an error that presents as a mixing-model
failure and will waste days. Also keep the card and the paint at similar distances from frame centre.

**If the tone curve is linear, the image is no longer sRGB-encoded.** Skip `toLinear()` (2.1) for
such captures and use the values directly. Record which transfer function was used per image
(`CaptureSettings.linearTonemap`) — decoding with the wrong assumption silently corrupts every
colour. This is the most likely serious bug in the whole app.

**Persist and replay.** Store the exact settings used for the palette shot on the `Palette` row, and
reuse those same numbers for every target shot against that palette. Do not re-meter per shot. A
"re-meter" action can exist, but it invalidates the palette's colours and must say so.

Other capture notes:
- Flash off — direct flash makes specular hotspots on wet paint.
- Save full-resolution JPEG at quality 100 to app-internal storage. JPEG chroma subsampling bleeds
  colour across edges, which is a further reason to sample flat interior regions (4.3).
- Handle EXIF rotation on decode so stored normalised coordinates always refer to the upright image.

### 4.2 Optional: RAW / DNG capture

If the probe reports `RAW`, this is the strongest option: sensor data before tone mapping and before
any vendor computational-photography pass, already linear in scene radiance. Note that CameraX RAW
support has been limited historically — verify current status, and expect the reliable path to be
plain Camera2 with an `ImageReader` of format `RAW_SENSOR` plus `DngCreator`.

Extra work required: black level subtraction (`SENSOR_BLACK_LEVEL_PATTERN`), white level
(`SENSOR_INFO_WHITE_LEVEL`), and a camera-RGB -> XYZ matrix built from `SENSOR_FORWARD_MATRIX1/2`.
Demosaicing is avoidable — for a flat colour patch, average the R / Gr / Gb / B sites in the
neighbourhood directly.

**Do not build this for v1.** Keep the capture layer behind an interface so it can be added later,
and revisit only if Phase 5 shows accuracy is limited by capture rather than by the mixing model.

### 4.3 Sampling

Sampling a tapped point:

```
patch  = 9x9 px around the point (scaled to image resolution, minimum 5x5)
value  = per-channel MEDIAN of the patch      # median, not mean — rejects specular outliers
stdDev = per-channel standard deviation

if any channel median > 250 -> "too bright / blown out — sample elsewhere"
if stdDev > threshold       -> "inconsistent area — sample a flatter spot"
```

The median matters: one glare pixel in a mean drags the whole sample toward white.

**Picking UX:** a magnifier loupe showing the pixel neighbourhood with a crosshair, rendered offset
above the finger so it is not occluded. Pinch-zoom the image. Long-press to drag-refine a placed pick.

---

## 5. Screens

1. **Palette list** — cards with thumbnail, swatch strip, name. FAB: new palette.
2. **Palette capture** — camera preview, shutter, current manual settings shown for verification.
3. **White reference** — "Tap the white card", loupe, reflectance selector (white paper / grey card).
   Not skippable; the shot cannot be saved without it.
4. **Palette picking** — tap to add colours, numbered pins on the image, ordered list beneath,
   inline rename, swipe to delete, tap to resample. Save.
5. **Target capture** — the same camera screen, then the same white-reference step.
6. **Target pick** — one tap, loupe, large target swatch. "Find mix" button.
7. **Result** — the deliverable screen:
   - Target swatch **directly adjacent to** the predicted swatch (adjacency is what makes error
     visible; separated swatches hide it).
   - `Color 3 (Ultramarine) : Color 7 (Cad Yellow)  =  3 : 1`
   - Delta-E verdict in words, not a bare number.
   - Alternatives 2-5 in a list.
   - Gamut warning where applicable (2.7).
   - The palette photo with the two used colours highlighted — this is the `IDEA.md` output.
8. **Export / share** — render screen 7 to a PNG (palette photo + named swatches + recipe) and share.

---

## 6. Build phases

Each phase ends in something demonstrable. Do not start a phase before the previous one's acceptance
criteria pass.

### Phase 0 — Skeleton
Project, two modules, Compose scaffold, nav graph, Room configured with empty DAOs.
**Done when:** the app builds, runs, and navigates between placeholder screens.

### Phase 0.5 — Device capability probe *(do this before writing any capture code)*
The debug screen from 4.0, run once on the actual target device.
**Done when:** the hardware level and `MANUAL_SENSOR` / `MANUAL_POST_PROCESSING` / `RAW` flags are
known and written down, and the capture path from the 4.0 table is chosen. This is a couple of
hours of work that decides the architecture of Phase 2 — do not skip it or guess the answer.

### Phase 1 — Colour engine, headless *(highest risk — do it first)*
All of section 2 in `:core-color`, with JVM unit tests. No UI at all.
**Done when:** the 2.5 mixing tests pass; sRGB -> Lab -> sRGB round-trips within tolerance; the
solver returns a sensible recipe for a hand-written 12-colour synthetic palette in under 50 ms.

### Phase 2 — Capture + white reference
Manual capture per 4.1 (or the probe-selected fallback), settings persistence and replay,
internal storage, EXIF-correct decode, white-reference tap, normalisation.

**Done when — repeatability test:** photograph the same static scene 10 times across a session,
sample the same patch in each, and confirm the spread in normalised Lab is small (target: max
Delta-E between any two shots < 1.5). Then repeat after closing and reopening the app to confirm
replayed settings reproduce the same values.

**This is the acceptance test for assumption 3.** The controlled setup should make it pass easily; if
it does not, the cause is in capture, and no amount of work on the mixing model will compensate.
Stop and fix it before building on top.

### Phase 3 — Palette creation
Picking with loupe, ordered auto-naming, rename, delete, resample, persistence, palette list.
**Done when:** a palette survives an app restart with colours, names and order intact.

### Phase 4 — Target + solve + result
Target capture, single pick, solver wired in, result screen with adjacent swatches and gamut warnings.
**Done when:** the full `IDEA.md` loop works end to end on a real device.

### Phase 5 — Physical validation and strength calibration *(the real test of the product)*

Mostly not a coding phase, and the highest-value phase in the plan. Single-user scope is what makes
it worth doing: one paint set, fixed lighting, so the numbers you fit stay valid indefinitely.

**5a — Validation.** Mix 8-10 recommended recipes with actual paint, photograph the result under the
identical setup, and record predicted vs. actual Lab and Delta-E in a table committed to the repo.

**5b — Fit the tinting strengths.** For each paint, mix known ratios against a reference (white is
the important one), photograph, and fit `strength` by minimising total Delta-E between predicted and
measured across all samples. The model is cheap enough to fit by brute-force search over a strength
grid per paint — no optimiser library needed. Persist the fitted values as the per-paint defaults.

Build a small in-app helper for this: enter the ratio actually mixed, tap the resulting swatch, and
have the app log the (recipe, predicted, measured) triple. Doing this by hand across dozens of
samples is where the effort would otherwise go.

**Done when:** the median real-world Delta-E is under roughly 8, and — more importantly — **the hue
direction is correct in essentially every case.** Right hue with the value slightly off is a usable
app; wrong hue is not. Expect 5b to visibly improve every recipe involving white.

### Phase 6 — Refinement
Manual tinting-strength override sliders, 3-colour fallback, PNG export/share, CIEDE2000 upgrade if
still on CIE76, palette duplication, "reuse this palette" flow.

---

## 7. Explicitly out of scope for v1

Cloud sync, accounts, spectral upsampling, automatic colour-checker detection, iOS, paint-brand
databases, ML. Each is a reasonable v2; none is needed to find out whether the core idea works.

## 8. Note on third-party mixing libraries

Mixbox (Sochorova and Jamriska) does pigment-aware mixing and is more accurate than per-channel
Kubelka-Munk. Consider it only as a Phase 6 upgrade, and **check its licence terms before use** —
last known to be free for non-commercial use, with commercial licensing handled separately. Do not
make v1 depend on it; keep the interface in 2.5 swappable so it can be dropped in behind the same
signature.
