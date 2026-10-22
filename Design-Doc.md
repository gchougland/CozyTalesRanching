# Cozy Tales: Ranching — Complete Design and Development Prompt

I want to design and develop a new Hytale mod called **Cozy Tales: Ranching**, part of my Cozy Tales mod series.

The mod should overhaul animal taming, care, housing, breeding, and resource collection, taking significant inspiration from **Viva Piñata**, with elements of Stardew Valley and Animal Crossing.

The goal is to create a **cozy, intuitive, rewarding ranching experience without tedious micromanagement**. Players should form lasting relationships with their animals, build habitats that feel alive, attract and discover new species, and optionally explore deeper breeding and trait optimization.

The mod should be enjoyable for players who simply want a few animals around their home, while providing meaningful optional depth for players who want to create elaborate ranches.

## 1. Core Design Principles

Follow these principles throughout design and implementation:

- **Simplicity first:** Avoid complicated management systems, excessive menus, and unnecessary interactions.
- **Minimal micromanagement:** Routine animal care should largely happen automatically.
- **Meaningful companionship:** Animals should feel like permanent members of the player's homestead.
- **Creative habitat building:** Encourage players to create beautiful, functional habitats that animals actually interact with.
- **Optional depth:** Breeding optimization, rare traits, and advanced production should be available without being required.
- **Forgiving mechanics:** Players should not be punished for leaving their ranch unattended or taking breaks.
- **Reliable persistence:** Tamed animals must be recoverable and protected from accidental loss.
- **Mod compatibility:** Avoid interfering with unrelated vanilla or modded animal interactions.
- **Configurability:** Major features and individual species should be independently configurable.
- **Extensibility:** Make it straightforward to add new species, houses, traits, foods, and behaviors.

Prefer using existing Hytale systems and content where practical.

Do not introduce unnecessary complexity merely to add depth.

---

## 2. Animal Taming and Transportation

### Taming

Overhaul taming for explicitly supported animal species.

- Players befriend wild animals by offering appropriate food.
- Different species have preferred foods.
- Some animals may be more cautious or difficult to approach.
- Taming should generally require only a few interactions.
- Once befriended, animals become permanently owned.
- Players can name their animals.
- Newly tamed animals can follow their owner until assigned to a house.

Keep the initial taming process separate from the long-term friendship system.

Taming should be a short, enjoyable interaction rather than a lengthy progression system.

### Following and Transportation

Animal transportation must be extremely simple and reliable.

- Tamed animals can be instructed to follow their owner.
- Animals should navigate naturally whenever possible.
- If an animal gets stuck or falls too far behind, it should teleport near the owner.
- Prefer teleporting outside the player's view when practical.
- Teleportation must find safe positions and avoid hazards.
- Following should work across uneven terrain and long distances.
- Players should be able to stop following or send animals back home.
- Avoid transport crates, vehicles, complicated commands, or mandatory leads.

The intended experience is simply befriending an animal and walking home with it.

---

## 3. Animal Houses

**Animal houses are the central mechanic of Cozy Tales: Ranching.**

Each supported species should have a unique themed house inspired by Viva Piñata.

Examples:

| Animal | House |
|---|---|
| Chicken | Chicken Coop |
| Cow | Cow Barn |
| Pig | Pigsty |
| Sheep | Sheepfold |
| Rabbit | Rabbit Hutch |
| Frog | Frog Pond |

Each house supports a limited number of residents.

### Manual House Assignment

Animals must be manually assigned to houses.

This is important because house assignments determine which animals live together and potentially breed.

- Players choose which compatible house an animal belongs to.
- Assignment should require only a simple interaction.
- Houses display their residents and remaining capacity.
- Animals can be reassigned to different houses.
- Unassigned animals remain owned and can follow the player.
- Do not automatically assign animals merely because they wander near a house.
- Avoid complicated home-setting commands or management menus.

### House Behavior

Each house serves as its residents' home.

- Animals naturally wander around their house within a defined area.
- Animals return home at night.
- Animals enter their house to sleep.
- Sleeping animals may be safely unloaded while their persistent records remain stored.
- Animals emerge again in the morning.
- Houses automatically detect nearby food sources, habitat features, toys, and decorations.
- Houses store resident information and provide recovery functionality.
- Players can locate or resummon missing residents from their house.

Houses should be attractive decorative structures, not merely functional blocks.

### Houses Are Also Breeding Buildings

There should be no separate Love Shack or breeding building.

**Animal houses also serve as the location where breeding occurs.**

Compatible residents can breed when their friendship, happiness, and habitat requirements are satisfied.

Players control potential breeding pairs primarily through manual house assignments.

