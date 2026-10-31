# OpenFlux sidecar (arm64)

Соберите локально `jniLibs/arm64-v8a/libopenflux.so` (Go + Android NDK, `GOOS=android` — system DNS).  
Pure `linux/arm64` builds fail DNS on device (`[::1]:53`). Pin OpenFlux ref (default `v0.13.0`). Binaries are gitignored.
