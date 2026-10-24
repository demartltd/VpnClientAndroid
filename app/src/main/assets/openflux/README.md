# OpenFlux sidecar (arm64)

```bash
./scripts/openflux-build-android.sh   # needs Go + Android NDK
```

Produces `jniLibs/arm64-v8a/libopenflux.so` (CGO / `GOOS=android` — system DNS).  
Pure `linux/arm64` builds fail DNS on device (`[::1]:53`). Pin `OPENFLUX_REF` (default `v0.13.0`). Binaries are gitignored.
