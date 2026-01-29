# Trophic

A Minecraft mod (Fabric 1.21.11) that overhauls animal behaviour and interactions, introducing a complex ecological system with predator-prey relationships, food chains, and ecosystem dynamics.

## Features

### Ecological Simulation
- **Food Chain Dynamics**: Energy flows from vegetation through herbivores to carnivores with realistic efficiency losses
- **Population Control**: Carrying capacity limits based on available resources
- **Trophic Cascades**: Changes in predator populations affect prey, which affects vegetation

### Predator-Prey Interactions
- **Hunting Behavior**: Wolves, foxes, and ocelots actively hunt prey based on hunger
- **Stalking & Chasing**: Predators stalk prey before initiating a chase
- **Prey Detection**: Prey animals detect nearby predators and flee
- **Nutritional Value**: Different prey provides different amounts of sustenance

### Hunger & Metabolism
- All animals have a hunger system that drives behavior
- Higher trophic levels have faster metabolism
- Starvation occurs when hunger is critically low
- Well-fed animals are more likely to breed

### Social Behavior
- **Pack Behavior**: Wolves form packs and hunt cooperatively
- **Herd Behavior**: Sheep, cows, and other prey animals form protective herds
- **Territory System**: Predators claim and patrol territories

### Seasonal Cycles
- Four seasons affect animal behavior throughout the year
- **Spring**: Breeding season for most species, migration return
- **Summer**: Peak activity and territorial behavior
- **Autumn**: Migration departure, reduced breeding
- **Winter**: Minimal spawning, hibernation for some species

### Migration
- Animals migrate based on seasonal changes and food availability
- Habitat suitability affects migration destinations
- Population pressure can trigger dispersal

## Supported Animals

| Animal | Trophic Level | Diet | Behaviors |
|--------|---------------|------|-----------|
| Wolf | 3 | Carnivore | Pack hunting, territory |
| Fox | 3 | Omnivore | Solo hunting |
| Ocelot | 3 | Carnivore | Ambush predator |
| Polar Bear | 3 | Carnivore | Solitary |
| Rabbit | 2 | Herbivore | Fast fleeing, burrowing |
| Sheep | 2 | Herbivore | Herd behavior |
| Cow | 2 | Herbivore | Herd behavior |
| Pig | 2 | Omnivore | Foraging |
| Chicken | 2 | Omnivore | Fleeing |
| Goat | 2 | Herbivore | Mountain habitat |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download Trophic and place in your `mods` folder

## Configuration

Species can be customized via JSON datapacks in `data/trophic/species/`.

Example species definition:
```json
{
  "entity": "minecraft:wolf",
  "trophic_level": 3,
  "diet": {
    "type": "carnivore",
    "prey": {
      "minecraft:rabbit": { "preference": 0.5, "nutritional_value": 30 },
      "minecraft:sheep": { "preference": 0.3, "nutritional_value": 80 }
    },
    "hunt_cooldown": 6000
  },
  "habitat": {
    "preferred_biomes": ["minecraft:forest", "minecraft:taiga"],
    "temperature_tolerance": { "min": -15, "max": 25 }
  },
  "reproduction": {
    "breeding_season": { "start": 0.15, "end": 0.35 },
    "litter_size": { "min": 2, "max": 5 },
    "food_threshold": 0.6
  },
  "social": {
    "pack_animal": true,
    "pack_size": { "min": 3, "max": 8 },
    "territory_radius": 64
  }
}
```

## API for Other Mods

Trophic provides an API for other mods to register custom species:

```java
import com.trophic.api.TrophicAPI;

// Register a custom species
TrophicAPI.species("mymod:custom_predator")
    .trophicLevel(3)
    .carnivore()
    .prey("minecraft:rabbit", 0.5, 30)
    .prey("minecraft:chicken", 0.5, 20)
    .prefersBiomes("minecraft:forest")
    .packAnimal(2, 5)
    .territoryRadius(48)
    .breedingSeason(0.2, 0.4)
    .register();

// Query ecosystem state
SeasonManager.Season currentSeason = TrophicAPI.getCurrentSeason();
int wolfPopulation = TrophicAPI.getGlobalPopulation(Identifier.of("minecraft:wolf"));
```

## Building from Source

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Credits

Developed by the Trophic Team.
