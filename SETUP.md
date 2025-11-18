# CombatSys Motion Analysis - Quick Setup

## Installation

```bash
# Clone the repository (if not already done)
cd combat-motion

# Install dependencies
npm install

# Verify installation
npx shadow-cljs --version
```

## Development

### Start Development Server

**Terminal 1 - ClojureScript Compiler:**
```bash
npx shadow-cljs watch main renderer
```

Wait for compilation to complete:
```
[:main] Build completed.
[:renderer] Build completed.
```

**Terminal 2 - Electron:**
```bash
npm start
```

### Development Workflow

1. Edit ClojureScript files in `src/`
2. Save file → shadow-cljs recompiles automatically
3. UI hot-reloads (no restart needed)
4. Check DevTools console for logs

### Available Commands

```bash
# Development (hot reload)
npm run watch    # Or: npx shadow-cljs watch main renderer
npm start        # Start Electron

# Build (optimized)
npm run compile  # Or: npx shadow-cljs compile main renderer
npm run release  # Or: npx shadow-cljs release main renderer

# Clean
npm run clean    # Remove build artifacts
```

## Project Structure

```
combat-motion/
├── src/
│   ├── main/              # Electron main process (Node.js)
│   │   └── core.cljs      # App lifecycle, window management
│   ├── renderer/          # Electron renderer (Browser)
│   │   ├── core.cljs      # Entry point, React mounting
│   │   ├── state.cljs     # re-frame state management
│   │   ├── views.cljs     # UI components
│   │   ├── camera.cljs    # Camera capture (NEW)
│   │   └── video_capture.cljs  # Video display (NEW)
│   └── shared/            # Pure ClojureScript (no platform deps)
│       ├── schema.cljs    # EDN schemas
│       ├── mocks.cljs     # Mock data
│       ├── breathing.cljs # Breathing analyzer
│       └── posture.cljs   # Posture analyzer
├── resources/
│   └── public/
│       └── index.html     # HTML entry point
├── test/
│   └── shared/            # Tests
├── package.json           # npm dependencies
├── shadow-cljs.edn        # ClojureScript build config
└── README.md
```

## What's Working

✅ **Electron app** - Desktop application launches
✅ **UI framework** - Reagent/React with re-frame state
✅ **Camera integration** - Live video feed via getUserMedia
✅ **Frame capture** - 15fps capture with FPS monitoring
✅ **State management** - Centralized re-frame state
✅ **Mock analyzers** - Breathing & posture (stub implementations)

## What's Next

🔲 **MediaPipe integration** - Real pose estimation
🔲 **Skeleton overlay** - Draw pose on video
🔲 **Pose processing** - Joint angles, velocities
🔲 **Real breathing analysis** - FFT-based rate detection
🔲 **Session recording** - Save to disk

## Troubleshooting

**"Cannot find module 'electron'"**
```bash
npm install
```

**"Build failed"**
```bash
npm run clean
npx shadow-cljs watch main renderer
```

**"Port 8021 already in use"**
```bash
# Kill existing process
lsof -ti:8021 | xargs kill -9
# Or change port in shadow-cljs.edn
```

**"Camera permission denied"**
- Check browser/system settings
- Grant camera permission when prompted
- Restart app if needed

## Development Tips

### REPL-Driven Development
```bash
# Connect to renderer REPL
npx shadow-cljs cljs-repl renderer
```

Then evaluate code:
```clojure
(require '[combatsys.renderer.camera :as camera])
(camera/check-camera-support)
;=> true

(require '[combatsys.renderer.state :as state])
@re-frame.db/app-db
;=> {:ui {...} :camera {...} ...}
```

### Hot Reload
- Edit `.cljs` files → auto-recompiles
- Most changes reload without restart
- State preserved across reloads
- Check DevTools console for reload messages

### Debugging
- Open DevTools: View → Toggle Developer Tools
- Console shows all logs
- re-frame-10x for state inspection (if enabled)
- Add `(println ...)` for quick debugging

## Performance Targets

- **Video FPS**: 30fps ✓
- **Capture FPS**: 15fps ✓
- **Frame latency**: <100ms ✓
- **UI responsiveness**: <16ms/frame ✓

## Documentation

- `CAMERA_INTEGRATION.md` - Camera integration details
- `CLAUDE.md` - Development philosophy and team roles
- `SPEC.md` - Technical specifications
- `PLAN.md` - Development roadmap (LOD 0-6)
- `TASKS.md` - Granular task breakdown

## Questions?

Check the documentation files or open DevTools console for diagnostic information.
