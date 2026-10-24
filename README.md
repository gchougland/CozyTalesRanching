# Cozy Tales: Ranching

A cozy ranching mod for Hytale, part of the Cozy Tales series by Hexvane.

Befriend wild Cows, Chickens, Pigs, Sheep, Frogs, Rabbits, Mice, Squirrels, Crabs, Camels, Goats, Mosshorns, Turkeys and Ducks, give them names and a home, and watch your ranch grow. Keep them happy, raise their babies, collect what they make, and fill your journal with everything you learn along the way. Nothing ever starves and friendship never fades, so play at your own pace.

## Features

- **Befriend wild animals** by feeding them the food they like. Crouch and walk up slowly, or shy animals run off. Animals you tamed the vanilla way join the ranch too.
- **Name your friends** right after you befriend them. Their names show above their heads.
- **Animal houses:** a Chicken Coop, Cow Barn, Pigsty, Sheepfold, Frog Pond, Rabbit Hutch, Mouse Cottage, Squirrel Drey, Tide Pool, Caravan Shelter, Goat Shed, Mossy Lodge, Turkey Roost and Duck House. Animals spend the day around their house and go inside to sleep.
- **The house page** shows every resident's hearts, mood, traits and personality. Move animals between houses, call them back, collect produce, rename the house, turn breeding on or off, and light up the yard the house checks.
- **Friendship:** five hearts per animal, earned by petting once a day and by feeding. Favourite treats count for more.
- **Care:** troughs feed animals nearby on their own. Each species likes its own ground, plants and decorations around its house, and happy animals do better at everything.
- **Breeding:** two happy friends of the same kind who share a house may have a baby in the morning.
- **Traits and personalities:** animals carry up to two of nine traits that babies can inherit, and each one is Shy, Playful, Curious, Lazy or Affectionate.
- **Lively animals:** cows graze, chickens dust-bathe, pigs roll in the mud, sheep pronk and frogs hop on lily pads. They nap, visit the trough, follow their parents and play with toys, and the toys move while they play.
- **Produce:** eggs, milk, wool, truffles, fluff, nuts, shells, feathers, moss and more wait in each house for you to collect, and every one of them has a use: new foods like the Truffle Omelette and Trail Mix, seed pouches, crop-growing moss, crafting scraps and decorations.
- **Wildlife visitors:** a well kept ranch attracts visiting animals, and now and then a rare one.
- **Ranch journal:** every animal you have met, what it likes, its traits and habits, your ranch memories and a short How to ranch guide.
- **Rehoming:** give animals to friends, move them between your houses, or take them to the Butcher's Block.
- **Safe animals:** your animals cannot be hurt by players or hostile mobs, and lost animals find their way back.

## Getting started

1. Hold a food an animal likes (wheat or lettuce for cows, wheat or corn for chickens, and so on; the journal lists them). Crouch and walk up slowly, then press Use (F) to feed it a few times until it trusts you.
2. Give your new friend a name. It follows you from now on. To make it stay, crouch and use it.
3. Craft its house at the **Farmer's bench** and place it. Walk your friends to the house and use it to move them in.
4. Put a **trough** with food in the yard and add the plants and decorations the house page asks for. **Show area** lights up the yard the house checks.
5. Pet each friend once a day, collect produce from the house page, and look for babies in the morning.
6. Craft the **Ranch Journal** at the Workbench to see everything you have learned. Its Settings page turns the mod's chat messages and sounds off for you.

Tip: in Creative mode animals only follow you if Allow NPC detection is on in your Creative settings.

Tip: frogs only eat crickets. Craft a **Jar of Crickets** from fibre and wheat, then right-click a frog to offer it. Crouch and right-click to set the jar down.

## Commands

| Command | What it does |
| --- | --- |
| `/ranch animals` | List your animals |
| `/ranch name <name>` | Name the animal you are looking at |
| `/ranch follow` / `/ranch stay` | Make the animal you look at (or all of yours nearby) follow or stay |
| `/ranch release` | Let the animal you look at go back to the wild |
| `/ranch give`, `/ranch accept`, `/ranch decline` | Give an animal to another player |
| `/ranch move` | Move an animal to another of your houses |
| `/ranch journal` | Open your journal |

`/cozyranch` works too. Admins also get `/ranch reload` and `/ranch admin` tools.

## For server owners

Settings live in `RanchConfig.json` in the mod's data folder (written on first start; `/ranch reload` re-reads it).

| Setting | Default | Meaning |
| --- | --- | --- |
| `TamingBlockedWorlds` | none | Worlds where animals cannot be befriended or claimed |
| `MaxAnimalsPerPlayer` | 64 | How many animals one player can keep |
| `DisabledSpecies` | none | Species to leave out, e.g. `frog` |
| `AdoptVanillaTamed` | true | Animals tamed the vanilla way join the ranch |
| `ProtectOwnedAnimals` | true | Players cannot hurt owned animals; lost animals come back |
| `ProtectFromHostileMobs` | true | Hostile mobs cannot hurt owned animals |
| `BreedingEnabled` | true | Turn breeding off server wide |
| `ShowNameplates` | true | Names above animals' heads |
| `ClaimsIntegration` | true | Houses follow QuestLines Claims permissions |
| `HomeRangeRadius`, `TroughRange`, `HabitatScanRadius` | 10, 8, 12 | How far animals roam, troughs reach and houses look |

Permission nodes start with `cozyranch.` (for example `cozyranch.tame`, `cozyranch.house.place`, `cozyranch.breed`, `cozyranch.butcher`, `cozyranch.transfer`). Player nodes are granted to Adventurers by default; admin nodes (`cozyranch.admin.*`) to nobody. Permission plugins such as LuckPerms can override them.

## Compatibility

- **QuestLines Claims:** inside a claim, animal houses follow the claim's permissions. Trusted players can look after them, and the claim owner can always remove a house.
- **Other pet and mount mods:** the mod only touches its own animals and the vanilla livestock it adopts, and leaves other mods' pets, familiars and mounts alone. Your animals keep following you while you ride.
- **Other mods** can listen to the mod's events (befriended, bred, produced, moved and more) without depending on it.

## Languages

English, Spanish and Brazilian Portuguese.

## Building from source

Java 25 and Hytale installed through the launcher.

```bat
.\gradlew.bat build
.\gradlew.bat runServer
```

The jar is `build\libs\CozyTalesRanching-<version>.jar`.

Metrics go to [hstats.dev](https://hstats.dev) only when `COZYTALESRANCHING_HSTATS_MOD_UUID` (or `hstats_mod_uuid` in `gradle.properties`) is set at build time. Only change the package name in `HStats.java`, never its metrics logic, per the HStats license.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.
