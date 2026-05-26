# Dams's Vanilla+ `DVPlus`

DVPlus is a Paper plugin that expands vanilla Minecraft with survival-friendly mechanics, atmospheric systems, and a few utility upgrades that fit the base game rather than replacing it.

## Features

### Luminous Equipment
Turn armor and tools into portable light sources using the Smithing Table.

- Supported items: helmets, chestplates, leggings, boots, swords, pickaxes, axes, shovels, and hoes
- Recipe: `Glow Item Frame` as the template, the gear item as the base, and `Glow Ink Sac` as the addition
- Effect: the item gains a `Luminous` lore line and emits client-side light around the player
- The light is rendered with block updates only, so the world itself is not altered
- The glow decays over time and players get a reminder while it is still active

### Messenger Parrots
Send written messages through your tamed parrots.

- Use a `Writable Book` on your own tamed parrot
- Then run `/parrot deliver <PlayerName>`
- The parrot flies to the target, delivers the message, and can return to the sender

### Campfire Purification
Convert rotten flesh into leather using a campfire.

- Right-click a `Campfire` or `Soul Campfire` with `Rotten Flesh`
- The flesh is consumed
- After 2 minutes, a `Leather` item drops at the campfire
- Each campfire can hold up to 5 pieces at once

### Stonecutter Sharpening
Temporarily boost your weapons and tools at a durability cost.

- Right-click a `Stonecutter` while holding a pickaxe, axe, sword, shovel, or hoe
- Cost: `50` durability
- Pickaxes grant `Haste I`
- Swords grant `Strength I`
- Duration: 10 minutes

### Frost-Bound Cauldrons
Cold biomes can turn water-filled cauldrons into ice rewards overnight.

- Track placed cauldrons automatically
- In cold conditions, a `Water Cauldron` has a chance to freeze
- When it succeeds, the cauldron becomes a `Cauldron` and drops `Blue Ice`

### Lunar Harvesting
The moon affects crops and undead mobs.

- Full moon: crops have a chance to grow an extra stage
- Full moon: undead mobs spawn with reduced health
- Dark moon: undead mobs spawn stronger, tankier, and with combat buffs
- Dark moon: undead mobs also drop extra loot

### Hitch Mechanic
Let horses pull cargo in a more vanilla-feeling way.

- Right-click a tamed horse while holding a `Lead`
- Hitch it to a nearby `Boat` or `Minecart`
- Right-click the cargo again with a lead to unhitch
- The cargo is dragged behind the horse while hitched

### Kinetic Grid
A chunk-based electricity system built around copper conductivity and lightning rods.

- Lightning rods can charge a chunk with natural lightning
- Trident strikes can also charge a chunk
- The grid tracks battery values per chunk
- Copper blocks and copper grates act as conductive materials
- Nearby connected chunks can inherit the source battery link
- Action-bar feedback appears when a chunk is charged

### GCG Automation
The grid powers automated chunk behavior.

- Monster spawns can be countered with `Evoker Fangs` when a chunk has charge
- Crop growth can be accelerated when a chunk has power and a copper grate is nearby
- Right-clicking a `Gold Block` gives a grid monitor readout
- `/gcg mode <0-4>` controls the chunk mode

### Kinetic Trampoline
Use charged copper and slime structures as a boost launcher.

- Build around `Slime Block` and `Sticky Piston`
- Copper conductivity in the structure affects the launch strength
- A charged grid can improve the trampoline's copper cap
- Launch behavior depends on piston orientation and player movement

### Subterranean Sonar
A utility item for cave exploration.

- Use an `Echo Shard` below Y 30
- Charge it by sneaking
- While charging, an action bar shows progress
- When released, it highlights nearby monsters and valuable ores
- The shard has limited uses and can shatter when spent

### Dynamic Lighting
Players wearing luminous gear get a temporary personal light source.

- Updates are handled client-side
- Works with main hand, off-hand, and armor slots
- Light is removed when the luminous item expires or is unequipped

## Commands

- `/parrot deliver <PlayerName>`
- `/gcg mode <0-4>`

## Notes

- Built for Paper
- Target version in `app/build.gradle`: `1.21.11-R0.1-SNAPSHOT`
- Java target: `21`

## License

DVPlus is licensed under the GNU Affero General Public License v3.0.
If you run a modified version of this software as a service, you must provide access to the source code of your modifications.

License file: [LICENSE](LICENSE)

## Developer

`fxllingstar`
