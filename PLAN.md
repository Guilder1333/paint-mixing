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

**Result (2026-09-05, see `DEVICE_REPORT.md`, gitignored — personal device identifiers):** target device is a
Nothing A059 (API 36). Back camera (id 0, `LEVEL_3`) reports `MANUAL_SENSOR` + `MANUAL_POST_PROCESSING` +
`RAW` all present — exposure time range 42228ns–32.7s, ISO 50–51200, tonemap modes CONTRAST_CURVE / FAST /
HIGH_QUALITY. **Capture path: full manual JPEG (4.1), best case.** RAW/DNG (4.2) is available if 4.1 accuracy
turns out to be capture-limited (see the note at the end of 4.2) — not needed for v1. `CONTRAST_CURVE` being
present confirms the identity/linear tonemap curve 4.1 calls for is settable.

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

**Result (2026-09-05):** 1.3 max ΔE over 10 shots (under the 1.5 target) with careful, static
handling; rose to 1.6 over 50 shots when handling was less careful. Traced to hand-shake from
pressing the on-screen touch Shutter button, not the capture pipeline itself — confirms the fixed-
settings-replay approach works; the remaining noise source was physical, not numerical. Fixed by
adding a remote-shutter trigger (`RemoteShutterController`): the Activity intercepts
`KEYCODE_VOLUME_UP/DOWN` (the de facto convention cheap Bluetooth camera remotes use) and
`KEYCODE_HEADSETHOOK`/`KEYCODE_MEDIA_PLAY_PAUSE` (a Bluetooth headset's play button) and routes them
to whichever capture screen is on screen, so a shot can be taken without touching the phone. Nothing
here is Android-automatic — a remote's volume-key click and a headset's media-button press are both
ordinary hardware `KeyEvent`s an app has to choose to treat as a shutter release.

**Update:** the headset play-button path did not fire via plain `dispatchKeyEvent` on the target
device -- confirmed by testing, not just theory. Bluetooth media-button presses are routed by the
platform's audio framework to whichever app holds the active `MediaSession`, not delivered as an
ordinary key event to the foreground Activity. Fixed with `MediaButtonShutter`: a registered
`MediaSessionCompat` (from `androidx.media`, deprecated-but-functional -- Media3 would be
considerable extra weight just to catch one hardware button) with a `STATE_PAUSED` playback state,
activated in `onStart`/deactivated in `onStop`. Volume-key handling (for an actual Bluetooth shutter
remote, not yet tested) stays on the plain `dispatchKeyEvent` path, since it isn't MediaSession-routed.

**Update 2:** the `MediaSessionCompat` fix *also* did not make the headset play button fire, on
retest. Two different implementation strategies failing points at something more fundamental than
an API detail -- plausibly the OS/Bluetooth stack not routing AVRCP play/pause to any app session at
all when nothing is actually playing audio anywhere on the device, which no in-app fix can work
around without this app also holding real audio focus/playback state (not attempted -- disproportionate
complexity for a shutter trigger). Rather than keep guessing blind (no earphones or BT remote on hand
to test against), fixed the actual root cause directly instead of chasing a specific trigger source:
**`CameraController.lock()`/`shootLocked()` are now called with a 3-second gap** (`SELF_TIMER_SECONDS`
in each capture screen) -- exposure/WB lock immediately, then the phone has a few seconds to go still
before the shutter actually fires, regardless of what pressed the button. This fixes the hand-shake
problem unconditionally, independent of whichever trigger (touch, volume-key remote, headset) ends up
being used. The `MediaButtonShutter`/headset code is left in place (harmless, might work on other
hardware) but is unconfirmed and not the relied-upon fix.

**Update 3:** the self-timer doesn't fully answer the concern either -- a touch at the *start* of
the countdown can still nudge a handheld or lightly-mounted phone, and the user asked to keep
pursuing an actual hands-off trigger rather than accept that. Built `RemoteDiagnosticsScreen`
(on-screen live log of every raw key/media-session signal, no camera involved) to stop guessing
blind. Result: **zero log lines for anything** -- not the Bluetooth headset's play button (which
audibly registered on the headset itself), and not even the phone's own physical volume keys. Volume
keys reaching neither `dispatchKeyEvent` nor any other app-level path is the more surprising half of
that result; it means something below the normal app layer -- plausibly Nothing OS's own
hardware-button handling -- is intercepting these before any app, ours included, ever sees them via
the mechanisms tried so far. Also means a dedicated Bluetooth remote (which emulates volume-up)
might hit the same wall, not just the headset. Fix in progress: `ShutterAccessibilityService`, an
`AccessibilityService` with `FLAG_REQUEST_FILTER_KEY_EVENTS` -- the mechanism hardware-button-
remapper apps use specifically because it sits at a lower level than normal app dispatch. Requires
a one-time manual grant (Settings > Accessibility > Paint Mixer shutter) that Android does not let
an app skip. `RemoteDiagnosticsScreen` logs through this path too, so the next test tells us
definitively whether anything can reach this app's process at all.

