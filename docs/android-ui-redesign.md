# FileFlow native Android glass UI

The Java/XML app now uses a shared glass design system based on the supplied four-phone reference. It includes Home, the complete Tools catalog, processing/result states, Recent and Settings. The React application, conversion engines and optional processing-service selection were not replaced. No WebView, Compose, paid runtime dependency, deployment or publication was introduced.

## What changed

- **Home:** navy/blue FileFlow wordmark, working menu and search actions, a translucent hero, native floating file artwork, “Choose a tool,” six popular tool cards and “See all.” Compact cards use two columns normally and one column for large accessibility fonts. At larger font scales the decorative hero illustration is hidden and text receives the full width.
- **Tools:** a recycled native ListView containing all ten real routes: PDF to Excel, PDF to Word, Image to PDF, PDF compression, scanner, image conversion, image compression, Word to PDF, PDF editor and upload preparation. All/PDF/Image/Document filters and text search operate on the same route registry used by Home and quick actions. Empty searches show an empty state.
- **Navigation:** four tabs and a central blue quick-tools action. Main retains Home/Tools state, filters and query through recreation; separate Recent/Settings activities use the existing activity back stack. Tool activities keep their real editing/selection forms and back controls. New bottom navigation is absent during the focused tool workflow.
- **Processing:** existing ViewModel state drives a shared native panel. It shows source filenames, the real engine message and an animated blue/purple **indeterminate** segment. No invented percentages, timers estimating completion, or fabricated per-file completion states are used. Multi-file operations are explicitly described as one batch because current ViewModels do not report per-file completion.
- **Cancellation:** the existing ViewModels do not expose a safe cancellation contract across their native operations. The panel therefore disables Cancel and explains the limitation. It does not claim a running operation has stopped. This is an intentional remaining limitation, not an implemented cancellation feature.
- **Success/error:** a result is shown only after a nonempty private output file exists. Save, Open and Share are in the result card; success has a short checkmark animation. Selecting a new file invalidates stale results. Errors show actual failure messages without pretending a PDF was produced. System viewer/share actions use FileProvider, read-only URI grants, known MIME types and the actual output display name.
- **Recent:** genuine retained outputs, date groups, file-format icons, size/time, search, All/PDF/Images/Docs filters and menus for Open, Save copy, Share, details and removal. Production history has no seeded documents. Older metadata-only history entries remain visible but their unavailable file actions are disabled.
- **Settings:** the existing language, ad-privacy and theme controls remain. Explicit light/dark choice persists, a system-theme option is available and bundled font licensing can be opened. History clearing explains that private copies are removed while externally saved copies remain.

## Native design resources

`ui/GlassBackdrop` draws static ambient gradients. `FileArtworkView`, `FileTile` and inline `FileTypeDrawable` draw colorful folded-file tiles, translucent bubbles and highlights using Canvas vectors; there are no large decorative bitmaps or full-screen blur passes. Artwork floats on a six-second cycle using a view transform. `GradientProgressView` supplies indeterminate processing feedback. `Ui` centralizes glass navigation, filters, touch feedback, quick actions and motion preferences. `ToolCatalog` is the shared tool component and destination registry. `ResultUi` binds existing engines to shared progress/result layouts.

The XML layer includes `screen_home`, `screen_tools`, bottom-navigation and processing components, glass/gradient drawables, navigation vectors and entrance/exit animations. Existing document/image/scanner/crop controls and their IDs are retained in the tool layouts. Light and night palettes share blue, cyan, purple, coral, emerald and orange accents, with navy/light typography respectively.

