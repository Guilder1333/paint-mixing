# Evaluation of the Paint Mixer idea

**Verdict: feasible.** Nothing here needs research — every piece is a known, solved problem with
published formulas. The app is small (a few thousand lines). But one of your four assumptions is
right for the wrong reason, and one is the real risk to the whole thing.

---

## Your assumptions, audited

### 1. "Color mixing is predictable to some point" — TRUE, but only with the right model

This is true, and it is the part people usually get wrong. Paint mixing is **subtractive**, and
averaging RGB values models **additive** (light) mixing. If the app averages RGB it will produce
mud for every single mix and the product is worthless.

Measured, mixing at 50/50 (see `PLAN.md` for the formula):

| Mix | Kubelka-Munk (correct) | Linear RGB average (wrong) | Reality |
|---|---|---|---|
| yellow + ultramarine | dark green | greyish tan | dark green |
| yellow + cyan | green | pale grey | green |
| magenta + yellow | red | orange-tan | red |
| cyan + magenta | blue | mauve | blue |

So: the assumption holds, **conditional on using Kubelka-Munk, not averaging.** This is the single
most important technical decision in the app, and it is about 20 lines of code.

**Known limit found while testing:** single-constant Kubelka-Munk under-weights white and black.
`ultramarine + white` at 50/50 predicts a colour noticeably darker than real titanium white gives,
because real white has enormous tinting strength. Fix: a per-paint **tinting strength** scalar
(default 1.0, white ~6-10). Verified this corrects the behaviour cleanly and monotonically. The
field must exist in the data model from day one; the calibration UI can come later.

### 2. "I don't need high precision" — TRUE, and this is what makes the project viable

Correct, and worth leaning on. Hand-mixing on a palette knife has far more error than the model
does. Chasing spectral accuracy would multiply the work for gains you would never see in a paint
tube. Target "visually close, then eyeball the last 10%" and the project stays small.

### 3. "Two pictures in the same conditions is good enough" — TRUE, because the conditions are enforced

*(Revised after clarification: this is a deliberate scoping decision. Single user, fixed room, fixed
light, fixed camera position, white card in frame.)*

Under those constraints the assumption holds, and it is the thing that makes the whole project
tractable. Controlling the capture side is far cheaper than modelling your way around uncontrolled
capture, and it is the correct trade for a single-user tool. The generic version of this app — many
users, arbitrary lighting, arbitrary phones — is a much harder product, and you have simply opted
out of it.

What remains is not illuminant variation but **residual pipeline drift**: the camera re-running auto
white balance, auto exposure and tone mapping per shot even when the scene is identical. Two fixes,
in order of strength:

1. **Manual capture settings, persisted and replayed.** Android exposes this (`CONTROL_AE_MODE_OFF`,
   `CONTROL_AWB_MODE_OFF`, manual exposure/ISO/colour gains, identity tone curve) subject to
   hardware capability flags. Because there is one user and one phone, store the exact settings used
   for the palette shot and **reuse those same numbers for every target shot**. This makes the two
   images comparable at the sensor level, not just approximately.
2. **The white card**, tapped and divided out per channel (von Kries). This catches whatever drift
   the manual settings did not.

With both, assumption 3 is solid. The white card is worth keeping even under manual control — it is
nearly free and it is the only thing that will tell you when something has silently changed.

**One trap specific to your fixed rig:** do *not* switch off lens shading correction while disabling
the other auto-corrections. Vignetting darkens the frame edges, so if the white card sits near an
edge and the paint near the centre, the card reads darker than it is and every normalised sample is
scaled wrong — a systematic error that looks like a mixing-model failure. Keep `SHADING_MODE` on,
and keep the card and the paint at similar distances from the frame centre.

**Consequence for the plan:** the "uncalibrated" fallback path, guidance overlays and onboarding copy
all become unnecessary. The capture step gets *stricter* and the UI around it gets *smaller*.

### 4. "Pure brute force over all pairs is good enough" — TRUE, and you can afford more

Very safe. 20 palette colours = 190 pairs, times ~30 practical ratios = ~5,700 evaluations, which is
sub-millisecond. You are so far inside budget that **three-colour mixes are also free** (~1,140
triples, still trivial). Recommend shipping pairs as the headline answer, with triples as a "still
not close enough?" fallback.

One refinement: don't search a fine 1% grid. Search directly over **ratios a human can actually
mix** — 1:1, 2:1, 3:1, 3:2, 5:1... Nobody measures out 63:37. This is faster *and* more usable.

---

## Risks you didn't list

1. **Gamut — the honest deal-breaker for individual matches.** A mix of two paints can never be
   lighter or more saturated than its ingredients. If the target is lighter than everything on the
   palette, there is no correct answer. The app must **detect this and say so** ("nothing on your
   palette is light enough — add white"), not silently return the least-bad mix. An app that says
   "I can't do this one" is trusted; one that always answers is not.
2. **Glare.** Wet paint is glossy. A specular highlight blows out the sample and reads as near-white.
   Mitigate: sample a patch and take the **median**, not one pixel; reject samples that are
   near-clipping or high-variance, and tell the user to re-shoot.
3. **Fat-finger picking.** Picking an exact pixel by touch is unreliable. Needs a magnifier loupe
   offset above the finger. Standard, but don't skip it.
4. **Conceptual gap.** The app matches *the colour in the target photo*. That is the colour of an
   object under that scene's lighting, not necessarily the paint colour an artist would choose for
   it. Worth a line of UI copy so expectations are right.
5. **Tube pigments vary spectrally in ways 3 channels can't see.** Two blues that look identical in a
   photo can mix to different greens (this is metamerism, and it is real and unavoidable at RGB). It
   is a genuine accuracy ceiling — acceptable under assumption 2, but it means the app should never
   present results as exact.

---

## What I'd change about the plan as described

- **Add white and black to the palette, always.** Most useful mixes are "chromatic + white". Without
  white the gamut is tiny and most targets will be unreachable.
- **Show the error, in the user's language.** Report the predicted swatch beside the target swatch,
  plus a plain verdict ("very close" / "noticeable" / "your palette can't reach this"). Trust comes
  from the app admitting when it is wrong.
- **Build the colour engine first, headless, with unit tests, before any Android UI.** All the
  project risk is in ~300 lines of pure maths. Prove it on the JVM in a day; the app around it is
  routine. This ordering is what de-risks the build.
- **Promote tinting-strength calibration from "nice to have" to a real phase.** This is the single
  biggest accuracy win available to you, and single-user scope is exactly what makes it worth doing.
  With one fixed paint set and fixed lighting, you can mix a handful of known ratios, photograph
  them, and fit each paint's strength value by least squares against what the model predicted. The
  numbers stay valid indefinitely because nothing about your setup changes. That is a far better
  return on effort than any amount of spectral modelling, and it directly corrects the known
  white/black weakness in assumption 1.

## Answer to "the best colour encoding format"

Store three representations, not one:
- **sRGB 8-bit hex** — for display and export only.
- **Linear sRGB, 3 floats, after white-reference normalisation** — the canonical value; all mixing
  maths runs here. Cannot be stored as hex, since normalised values can exceed 1.0.
- **CIELAB (D65)** — for "how close is this to that". Never measure colour distance in RGB.

Formulas and schema in `PLAN.md`.
