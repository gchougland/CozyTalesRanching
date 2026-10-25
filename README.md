# Cozy Tales: Ranching

A cozy ranching mod for Hytale, part of the Cozy Tales series by Hexvane.

Befriend 40 kinds of animals, from cows, chickens and sheep to foxes, wolves, bears, owls, penguins, snails and the tetrabird, give them names and a home of their own, and watch your ranch grow. Keep them happy, raise their babies, collect what they make, and fill your journal with everything you learn along the way. Nothing ever starves and friendship never fades, so play at your own pace.

## Features

- **Befriend wild animals** by feeding them the food they like. Crouch and walk up slowly, or shy animals run off. Animals you tamed the vanilla way join the ranch too.
- **Tame predators:** foxes, wolves, bears, snow leopards and hawks are too wild to feed by hand. Calm them with a Bait Bowl first.
- **Name your friends** right after you befriend them. Their names show above their heads, and every look keeps its own name (a Raven, a Frost Snail, an Ice Bat).
- **A house for every animal:** 40 houses, from the Chicken Coop, Cow Barn and Pigsty to the Rookery, Penguin Igloo, Snail Terrarium, Bear Cave and Fossil Nest, crafted at the **Ranching Bench**. Upgrade the bench to unlock more. Animals spend the day around their house and go inside to sleep.
- **The house page** shows every resident's hearts, mood, traits and personality. Move animals between houses, call them back, collect produce, rename the house, turn breeding and wild visitors on or off, and light up the yard the house checks. Hover a need or want to see exactly what counts.
- **Friendship:** five hearts per animal, earned by petting once a day and by feeding. Favourite treats count for more.
- **Care:** troughs feed animals nearby on their own. Each species likes its own ground, plants, toys and decorations around its house, and happy animals do better at everything. Special feeds give a boost.
- **Game time:** houses work like furnaces and crops. Sleep through the night and wake up to full houses.
- **Breeding:** two happy friends of the same kind who share a house may have a baby in the morning.
- **Traits and personalities:** animals carry up to two of nine traits that babies can inherit, and each one is Shy, Playful, Curious, Lazy or Affectionate.
- **Lively animals:** each kind has its own habits. Crows admire shiny finds, bears roll on their backs, squirrels bury nuts, pigs roll in the mud and parrots dance. They nap, visit the trough, follow their parents and play with toys.
- **Produce:** eggs, milk, wool, feathers of every colour, truffles, honey, snail essences, fox trinkets and much more wait in each house, and every one of them has a use: foods, tonics, armour, crafting materials and decorations.
- **Wildlife visitors:** a well kept house attracts wild animals of its own kind, and now and then a rare one.
- **Rancher's Journal:** every animal you have met, what it likes, what its house needs, its looks, traits and habits, your ranch memories and a How to ranch guide.
- **Rancher's Ledger:** a book you place to see your whole ranch at a glance.
- **Rehoming:** give animals to friends, move them between your houses, or take them to the Butcher's Block.
- **Safe animals:** your animals cannot be hurt by players, hostile mobs, falls, fire or drowning, and lost animals find their way back.

## Getting started

1. Hold a food an animal likes (wheat or lettuce for cows, wheat or corn for chickens, and so on; the journal lists them). Crouch and walk up slowly, then press Use (F) to feed it a few times until it trusts you.
2. Give your new friend a name. It follows you from now on. To make it stay, crouch and use it.
3. Craft a **Ranching Bench** at the Workbench, then craft the animal's house there and place it. Walk your friends to the house and use it to move them in.
4. Put a **trough** with food in the yard and add the plants and decorations the house page asks for. **Show area** lights up the yard the house checks.
5. Pet each friend once a day, collect produce from the house page, and look for babies in the morning.
6. Craft the **Rancher's Journal** at the Workbench or the Ranching Bench to see everything you have learned. Its Settings page turns the mod's chat messages and sounds off for you.

Tip: in Creative mode animals only follow you if Allow NPC detection is on in your Creative settings.

Tip: predators bite. Fill a Bait Bowl with the bait they love, step back, and feed them by hand once they are calm.

Tip: frogs only eat crickets. Craft a **Jar of Crickets** at the Ranching Bench, then right-click a frog to offer it. Crouch and right-click to set the jar down.

Tip: rather not have wild visitors at a house? Turn **Wild visitors** off on its page.

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

- **Wildlife skin packs** such as Celly's Wildlife Skins: a tamed animal keeps the look it had in the wild, through houses, breeding and release.
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
