# Production smoke

Each `dist/` jar was launched with [portablemc](https://github.com/mindstorm38/portablemc) and the real Fabric, Forge, or NeoForge installer. Fabric API was downloaded from Modrinth. Forge and NeoForge have no extra required mod; Cloth Config is inside the jars that need it. The client loads a flat creative world, wears the bundled cloak, and saves a third-person shot. Nothing is published.

| Result | Loader | Minecraft | Installer | Modrinth deps | Screenshot |
| --- | --- | --- | --- | --- | --- |
| pass | fabric | 1.20.1 | `fabric:1.20.1:0.19.5` | fabric-api 0.92.12+1.20.1 | [docs/prod-smoke/fabric-1.20.1.png](prod-smoke/fabric-1.20.1.png) |
| pass | fabric | 1.21.1 | `fabric:1.21.1:0.19.5` | fabric-api 0.116.17+1.21.1 | [docs/prod-smoke/fabric-1.21.1.png](prod-smoke/fabric-1.21.1.png) |
| pass | fabric | 1.21.4 | `fabric:1.21.4:0.19.5` | fabric-api 0.119.4+1.21.4 | [docs/prod-smoke/fabric-1.21.4.png](prod-smoke/fabric-1.21.4.png) |
| pass | fabric | 1.21.8 | `fabric:1.21.8:0.19.5` | fabric-api 0.136.1+1.21.8 | [docs/prod-smoke/fabric-1.21.8.png](prod-smoke/fabric-1.21.8.png) |
| pass | fabric | 1.21.11 | `fabric:1.21.11:0.19.5` | fabric-api 0.141.6+1.21.11 | [docs/prod-smoke/fabric-1.21.11.png](prod-smoke/fabric-1.21.11.png) |
| pass | forge | 1.20.1 | `forge::1.20.1-47.4.29` | none | [docs/prod-smoke/forge-1.20.1.png](prod-smoke/forge-1.20.1.png) |
| pass | forge | 1.21.1 | `forge::1.21.1-52.1.20` | none | [docs/prod-smoke/forge-1.21.1.png](prod-smoke/forge-1.21.1.png) |
| pass | forge | 1.21.4 | `forge::1.21.4-54.1.22` | none | [docs/prod-smoke/forge-1.21.4.png](prod-smoke/forge-1.21.4.png) |
| pass | forge | 1.21.8 | `forge::1.21.8-58.1.26` | none | [docs/prod-smoke/forge-1.21.8.png](prod-smoke/forge-1.21.8.png) |
| pass | forge | 1.21.11 | `forge::1.21.11-61.2.4` | none | [docs/prod-smoke/forge-1.21.11.png](prod-smoke/forge-1.21.11.png) |
| pass | neoforge | 1.21.1 | `neoforge::21.1.265` | none | [docs/prod-smoke/neoforge-1.21.1.png](prod-smoke/neoforge-1.21.1.png) |
| pass | neoforge | 1.21.4 | `neoforge::21.4.161` | none | [docs/prod-smoke/neoforge-1.21.4.png](prod-smoke/neoforge-1.21.4.png) |
| pass | neoforge | 1.21.8 | `neoforge::21.8.57` | none | [docs/prod-smoke/neoforge-1.21.8.png](prod-smoke/neoforge-1.21.8.png) |
| pass | neoforge | 1.21.11 | `neoforge::21.11.48` | none | [docs/prod-smoke/neoforge-1.21.11.png](prod-smoke/neoforge-1.21.11.png) |

A pass needs the log line `quarkos_capes prod smoke ok` and a screenshot larger than 1000 bytes. After the Sol merge, Forge 1.21.1 (`52.1.20`) and Forge 1.21.11 (`61.2.4`) were booted again from the new release jars. The other twelve rows are the previous production smoke and were not rerun.
