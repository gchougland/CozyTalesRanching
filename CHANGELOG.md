# Changelog

All notable changes to Cozy Tales: Ranching are documented in this file.

## [0.3.0] - Unreleased

### Added

- **Nine new animals,** each with its own house, foods, habitat, behaviours, journal page and babies:
  - **Rabbits** in two coats (and a rare Snowy Rabbit) live in a **Rabbit Hutch**. They binky, dig, thump and wash their ears, and leave Rabbit Fluff.
  - **Mice** (and a rare Golden Mouse) live in a **Mouse Cottage** made from a wheel of cheese. They scurry, nibble and wash, and bring you Seed Pouches.
  - **Squirrels** live in a hollow-stump **Squirrel Drey**. They sit up to nibble, bury nuts and flick their tails, and gather Mixed Nuts.
  - **Crabs** live in a **Tide Pool** on the sand. They wave their claws, dig into the sand and blow bubbles, and leave Sea Shells.
  - **Camels** (and a rare Moonlit Camel) live in a striped **Caravan Shelter**. They kneel to chew and snort, love cactus flowers, and give milk.
  - **Goats** (and a rare Slate Goat) live in a **Goat Shed** with rocks to climb. They rear up on ledges and butt heads in play, and give milk and Goat Hair.
  - **Mosshorns** in two coats live in a **Mossy Lodge**. They graze moss, shake it off and rub their antlers on trees, and grow Moss Tufts.
  - **Turkeys** live in a **Turkey Roost**. They strut with their tails fanned and gobble, and drop Turkey Feathers.
  - **Ducks** live in a **Duck House** over a little pond. They dabble, preen and paddle, and lay Duck Eggs. Wild ducks fly, so befriend the ones that waddle up to visit your ranch near water.
- **New produce with real uses:**
  - **Rabbit Fluff** and **Goat Hair** spin into Cotton and Linen Scraps for the Loom and armour.
  - **Seed Pouch:** open it for three random crop seeds.
  - **Moss Tuft:** use it on a growing crop to grow it a stage, or craft mossy blocks and a moss rug.
  - **Turkey Feather:** fletch eight arrows at once instead of four.
  - **Sea Shell:** craft shell decorations.
  - **Mixed Nuts:** a snack, and Trail Mix for health and stamina on the go.
  - **Duck Egg:** fry it on the campfire, or use it in baking.
- **Truffle Omelette:** truffles finally have a use: a rare dish at the Cooking Bench with a duck egg (or two hen eggs) and cheese.

### Changed

- **Bunny kits:** Bunny parents now have Bunny kits.

### Fixed

- **Growing up:** Babies that are a small copy of their parents grow to full size when they grow up.
- **Frog Pond:** The pond water is see-through again.

## [0.2.0] - 10/24/2026

### Added

- **Land claims:** Animal houses inside a QuestLines Claims claim follow the claim's permissions. Trusted players can look after them, and the claim owner can always pick up a house placed in their claim. Turn it off with `ClaimsIntegration` in the config.
- **Spanish and Portuguese:** The mod is now translated into Spanish and Brazilian Portuguese.

### Changed

- **Following:** When you befriend or claim an animal, the message now says how to make it stay.

### Fixed

- **Lost animals:** Animals marked missing now really come back near their house or owner. Before, the return trip never found a spot to land, so they stayed lost.
- **Vanilla coops:** Ranch chickens no longer get taken into a vanilla chicken coop, which used to turn them into plain chickens with no name. Chickens that already went through one find their way back to their records when you are nearby. Admins can also run `/ranch admin relink` near them.
- **Riding:** Your animals keep following you while you ride a mount, and follow you again when you get off.
- **Toys:** Animals walk right up to a toy before they play with it, instead of playing from over a block away.

## [0.1.0] - 10/23/2026

### Added

- **Befriend wild animals:** Win over wild Cows, Chickens, Pigs, Sheep and Frogs by feeding them the food they like. Crouch and walk up slowly, or shy animals run off. Wild animals show a "befriend" prompt when you hold a food they eat.
- **Animals you already tamed:** Cows, Chickens, Pigs and Sheep tamed the vanilla way join the ranch. The first player to use one claims it.
- **Naming:** Give each new friend a name right after befriending it, or later from its house. Names show above their heads. Turn name tags off with `ShowNameplates` in the config.
- **Following:** Friends follow you home. Crouch and use an animal to make it stay or follow again.
- **Friendship:** Five hearts per animal, from petting once a day and from food. Favourite treats count for more. Friendship never goes down.
- **Animal houses:** A Chicken Coop, Cow Barn, Pigsty, Sheepfold and Frog Pond, crafted at the Farmer's bench. Animals live around their house by day and go inside to sleep.
- **House page:** See every resident's hearts, mood, traits and personality, move animals between houses, call them back, collect produce, rename the house and turn breeding on or off. Show area lights up the yard the house checks, and Check now looks again straight away.
- **Care:** Troughs feed animals nearby on their own. Each species likes its own ground, plants and decorations around its house, and happy animals do better at everything.
- **Feeds:** Hearty, Sweet, Breeder's and Premium feed give animals a boost for a while.
- **Breeding:** Two happy friends of the same kind who share a house may have a baby in the morning. Babies grow up in real time.
- **Traits:** Animals can carry up to two of nine traits, such as Bountiful, Swift Grower, Twinborn and Lucky. Babies can inherit them. Hover a trait on the house page to see what it does.
- **Personalities:** Every animal is Shy, Playful, Curious, Lazy or Affectionate, and it shows in what it likes to do.
- **Things animals do:** Cows graze and swish their tails, chickens peck and dust-bathe, pigs roll in the mud, sheep pronk, frogs hop on lily pads and croak. They nap, visit the trough, follow their parents and play with friends.
- **Toys:** A ball, scratching post, hanging bell, floating duck and log. Animals play with them and the toys move while they do.
- **Produce:** Eggs, milk, wool, truffles and more wait in each house for you to collect. Bring an empty bucket for milk.
- **Poop piles:** Animals leave the odd pile now and then. Piles next to each other merge and grow instead of covering the land. Break one for poop, or craft a pile to place yourself.
- **Jar of Crickets:** Frogs only eat crickets. Craft a jar from fibre and wheat, right-click a frog to offer it, or crouch and right-click to set the jar down.
- **Wildlife visitors:** A well kept ranch attracts visiting animals, and once in a while a rare one: a spotted pig, a black sheep, a golden chicken, a highland cow or a purple frog.
- **Ranch journal:** A book of every animal you have met, what it eats and likes, its traits and habits, a Traits page, your ranch memories and a short How to ranch guide. Craft it at the Workbench.
- **Journal settings:** Turn the mod's chat messages and sounds off for yourself, separately.
- **Rehoming:** Give an animal to another player, move it between your houses, or take it to the Butcher's Block.
- **Animal safety:** Your animals cannot be hurt by players or hostile mobs, and lost animals come back home.
- **For servers:** Blocked worlds, an animal limit per player, permission nodes for permission plugins, admin commands, and events other mods can listen to.