Houses should have a simple breeding enabled/disabled option.

---

## 4. Friendship and Happiness

Use two separate but simple systems.

### Friendship

Friendship represents the permanent bond between an animal and its owner.

Use a five-heart progression system.

Friendship increases through:

- Petting.
- Feeding.
- Offering favorite treats.
- Raising animals from birth.
- Other positive interactions.

Friendship should not decrease through ordinary neglect.

Higher friendship may provide:

- Improved resource production.
- Special affectionate behaviors.
- Increased breeding readiness.
- Small additional bonuses.

Petting should be rewarding but never become a mandatory daily chore.

### Happiness

Happiness represents an animal's current comfort.

It depends on:

- Food availability.
- Suitable habitat conditions.
- Preferred decorations.
- Toys and enrichment.
- Occasional player attention.

Unhappy animals should not die, permanently lose friendship, or disappear.

Low happiness may temporarily reduce production or prevent breeding.

Avoid complicated survival meters for hunger, thirst, cleanliness, disease, or similar mechanics.

---

## 5. Feeding and Crop Integration

### Feeding Troughs

Animals should primarily feed themselves through nearby troughs.

- Troughs hold multiple stacks and types of food.
- Compatible animals automatically consume food.
- Different species can share troughs.
- Animals should visually approach troughs and eat.
- Feeding must remain reliable even if pathfinding fails.
- Avoid requiring players to feed each animal individually.

### Food Preferences

Use three broad food categories:

1. Basic food that satisfies feeding needs.
2. Preferred food that improves happiness.
3. Favorite treats that provide additional friendship bonuses.

Individual animals may have different favorite treats.

This gives animals personality and encourages growing different crops.

### Optional Advanced Feed

Consider specialized feed crafted from existing crops:

- Hearty Feed: Keeps animals satisfied longer.
- Sweet Feed: Improves friendship gain.
- Breeder's Feed: Improves breeding readiness.
- Premium Feed: Slightly improves production.

These should be optional bonuses, not mandatory maintenance items.

---

## 6. Habitat Requirements and Preferences

Each species should have environmental requirements and preferences inspired by Viva Piñata.

Animal houses automatically evaluate the surrounding environment.

### Requirements

Simple conditions needed for maximum comfort or breeding eligibility.

### Preferences

Optional conditions that improve happiness, production, breeding, or unlock special behaviors.

Example species:

**Frogs**
- Require water and lily pads.
- Prefer reeds, tall grass, shade, and larger ponds.

**Pigs**
- Require appropriate food.
- Prefer mud, water, and vegetable crops.

**Chickens**
- Require food access.
- Prefer hay, grass, flowers, and dust-bathing areas.

**Rabbits**
- Require food and shelter.
- Prefer carrot patches, flowers, and hiding spots.

**Sheep**
- Require grass or feed.
- Prefer flowers, hay bales, and open pasture.

These are preliminary examples and should be adapted to Hytale's actual content.

### Habitat Design

- Requirements should be intuitive and achievable.
- Avoid demanding large quantities of specific blocks.
- Do not turn habitat building into a tedious checklist.
- Decorations should offer modest, meaningful bonuses.
- Players should be encouraged to experiment with different arrangements.
- Habitat satisfaction should be visible through the house interface.

The goal is to reward creative building rather than enforce rigid layouts.

---

## 7. Animal Behaviors and Environmental Interactions

Animals should actively interact with their environments.

Examples:

- Frogs jump onto lily pads and swim.
- Pigs roll around in mud.
- Chickens peck at the ground and dust-bathe.
- Sheep graze.
- Rabbits hide under bushes.
- Animals rest in shaded areas.
- Babies follow their parents.
- Animals socialize with other residents.
- Animals return home to sleep.

### Toys and Enrichment

Introduce optional toys that animals can interact with.

Examples:

- Balls that animals push around.
- Scratching posts and brushes.
- Tunnels for rabbits.
- Hanging toys for birds.
- Logs or platforms for climbing animals.
- Floating toys for aquatic animals.

Different species should prefer different toys.

Toys may improve happiness or unlock special interactions.

Some toys could support interactions between multiple animals.

Not every behavior needs a numerical reward. Some should exist purely because they are charming.

Environmental interactions should make the ranch feel alive.

---

## 8. Wildlife Attraction and Discovery

Introduce a Viva Piñata-inspired wildlife attraction system.

Suitable habitats can attract wild animals to visit the player's ranch.

This reduces the need to endlessly search the world for specific species.

### Attraction Conditions

Different species have different conditions.

Examples:

