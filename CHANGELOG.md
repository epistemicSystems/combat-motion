# Changelog
All notable changes to CombatSys Motion Analysis Platform will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned for v1.0.0 (MVP)
- Real-time skeleton tracking
- Breathing analysis with Eulerian magnification
- Posture analysis
- Session recording and playback
- Multi-session comparison
- User calibration and personalization
- Cross-platform desktop app (macOS, Windows, Linux)

---

## [0.3.0] - LOD 3 - 2025-11-XX (Target: Day 10)

### Added
- WebGPU compute shaders for Eulerian magnification
- Motion magnification UI with ROI selection
- Side-by-side video comparison (original vs magnified)

### Changed
- Improved pose processing performance (now <5ms per frame)
- Enhanced breathing analysis accuracy

### Fixed
- Memory leak in video frame upload/download
- Magnification artifacts at high gain values

---

## [0.2.0] - LOD 2 - 2025-11-XX (Target: Day 7)

### Added
- Real breathing analysis from torso motion
- FFT-based breathing rate detection
- Fatigue window detection
- Coaching insights generation

### Changed
- Replaced stub breathing analyzer with real implementation
- Improved timeline UI with breathing waveform overlay

### Fixed
- Pose processing edge cases (occluded landmarks)

---

## [0.1.0] - LOD 1 - 2025-11-XX (Target: Day 4)

### Added
- Real camera capture via getUserMedia
- MediaPipe integration for pose estimation
- Real-time skeleton overlay
- Session recording (video + EDN)
- Session playback with synchronized skeleton

### Changed
- Replaced mock camera with real webcam integration
- Replaced stub pose processing with real angle computation

### Fixed
- Camera permission handling
- Video encoding issues on Linux

---

## [0.0.1] - LOD 0 - 2025-11-17 (Day 1)

### Added
- Initial project structure
- EDN schema definitions for session, frame, analysis
- Mock data generators
- Stub analyzers (breathing, posture)
- Basic Reagent UI with timeline viewer
- Main loop skeleton (functional but using mocks)
- shadow-cljs build configuration
- Electron app shell

### Infrastructure
- Set up ClojureScript + Electron + Reagent + re-frame
- Configured shadow-cljs for :main and :renderer builds
- Created project documentation (SPEC.md, PLAN.md, TODO.md)

### Known Issues
- All data is mocked (no real capture yet)
- Analyzers return hardcoded values
- UI is basic (no styling)

---

## Version History Context

### LOD (Level of Detail) Development Strategy

This project uses a progressive refinement approach where each LOD stage builds on the previous:

- **LOD 0**: Mock pipeline (everything connected, nothing real)
- **LOD 1**: Real capture (camera, pose estimation)
- **LOD 2**: Real analysis (breathing, posture)
- **LOD 3**: GPU processing (magnification)
- **LOD 4**: Multi-analyzer support
- **LOD 5**: Personalization
- **LOD 6**: Comparison & trends
- **LOD 7**: Production polish

Each LOD stage results in a working, demonstrable system.

---

## Future Roadmap

### v1.1.0 (Post-MVP) - Estimated Q1 2026
- Gait analyzer
- Balance analyzer
- Multi-camera support (stereo depth)
- Advanced motion magnification (phase-based)

### v1.2.0 - Estimated Q2 2026
- Cloud sync (optional, encrypted)
- Coach dashboard (multi-user support)
- Mobile companion app (iOS/Android)
- Export to PDF with annotations

### v2.0.0 - Estimated Q3 2026
- 3D body model fitting
- Real-time 3DGS reconstruction
- AR visualization (smart glasses integration)
- Generative AI coaching (LLM integration)

---

## Deprecation Notices

### Upcoming in v2.0
- Legacy EDN format will be migrated to new schema
  - Migration tool will be provided
  - Old sessions will be automatically converted on first load

---

## Notes

### Version Numbering
- Major version (X.0.0): Breaking changes to IR schema or API
- Minor version (0.X.0): New features (analyzers, UI components)
- Patch version (0.0.X): Bug fixes, performance improvements

### Release Process
1. Update CHANGELOG.md with release notes
2. Update version in package.json
3. Tag release in git: `git tag -a vX.Y.Z -m "Release vX.Y.Z"`
4. Build installers: `npm run build`
5. Publish release artifacts

---

[Unreleased]: https://github.com/combatsys/motion-analysis/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/combatsys/motion-analysis/releases/tag/v0.0.1
