# Transferable Pets

This fork ports WinterWolfSV's Transferable Pets to **Minecraft Java 26.3 on Fabric**.
Install it on the **dedicated server only**: players use completely unmodified vanilla
26.3 clients, without Fabric, client mods, or a server resource pack.

The owner leashes their pets, then sneaks and right-clicks another player with the main
hand. All eligible nearby pets change owner and leash holder together, with the original
action-bar messages and heart particles. This port adds no commands, untaming, custom
items, or IndyPets dependency.

## Compatibility and installation

| Component | Requirement | Verified version |
| --- | --- | --- |
| Minecraft Java server and clients | Exactly 26.3 | 26.3 |
| Server Java runtime | Java 25 or newer | OpenJDK 25 |
| Fabric Loader on the server | 0.19.5 or newer | 0.19.5 |
| Fabric API on the server | 0.161.0+26.3 or newer, built for 26.3 | 0.161.0+26.3 |
| Client installation | None | See multiplayer verification limitation below |

1. Back up your world and install a [Fabric dedicated server](https://fabricmc.net/use/server/)
   for Minecraft 26.3 with the requirements above. Accept the Minecraft EULA yourself
   before operating your server.
2. Build this fork as described below. Copy
   `build/libs/transferable_pets-[26.3]-1.0.6.jar` and the matching
   [Fabric API JAR](https://modrinth.com/mod/fabric-api) into the **server's** `mods/`
   directory. Remove any older Transferable Pets JAR. Do not install the `-sources.jar`.
3. Start the server with Java 25 or newer, for example:

   ```sh
   java -Xmx2G -jar fabric-server-launch.jar nogui
   ```

4. Connect using the normal vanilla Minecraft 26.3 launcher profile. Nothing needs to
   be installed or downloaded on clients for this feature.

The mod uses only vanilla entities, ownership data, interaction packets, leash updates,
messages, and particles. It registers no custom content or networking channels.
Metadata retains `environment: "*"` to avoid disabling upstream's integrated-server
behavior; the actual transfer is guarded by `ServerPlayer` and runs only on the logical
server. Installing it on joining clients is neither required nor part of the dedicated
server setup.

The upstream [Modrinth page](https://modrinth.com/mod/transferable-pets) remains the
original project's page; it is not a publication of this fork's 26.3 build.

## Usage and eligibility

1. Tame your pets and attach their leads to yourself, not a fence or another entity.
2. Keep them near you. The original search area is a **12 x 12 x 12 block box centered
   on the initiating player's position**, extending six blocks along each axis. This
   is not a 12-block radius; entity bounding boxes determine intersection.
3. Sneak and right-click the intended recipient player with your **main hand**. An
   empty main hand is a convenient way to avoid unrelated vanilla item interactions.

Every tameable animal in that box whose owner is you **and** whose leash holder is you
transfers. This includes wolves, cats, and parrots; ordinary leashed livestock, untamed
animals, other players' pets, and pets leashed elsewhere do not transfer.
The existing vanilla interaction result is not overridden.

For each transferred pet, seven heart particles appear around the pet and seven around
the recipient. The original action-bar texts are:

```text
<recipient> is now the owner of <pet>
<initiator> has transferred <pet> to you
```

Multiple transfers send these messages for each pet, so the last action-bar message may
replace earlier ones, as in upstream. Leads are reattached without dropping a lead item.
Taming and sitting state are not reset.

Ownership and the leash holder are saved through vanilla entity data. Leash restoration
still follows vanilla rules: a missing/offline holder can cause a lead to drop after
the normal loading timeout. Ownership does not depend on the recipient being online.

## Building

Install a **JDK 25** and set `JAVA_HOME` to that installation. The Java compilation
toolchain is pinned to 25 even if another JDK is your system default. The checked-in
Gradle wrapper includes its JAR and downloads checksum-verified **Gradle 9.6.0**.
The build uses stable **Fabric Loom 1.17.21** and unobfuscated Minecraft names, not Yarn.

```sh
./gradlew build
```

On Windows, run `gradlew.bat build` with `JAVA_HOME` pointing to JDK 25.
The first build requires internet access to download Gradle and dependencies.

The installable artifact is:

```text
build/libs/transferable_pets-[26.3]-1.0.6.jar
```

`build` also runs the headless server game tests. Run just that suite with
`./gradlew runGameTest`. Tests and their mod metadata live in `src/gametest/`, are not
included in the installable JAR, and use a separate world under `build/run/gameTest/`.

## Verification

The port was built with JDK 25. Seven mod-specific server game tests passed, covering:
multi-pet transfer across wolves/cats/parrots; owner and leash-holder restrictions;
main-hand, sneaking, and player-target guards; the original box dimensions; spectator
and logical-server guards; the vanilla interaction packet path; and vanilla ownership,
leash, taming, and sitting serialization/restoration. They also check unchanged
interaction results, original action-bar text, heart packet counts, and no dropped lead.

A separate production Fabric 26.3 dedicated server, containing only Fabric API and the
built mod JAR, reached readiness, answered a vanilla protocol status request, saved its
world, stopped cleanly, and started again successfully. This was not a client login test.

**Not performed:** interactive multiplayer testing with actual vanilla clients,
rendered leash/particle checks, or a transferred pet surviving a full multiplayer server
restart. No authenticated interactive client session was available. Game-test players
and save/load checks are automated server-side evidence, not substitutes for these
checks.

### Manual vanilla multiplayer checklist

1. Start a dedicated 26.3 server with only the two JARs listed above. Connect two players
   (A and B) using unmodified vanilla 26.3 profiles, with no Fabric installation or server
   resource pack. Confirm both reach the world without missing-mod or registry errors.
2. As A, tame and name at least two wolves and a cat; optionally add a parrot. Leash all
   of them to A and gather them within five blocks along each axis. Sneak and main-hand
   right-click B at normal interaction distance. Confirm **all** pets transfer, both
   players see the original action-bar messages, hearts appear, leads visibly attach
   to B for both clients, and no lead item is dropped.
3. Confirm B can order the transferred pets to sit/stand and A no longer can. As an
   operator, compare B's UUID (`/data get entity B UUID`) with a named pet's saved owner
   (`/data get entity @e[type=minecraft:wolf,name=PortTestWolf,limit=1] Owner`).
4. Prepare a pet owned by B but leashed to A, a pet owned by A but leashed to B or a
   fence, an unleaded pet owned by A, an untamed wolf, and a leashed cow. Repeat A's
   gesture toward B and confirm none of those ineligible animals changes ownership or
   leash holder. Include one eligible pet to confirm the scan skips ineligible pets
   rather than stopping at the first one.
5. With a fresh eligible pet, right-click B without sneaking; test an off-hand interaction
   without a qualifying main-hand gesture; and interact with a non-player. None should
   transfer pet ownership. Unrelated vanilla leash/item interactions still behave
   normally. Also verify a pet wholly beyond six blocks on one axis stays with A while
   an eligible pet inside the box transfers.
6. Have B transfer the eligible pets back to A using the same gesture. Verify all
   ownership and visible leash changes again, including after both clients reconnect.
7. Transfer again to B, record the named pets' `Owner` and `leash` data, and stop the
   server normally. Restart and reconnect. Confirm saved ownership is still B's and
   B retains sit/stand control. Check leash restoration if B is present while the pets
   load; if the holder is unavailable past vanilla's timeout, distinguish normal lead
   dropping from an ownership regression.

## Source, credit, and license

Original mod and author: [WinterWolfSV/Transferable_Pets](https://github.com/WinterWolfSV/Transferable_Pets).
This 26.3 port is maintained in [lnr0626/Transferable_Pets](https://github.com/lnr0626/Transferable_Pets).
Report fork-specific bugs on the [fork's issue tracker](https://github.com/lnr0626/Transferable_Pets/issues),
including versions, reproduction steps, and relevant server logs without credentials.

The mod remains licensed under **CC-BY-NC-4.0**. Preserve WinterWolfSV's author credit
and include the unchanged [LICENSE](LICENSE) when redistributing this non-commercial
derivative. The build also includes the license in the JAR.