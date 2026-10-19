# BundleMapCopier

A standalone server-side Paper/Folia 26.2 plugin for copying collections of filled
maps into supplied empty bundles, with configurable material costs. No client mod,
Vault, CommandAPI or other plugin dependency is required.

This download includes a compiled JAR in `dist/BundleMapCopier-0.1.1.jar`.

## Install (server owner)

1. Download `BundleMapCopier-0.1.1.jar` from the GitHub Release assets, not the
   automatically generated GitHub source-code ZIP.
2. Stop the server.
3. Put the JAR in the backend server's `plugins` directory.
4. Start the server and check the console for startup errors.
5. Edit `plugins/BundleMapCopier/config.yml` if desired, then restart.

Requires Java 25 and Paper/Folia 26.2. It is not a Fabric mod or a Velocity plugin.
The original project was developed for Folia 26.2 build 7 (commit 14b7fee).

The permission remains **`saddle.copymaps.use`**, defaulting to true, so existing
LuckPerms settings continue to apply. The historical Java package is retained;
its name does not create a dependency on Saddle.

## Use

Hold a bundle containing only filled maps. Carry ordinary empty maps and empty
bundles in your hotbar/main inventory.

| Command | Behavior |
| --- | --- |
| `/copymaps` | Make one additional complete set |
| `/copymaps 8` | Make eight additional complete sets |
| `/copymaps preview 8` | Quote costs and check supplies/space without changing inventory |

For 48 source maps, eight copies cost **384 empty maps and eight empty bundles**
with the default settings. An optional extra item fee applies per complete set.
Preview does not reserve items: the actual command validates the currently held
bundle and inventory again.

- The original bundle is untouched.
- Renamed **filled** maps are supported. Map IDs, item metadata, quantities and
  bundle contents order are preserved; duplicate IDs are copied and charged for.
- Copies share the original map data. They are not independent pictures. Lock
  map art beforehand if the image should no longer update.
- Each destination keeps the supplied empty bundle's color. The source bundle's
  name and wrapper metadata are not copied.
- Renamed/customized **payment items**, including empty bundles, can not be used and will not be consumed.
- Offhand, armor, ender chest and items inside other containers are not searched.
- The source must contain 1..64 maps with map IDs, with no other item types.
- Missing materials or space abort the entire operation without charging.
- Outputs use slots freed by payments; nothing is dropped on the ground.

## Configuration

```yaml
max-copies: 16
empty-maps-per-map: 1
fee:
  material: EMERALD
  per-copy: 0
```

`max-copies` and `empty-maps-per-map` accept 1..64. `fee.per-copy` accepts
0..1,000,000 and is added to the empty-map/bundle cost. For two emeralds per set,
set it to 2. Currency must be a valid item material; no economy balance is used.
Restart after changes. Invalid configuration disables the plugin.

## Develop on Windows

Install JDK 25. Open **this folder**, the one containing `gradlew.bat` and
`settings.gradle`, in VS Code. The wrapper JAR is included; no Gradle installation
is necessary.

```powershell
.\gradlew.bat clean build
```

The first build downloads Gradle 9.8.0 and Maven dependencies. Tests run as part
of `build`. Output:

```text
build/libs/BundleMapCopier-0.1.1.jar
```

On Linux/macOS use `bash ./gradlew clean build`.
This project uses the standard `jar` task: there is **no `shadowJar` task** because
the only production dependency is provided by the server.


## Publish downloadable releases

The included GitHub Actions workflow tests/builds on pushes to main/master and
pull requests, and stores the plugin JAR as a workflow artifact.

To make an ordinary downloadable release:

Run `clean build` locally and manually attach the JAR from
`build/libs` to the release. Do not commit it into source control.

## Server verification

After installation, test preview, one/many copies, named filled maps, differently
colored destination bundles, missing maps/bundles/fee items, non-map contents,
full inventory, permission denial and repeated commands with supplies for only
one copy. Check the console for Folia thread errors. Test as a non-operator when
checking LuckPerms restrictions.

Inventory work is scheduled on the player's owning entity thread. Each command
simulates all costs and outputs on detached stacks, then commits once. There is
no cross-tick inventory snapshot. This is not a guarantee against server crashes
or other plugins illegally changing inventory asynchronously.

Custom map metadata is copied. The owner should review any special map-item
integrations before production use.

## License and origin

Apache License 2.0 is retained. See LICENSE and
NOTICE for origin and standalone packaging changes.
