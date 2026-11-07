# S Pen Revival

A lightweight native Android starter project for experimenting with stylus gestures and useful phone commands.

## Current prototype (0.1.0)

- Detects stylus/eraser touch events delivered to this app's window.
- Recognizes tap, directional swipes, and a stylus-button event when Android reports one.
- Includes buttons to open the camera app, adjust volume, and send media play/pause/next/previous key events.
- Builds a debug APK with GitHub Actions.

## Important S25 Ultra limitation

The S Pen supplied with the Galaxy S25 Ultra does not have Bluetooth Low Energy remote-control hardware. This app cannot restore S24 Ultra Bluetooth Air Actions, remote camera shutter, or gestures while the pen is away from the screen. Android also does not let an ordinary app observe stylus touches in other apps. The current gesture detector works only while this app is open and receiving the touch.

## Build

Pushes to `main` and manual workflow dispatches run **Actions → Build Android APK**. Download the `s-pen-revival-debug` artifact from a successful run. Install the APK on the phone after allowing installation from the browser/files app you used.

## Next milestones

1. Test which stylus events the S25 Ultra actually reports.
2. Add user-configurable gesture-to-command mappings.
3. Add app launching with Android package visibility handled properly.
4. Add screenshot capture through Android's user-approved MediaProjection flow.
5. Explore a permission-based accessibility shortcut service for supported global actions.

No accessibility, screenshot, or background-monitoring permissions are enabled in this starter build.