- Frogs are attracted by ponds and lily pads.
- Rabbits are attracted by vegetable gardens.
- Chickens may be attracted by grain or seeds.
- Other animals may require special plants, decorations, or habitat combinations.

### Rare Animals

Rare species should have more demanding attraction requirements.

These may involve:

- Rare crops.
- Unusual decorations.
- Special environmental features.
- Specific habitat combinations.
- Other animals already living nearby.

Rare attraction should encourage discovery and experimentation rather than excessive grinding.

### Visiting Animals

- Wild animals periodically visit suitable habitats.
- Visiting animals are not automatically owned.
- Players can befriend them through the normal taming system.
- Tamed visitors can be assigned to compatible houses.
- Attraction requirements should gradually become discoverable through the Rancher's Journal.
- Wildlife attraction should complement exploration rather than completely replace it.

The intended progression loop is:

**Build habitats → Attract wildlife → Befriend animals → Assign homes → Raise and breed animals → Discover traits and species → Expand the ranch.**

---

## 9. Breeding and Inherited Traits

Breeding should be straightforward for casual players while supporting optional optimization.

### Breeding Conditions

Two compatible animals can breed when:

- They live in the same house.
- They meet friendship and happiness requirements.
- Their habitat satisfies breeding conditions.
- The house can accommodate offspring.
- Any breeding cooldown has elapsed.

Animals voluntarily enter their house to breed.

A charming animation or visual effect can indicate breeding.

### Breeding Control

- Players control breeding primarily through manual house assignments.
- Houses have a breeding enabled/disabled toggle.
- Breeding stops when house capacity is reached.
- Optional pair selection may be provided if useful.
- Avoid complicated breeding menus.

### Offspring

- Babies inherit traits from their parents.
- Babies grow into adults over time.
- Good care may slightly accelerate growth.
- Offspring may inherit rare appearances or color variants.
- Animals raised from birth may begin with a small friendship bonus.

### Traits

Animals can possess simple inherited traits.

Examples:

| Trait | Effect |
|---|---|
| Bountiful | Increased resource production |
| Swift Grower | Faster maturation |
| Fertile | Shorter breeding cooldown |
| Affectionate | Faster friendship gain |
| Hardy | Slower happiness decline |
| Gourmet | Stronger favorite treat bonuses |
| Twinborn | Chance of twins |
| Efficient Eater | Reduced food consumption |
| Lucky | Chance of bonus resources |

Traits should have noticeable effects without becoming essential.

Use understandable inheritance rules, potentially including a small mutation chance.

Avoid complicated hidden genetic statistics.

### Personality

Consider personality traits separate from production traits.

Examples include shy, playful, curious, lazy, and affectionate.

Personality should primarily influence behavior, animations, and interactions.

---

## 10. Resource Production and Optional Butchering

Players should be able to obtain most ordinary animal resources without killing their animals.

Examples:

- Milk.
- Eggs.
- Wool.
- Feathers.
- Naturally shed materials.

Resource production can be influenced by happiness, friendship, and traits.

### Automatic Collection

Appropriate ranch structures should support automatic resource collection.

Examples:

- Chicken coops collect eggs.
- Milking stations collect milk.
- Shearing stations collect wool.

Prefer integrating collection into existing houses when practical rather than requiring excessive additional structures.

### Optional Butchering

Players should have the option to process livestock for meat, hide, and other ordinary drops.

Introduce a simple Butcher's Block or similar workstation.

- Butchering is entirely optional.
- Use a non-graphic interaction.
- Adult ranch animals provide significantly more resources than wild animals.
- Happiness increases yields.
- Relevant traits may improve yields further.
- Well-raised animals should provide enough materials that players do not need to kill large numbers.
- Butchering should not negatively affect nearby animals.
- Named or highly bonded animals should have confirmation or protection against accidental processing.

Support both lifelong companion ranching and livestock farming without forcing either playstyle.

---

## 11. Animal Persistence and Deduplication

**Animal persistence is one of the most important technical requirements of the mod.**

Tamed animals must never be permanently lost due to ordinary despawning, chunk unloading, server restarts, pathfinding failures, or other common world simulation issues.

### Stable Animal Identity

Each tamed animal must have a persistent identity independent of its current world entity.

Store:

- Stable animal ID.
- Most recently registered entity UUID.
- Owner.
- Species.
- Name.
- Friendship and happiness.
- Personality and inherited traits.
- Parentage and breeding history.
- Assigned house.
- Other necessary persistent state.

The stable animal ID must remain unchanged when the physical entity is recreated.

### House-Based Persistence

Assigned houses maintain records of their residents.

- Animals entering houses can transition from active entities to stored residents.
- Animals emerge when appropriate.
- Missing residents can be resummoned.
- Automatic recovery may occur when animals have been missing for an extended period.
- Resident records survive chunk unloading and server restarts.