Inter is bundled from [Google's font repository](https://github.com/google/fonts/tree/main/ofl/inter) with the full SIL Open Font License in `assets/licenses/Inter-OFL.txt`. API 26+ uses the bundled variable font; API 24–25 retains a system sans-serif fallback. The theme font applies to the interface, not a replacement document-layout engine. Urdu resources were added for the new XML strings and the existing language switch is preserved. Remaining engine diagnostics are not all localized.

Motion durations: wordmark entrance 580 ms, tool press/release 160/220 ms, tab/content transition 240 ms, filters 220 ms, success 400 ms, Settings/theme entrance 350 ms and decorative float 6000 ms. Effects check Android animation settings. Artwork/progress animations stop when hidden, detached or their window backgrounds. No device FPS claim is made.

System bars, cutouts and keyboard insets are handled at the shared root using the maximum applicable inset, not summed keyboard/navigation padding. Edge-to-edge setup and resize handling follow [Android's documented View approach](https://developer.android.com/develop/ui/views/layout/edge-to-edge-manually). On older devices without dark navigation icons, the system navigation bar retains a dark fallback.

## Private file retention

Previously, Recent stored only output names and sizes; Open/Share could not be connected to an actual file. The processing workers now retain new successful outputs in `files/recent/` before publishing success, using unique internal names and a partial-file write/rename. FileProvider exposes only that dedicated private directory in addition to the original camera/share cache paths.

Retention is bounded to **30 entries and 100 MiB total**; a single retained output is also limited to 100 MiB. Old private copies are pruned only after the new history record commits. Removing/clearing Recent removes app-managed copies, never the original input or user-selected external save destinations. The existing metadata-only `HistoryStore.add` remains compatible with old entries. Storage failures are reported instead of displaying a false retained-file success. Processing, metadata validation and destination writes continue on existing background executors.

## Verification actually executed

The screenshots are **real Android Java/XML views rendered with Robolectric 4.17 native graphics**, mainly Android API 28. The inset test uses API 30. They are not HTML mockups, generated artwork pretending to be screenshots, emulator captures or physical-device results. The original committed app was rendered separately in an isolated checkout for the Home baseline.

The screenshot gallery is `.tools/android-ui/comparison.html`. It links:

| Screen/state | Evidence |
| --- | --- |
| Before Home | `before/home.png` |
| Home light / dark | `after/home.png`, `after/home-dark.png` |
| Tools light / dark | `after/tools-light.png`, `after/tools-dark.png` |
| Processing light / dark | `after/processing-light.png`, `after/processing-dark.png` |
| Success light / dark | `after/success-light.png`, `after/success-dark.png` |
| Recent light / dark | `after/recent-light.png`, `after/recent-dark.png` |
| Recent empty | `after/recent-dark-empty.png` |
| Settings dark | `after/settings-dark.png` |
| Compact / large fonts | `after/home-320-large-font.png`, `after/tools-320-large-font.png` |
| Larger/dense phone | `after/home-480-xhdpi.png` |
| Real failed processing | `after/error-light.png` |

Standard captures use a 390 × 844 dp viewport at mdpi; compact captures use 320 × 740 dp with font scale 1.6 and animations disabled; the large capture uses 480 × 960 dp at xhdpi. Native snapshots contain app UI only; phone frames and marketing annotations are not included in the app.

| Check | Result |
| --- | --- |
| Debug APK | Built successfully |
| Instrumentation APK | Built successfully; not executed without a device |
| JVM suite | 31 tests passed, zero failures/errors/skips: 21 existing tests plus 10 native UI/workflow tests |
| Lint | Passed with zero errors and 63 warnings; details are in the generated lint report |
| Tool routes | All ten catalog routes clicked and their target activities created/checked in native tests |
| Filters/search | Native Tools list filtering, matching counts and no-results state checked |
| Real file selection | Actual ActivityResult OpenDocument contract dispatched a locally authored image into validation and selection |
| Real conversion/save | PNG converted to JPEG through the actual activity button, decoded independently, retained, then saved through the CreateDocument contract and verified |
| Real PDF processing | Two authored PDFs merged through the actual PDF editor selection/button; saved bytes independently parsed as two pages |
| Success/file existence | Generated nonempty private files checked before results; output actions appear in the result card |
| Failure | Missing input produces a real error and no valid result |
| Recent/storage | Real generated outputs, legacy metadata handling, deletion, clearing and 30-entry pruning checked |
| Theme | Native dark resources rendered; explicit theme choice stored locally |
| Reduced motion | Core UI/conversion still works with animations disabled |
| Insets | Simulated system bars and IME tested without double-adding their heights |

`verified-merged-light.pdf` and `verified-merged-dark.pdf` in the evidence directory contain the actual two-page PDFBox results. `verification.json` records test totals, screenshot inventory, font checksum and the APK's size/checksum. These artifacts and fixtures are intentionally ignored by Git. The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`, approximately 24 MB. Robolectric is test-only and is not packaged into the APK.

Independent PyMuPDF inspection also confirmed both saved PDFs have two pages and retain the authored blue/green drawing on the corresponding page. This verifies actual content survived the UI-driven operation, beyond merely creating a file or checking its header. Intermittent Windows AAPT2 startup failures were resolved for the final checked build by using one worker and a 1 GiB Gradle heap; no project dependency or system component was replaced.

## Remaining device work

`adb devices` returned no devices and the SDK emulator installation has no runnable emulator executable. No emulator or physical device was claimed. In this Windows JVM environment Android's native `PdfDocument` creation path returned “document is closed”; it was not mocked into producing a successful image-to-PDF. Real PDF verification instead uses the existing PDFBox merging engine, and real image-format conversion uses native bitmap codecs. Image-to-PDF/scanner PDF export and the other native-code processing paths still require device testing.

System viewer/share receiving apps, cross-process URI grants, camera capture, physical gesture/three-button navigation, actual keyboard behavior across OEM devices, API 24–25 font fallback and TalkBack need device checks. The instrumentation suite includes the full catalog navigation and a real FileProvider read test for execution on a connected device. JVM tests exercise either the Open/Share intent route or a meaningful unavailable-storage error in the simulator; they do not establish compatibility with installed receiving apps.

No 60 FPS, peak-memory, battery or hardware-rendering measurements were available. The native vector/transform approach avoids full-screen blur and bitmap decoration, but a release still needs device profiling. The artwork is a native vector interpretation of the reference, not a pixel-identical reconstruction of its rendered 3D objects.

Native Android's pre-existing Word-to-PDF renderer limitation remains: it offers the installed-office-app route, explicit basic text export and an explicitly configured service choice. This UI work does not claim an embedded high-fidelity DOCX engine. No processing library was swapped to satisfy the UI tests.

## Reproduce

With an Android SDK/JDK installed:

```powershell
cd android
./gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --max-workers=2 --no-daemon --console=plain
# Resource-constrained Windows fallback used for the final checked build:
./gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' --no-daemon --console=plain
# Once a device/emulator is connected:
./gradlew.bat connectedDebugAndroidTest
cd ..
.tools/conversion-venv/Scripts/python.exe scripts/android-ui-evidence.py
```

Set `ANDROID_HOME`, `GRADLE_USER_HOME` and `ANDROID_USER_HOME` for the local SDK/caches as required. This workspace uses TEMP/TMP under `.tools/runtime-tmp` because the system drive has limited space. A cold test run downloads Robolectric SDK/test artifacts; a production APK does not download them. The evidence script uses standard Python only.

The exact modified/new file inventory is [android-ui-redesign-files.txt](android-ui-redesign-files.txt). No commit, push, deployment or store publication was performed for this redesign. Pre-existing untracked `test/` and user IDE files were left untouched.
