# Quark's Capes

A client-side Minecraft mod for choosing and wearing capes, with animation, cloth physics and custom models. Animated capes are free.

- **Loaders and versions:** Fabric 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11 and 26.3. Forge 1.20.1, 1.21.1, 1.21.4, 1.21.8 and 1.21.11. NeoForge 1.21.1, 1.21.4, 1.21.8 and 1.21.11.
- **Mod id:** `quarkos_capes`. It is client-only; servers don't need it.
- **Gallery:** browse LabyMod community cloaks live from laby.net. Previews are try-on only, kept in memory, and each one links back to its laby.net page. Nothing from LabyMod is bundled.
- **Offline by default:** the mod makes no network calls until you turn on online mode. See [PRIVACY.md](docs/PRIVACY.md) for every host it can contact.
- **Open it:** press **O**, use Mod Menu, or click the title-screen button.

## Build

Gradle runs on Java 25. The jars target Java 21 (Java 25 for 26.3).

```
./gradlew releaseAll verifyReleaseJar -Prelease=true -PserviceUrl=https://api.emilioschwaiger.com
```

The jars and `SHA256SUMS` are written to `dist/`.

## License

MIT, see [LICENSE](LICENSE). Bundled Cloth Config by shedaniel is LGPL-3.0-only and is not relicensed; see [NOTICE](NOTICE) and [CREDITS.md](CREDITS.md).

Not an official Minecraft product. Not approved by or associated with Mojang, Microsoft or LabyMod.

Issues: https://github.com/QuarkOS/quarkos-capes/issues