**Update 4:** `ShutterAccessibilityService` also logged zero for both the volume keys and the
headset. One more attempt was made for the headset specifically -- the user pointed out that real
media apps don't need Accessibility for this at all, because they earn media-button routing
honestly (holding audio focus, reporting `STATE_PLAYING`), not by merely registering a passive
session -- so `MediaButtonShutter` was updated to request transient audio focus and report
`STATE_PLAYING` instead of `STATE_PAUSED`. Untested before the decision below was made.

**Rolled back the entire headset/media-button path** (`MediaButtonShutter`, the `androidx.media`
dependency, and the media-specific key codes in `dispatchKeyEvent`/`ShutterAccessibilityService`)
at the user's request: four attempts (plain key dispatch, passive `MediaSessionCompat`, an
Accessibility service, then an audio-focus-holding `MediaSessionCompat`) with zero confirmed signal
is enough attempts without real hardware to test against. `RemoteShutterController` and
`ShutterAccessibilityService` remain, scoped to volume/camera keys only -- the mechanism an actual
dedicated Bluetooth shutter remote is expected to use, to be tested once one is in hand.

**Both a 3-second self-timer and a no-delay option are now offered as separate buttons** on Palette
Capture and the repeatability-test screen, rather than only the timer: a delay only helps when
something touches the phone to trigger the shot, so a genuinely hands-off trigger (once one is
confirmed working) should skip it. A remote-triggered shot always uses the no-delay path for the
same reason.

**Update 5 -- exposure boost for the identity tonemap.** The preview visibly darkening the instant
Shutter locks in the manual request is expected, not a bug: it's the identity tonemap curve (4.1)
actually removing the normal boosting S-curve a JPEG usually gets, which is exactly what makes the
pixel values proportional to linear scene brightness. But it exposed a real problem: reusing the
*metered* exposure verbatim (which was metered assuming that boost curve would follow) produced a
needlessly dark capture -- a white reference measured only ~90/255, wasting most of the 8-bit range
and leaving darker paints only a handful of distinguishable values. Fixed with
`CaptureSettings.withLinearExposureBoost()` (`Camera2ManualOptions.kt`): multiplies exposure time by
`LINEAR_EXPOSURE_BOOST_FACTOR` (2.2, chosen to target ~200/255 for white with headroom against
clipping specular highlights on glossy paint) applied exactly once, at the metered-reading ->
locked-and-persisted transition in `PaletteCaptureScreen`. Not reapplied on replay (repeatability
test, future target shots) -- what gets stored already reflects the boost. The 2.2 factor is a
starting point from one measurement and likely needs retuning; re-check the White Reference screen's
displayed median RGB after a fresh palette shot and adjust the constant if it's still noticeably
dark or starts clipping (`PatchSample.isBlownOut` will flag clipping directly).

**Result, retest:** 0.4 max ΔE over 10 shots, 0.5 over 20 -- well under the 1.5 target, a large
improvement over the pre-self-timer 1.3/1.6. **Phase 2's repeatability half of the acceptance test is
met.**

**Result, restart test:** 1.0 max ΔE over a pre-restart session, 0.6 over a post-restart session --
both still comfortably under 1.5 (normal session-to-session variance). Lab values compared by hand
across the actual app kill/relaunch showed no visible drift. **Both halves of Phase 2's acceptance
test are now met -- Phase 2 is done.**

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
