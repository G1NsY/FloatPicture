# Floating control layer verification

Date: 2026-09-08

## Direct expansion follow-up

Right-docked expansion now prepares the complete panel at its final size and
position while leaving the old dot unchanged. The panel is revealed after its
first draw and the dot is retired. This avoids first resizing/moving the visible
dot window and then replacing it, and avoids the horizontal window move animation
observed even with `windowAnimations = 0`. Window positions are anchored to the
docked edge; stored positions and drag calculations remain physical-left coordinates.

All **9 tests passed** on Android 14, including the original six scenarios,
direct expansion on each side with an unchanged docked edge, and leftward dragging
from the right side. The drag starts inside the dot away from Android's edge Back
gesture region; starting at its center on this gesture-navigation emulator was
cancelled by the system before a MOVE reached the controller.

Build and `lintDebug` passed. The final run is in
`direct-expand/test-results.txt`. `direct-expand/verification-verified.mp4` and
`direct-expand/right-expansion-verified-frames.png` show the final right-side panel
appearing fully at its final location with no intermediate slide. Earlier
`verification.mp4` and `verification-final.mp4` files are diagnostic recordings,
not the final accepted run.

## Fix verification

The subsequent fix raises the controller whenever a `FloatImageView` attaches,
covering newly imported pictures, editor previews and re-shown windows. The
controller subscribes only while shown and coalesces attachment callbacks. The
existing replacement-window rendering is retained, including the current
precision mode and any pending confirmation. Shutdown now removes both retired
windows and newly added windows that have not drawn yet, before recycling the icon.

On the same Android 14 emulator, all **6 tests passed**:

- All three original layer-order scenarios below now open the controller with
  zero picture DOWN events at the controller's location.
- A confirmation stays open and its confirm button accepts an injected touch.
- The precision panel remains open after a new picture appears.
- Closing during window replacement removes both windows and does not reappear
  when another picture attaches.

Debug APK and Android test APK builds and `lintDebug` passed. Fixed screenshots
and the complete test output are saved under `fixed/`; the original reproduction
screenshots remain in this directory. Samsung-device verification is still pending.

## Original reproduction (before the fix)

Device: emulator-5554, Android 14 (API 34), 1920 × 1080.
App: current 2.0.7(Beta) debug build, including the earlier expand-time
`bringControlToFront()` change. No new production fix was applied for this check.

## Method

`FloatingControlLayerTest` uses the actual `FloatingControlManager`,
`FloatImageView`, and `WindowsMethods` window creation and resize code. It
injects a real touchscreen DOWN/UP at the collapsed controller's screen center,
then checks whether the controller expanded and whether the picture received
the touch. The picture is touchable and enlarged beyond the display dimensions.

The test creates temporary windows directly. It does not exercise the complete
image picker/save UI or the notification service lifecycle. Controller `show()`
is invoked directly to avoid loading or changing the existing picture library.
The temporary picture is not registered or saved, so the control panel's
"No picture" label in the screenshots is expected and unrelated to the reported
empty-library issue. Temporary windows are removed after each test.

## Results

| Scenario | Controller expanded | Picture DOWN events | Result |
| --- | --- | --- | --- |
| Picture created, then controller created, then picture enlarged | true | 0 | PASS |
| Controller created, then picture created and enlarged | false | 1 | FAIL |
| Picture created, then controller created, then picture detached/re-added and enlarged | false | 1 | FAIL |

All taps were injected at (1887, 181). Three tests ran; two assertions failed
because the controller was not reachable. These failures document the existing
bug and should pass after the layer-order repair.

Screenshots and corresponding `dumpsys window windows` output are in this folder:

- `controller-first-before.png` / `controller-first-after.png`
- `picture-reattached-before.png` / `picture-reattached-after.png`
- `picture-first-before.png` / `picture-first-after.png`

## Interpretation

The previous expand-time fix does not help when the initial touch is intercepted
by the picture. Enlarging a picture that is already below the controller did not
break access in the control scenario. A later picture window attachment can
place the picture above the existing controller. This reproduces the blocked
controller failure on Android 14, but does not establish the exact sequence or
device-specific behavior of the original Samsung report.

## Run

Install the current debug app and Android test APK without clearing data, grant
overlay permission to the app, then run:

```text
adb -s emulator-5554 shell am instrument -w -r -e class tool.xfy9326.floatpicture.Services.FloatingControlLayerTest tool.g1nsy.floatpicture.test/androidx.test.runner.AndroidJUnitRunner
```

Screenshots are written to the target app's external files `layer-probe` folder.