Unassigned tamed animals must also have persistent records.

### UUID Tracking and Deduplication

**Deduplication must be designed extremely carefully to prevent multiple copies of an animal from existing.**

Use a stable animal ID as the animal's true identity and track the UUID of its latest physical entity.

Proposed behavior:

1. Every tamed animal receives a permanent stable animal ID.
2. The persistent record stores the most recent entity UUID.
3. When an animal is resummoned or recreated, the record is updated with the new entity UUID.
4. If an older entity later loads or is detected, compare its stable animal ID and UUID against the authoritative record.
5. If its entity UUID differs from the currently registered UUID, it is a stale incarnation and should be safely removed.
6. Only the currently registered incarnation may participate in gameplay.

Consider using a persistent generation or revision counter in addition to UUID tracking.

### Critical Safeguards

- Prevent stale entities from overwriting newer persistent records.
- Handle duplicates in previously unloaded chunks.
- Avoid duplicate spawning during simultaneous house loading, chunk loading, and resummoning.
- Never generate item drops when removing stale duplicates.
- Prevent stale entities from breeding, producing resources, or modifying persistent animal data.
- Use atomic updates or appropriate synchronization for entity registrations.
- Do not assume all duplicates can be detected immediately.
- Handle crashes and failures during resummoning.
- Ensure house destruction or reassignment cannot orphan animal records.
- Preserve animal records across world saves and restarts.
- Use safe spawn placement to prevent animals appearing inside blocks or hazards.

**The authoritative persistent animal record must always take precedence over outdated world entities.**

The implementation must account for delayed chunk loading, unexpected save ordering, and server crashes.

Deduplication must only apply to animals explicitly managed by Cozy Tales: Ranching.

Never remove unrelated entities owned by other mods.

---

## 12. Rancher's Journal

Introduce an illustrated in-game book called the **Rancher's Journal**.

It should record animal information, discoveries, and memories.

The presentation should feel charming and collectible, inspired by Viva Piñata's encyclopedia.

### Species Entries

Each supported species has an entry containing:

- Name and illustration.
- Description.
- Food preferences.
- Favorite treats.
- Habitat requirements.
- Habitat preferences.
- Wildlife attraction conditions.
- Breeding requirements.
- Resource products.
- Possible traits.
- Special behaviors.

### Progressive Discovery

Information unlocks as players interact with animals.

Examples:

- First encounter reveals a basic entry.
- Feeding reveals dietary information.
- Taming reveals care information.
- Housing reveals habitat preferences.
- Observing behaviors unlocks behavior descriptions.
- Breeding reveals breeding information.
- Discovering rare traits or variants adds additional entries.

Unknown information can appear as question marks or undiscovered sections.

Essential information should not be frustratingly difficult to obtain.

### Memories

Record milestones such as:

- First animal befriended.
- First animal house built.
- First successful breeding.
- First rare variant discovered.
- First animal reaching maximum friendship.
- First special behavior observed.
- First successful wildlife attraction.

These should feel like memories of the player's ranching journey rather than mandatory achievements.

---

## 13. Animal House Interface

Keep the house UI simple, attractive, and intuitive.

Display:

- House name and species.
- Resident count and capacity.
- Resident names.
- Friendship hearts.
- Happiness.
- Important traits.
- Habitat satisfaction.
- Breeding status.

### Actions

Players should be able to:

- Assign or remove residents.
- Locate or resummon animals.
- Enable or disable breeding.
- Inspect habitat conditions.
- Access relevant Rancher's Journal information.

Avoid complicated management panels.

Most routine functionality should be automatic once animals have been assigned.

---

## 14. Quality-of-Life Features

### Animal Safety

- Prevent accidental attacks against owned animals where practical.
- Protect animals from permanent loss due to ordinary hazards.
- Consider configurable protection against hostile mobs.
- Provide recovery for missing animals.
- Protect named animals against accidental butchering or release.

### Rehoming

- Allow animal ownership transfers between players.
- Allow reassignment between compatible houses.
- Allow animals to be released back into the wild.
- Preserve persistent animal data during transfers and reassignment.
- Avoid requiring physical transportation between houses on the same ranch.

### Population Control

- Respect house capacity limits.
- Provide simple breeding toggles.
- Prevent breeding when offspring cannot be accommodated.
- Avoid requiring manual management of breeding cooldowns.

### Performance

- Avoid continuous expensive habitat scans.
- Cache habitat information and update periodically or when relevant blocks change.
- Use efficient spatial queries for decorations, troughs, toys, and environmental