# Privacy

Offline mode (`ClientSettings.localOnly`) is **on by default**. Optional provider switches are off. In Offline mode the mod does not start service, provider, catalog, image or Mojang authentication requests. Turning Offline on prevents further sends; it cannot retract a request already sent. Local samples and cape settings work offline.

When you choose Go online or switch Offline off:

- The configured QuarkOS Capes site receives catalog filters/search text, paging cursors, requested cape identifiers, and UUIDs of players whose capes are looked up. Other-player website wearing references are polled in batches while in a world with Show others enabled.
- Authenticated actions (Wear/Unwear, Link, Publish/Unpublish and reports) use a service bearer. To obtain it, the client requests a challenge from the site, sends the Minecraft access token, selected profile UUID and challenge server ID directly to Mojang's `sessionserver.mojang.com/session/minecraft/join`, then sends the username and server ID to the site. The Minecraft access token is not sent to the cape site or texture providers. The site can associate the authenticated session with your Minecraft UUID/name.
- Wear sends a source and cape ID. Link sends the single-use code you enter. Publishing sends the cape PNG, title and animation/model metadata. Gallery search sends your search text; reports identify the reported cape. Published capes and website wearing references can be read by other users.
- Each optional provider receives the UUID or username needed by its API: OptiFine and Cloaks+ use usernames; LabyMod, MinecraftCapes and Cosmetica use UUIDs. Browse can fetch LabyMod catalog textures directly from `texture.laby.net` through URLs returned by the site, independently of the optional legacy provider switch. Other-player LabyMod wearing references require that switch.

The site and any directly contacted texture/provider host receive your IP address and normal HTTP headers, including `User-Agent: QuarkOS-Capes/1.0.0`. The site may forward catalog searches to its upstream providers. Some legacy provider URLs use HTTP. The mod's source does not establish a server operator's logging or retention policy.

Settings and the selected cape are saved under `config/quarkos_capes.json` and `config/quarkos_capes/local.{png,json}`. Service bearers are held in memory. The client has no analytics endpoint. Website account registration/password handling happens on the website, outside the mod.

Code: `client/local/{ClientSettings,LocalFiles,LocalSnapshot}.java`, `client/net/{CapeCalls,CapeHttp,SiteApi,CatalogClient}.java` under `src/main/java/dev/quarkos/capes/`.
