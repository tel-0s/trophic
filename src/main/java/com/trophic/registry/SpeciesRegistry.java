package com.trophic.registry;

import com.trophic.Trophic;
import com.trophic.data.SpeciesLoader;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Central registry for all species definitions.
 * Species can be registered via JSON datapacks or programmatically through the API.
 */
public class SpeciesRegistry {
    private final Map<Identifier, SpeciesDefinition> species = new HashMap<>();
    private final Map<Identifier, Set<Identifier>> predatorMap = new HashMap<>();
    private final Map<Identifier, Set<Identifier>> preyMap = new HashMap<>();

    public SpeciesRegistry() {
    }

    /**
     * Registers a species definition.
     * 
     * @param definition the species definition to register
     */
    public void register(SpeciesDefinition definition) {
        Identifier id = definition.getEntityId();
        
        if (species.containsKey(id)) {
            Trophic.LOGGER.warn("Overwriting existing species definition for {}", id);
        }
        
        species.put(id, definition);
        
        // Build predator-prey relationship maps
        if (definition.getDiet() != null && definition.getDiet().prey() != null) {
            for (Identifier preyId : definition.getDiet().prey().keySet()) {
                // This species is a predator of preyId
                predatorMap.computeIfAbsent(preyId, k -> new HashSet<>()).add(id);
                // preyId is prey for this species
                preyMap.computeIfAbsent(id, k -> new HashSet<>()).add(preyId);
            }
        }
        
        Trophic.LOGGER.debug("Registered species: {} (trophic level {})", id, definition.getTrophicLevel());
    }

    /**
     * Gets a species definition by entity ID.
     * 
     * @param entityId the entity identifier
     * @return the species definition, or empty if not registered
     */
    public Optional<SpeciesDefinition> getSpecies(Identifier entityId) {
        return Optional.ofNullable(species.get(entityId));
    }

    /**
     * Gets all predators of a given species.
     * 
     * @param preyId the prey species
     * @return set of predator entity IDs
     */
    public Set<Identifier> getPredatorsOf(Identifier preyId) {
        return predatorMap.getOrDefault(preyId, Collections.emptySet());
    }

    /**
     * Gets all prey of a given species.
     * 
     * @param predatorId the predator species
     * @return set of prey entity IDs
     */
    public Set<Identifier> getPreyOf(Identifier predatorId) {
        return preyMap.getOrDefault(predatorId, Collections.emptySet());
    }

    /**
     * Checks if an entity has a registered species definition.
     * 
     * @param entityId the entity identifier
     * @return true if the entity is registered
     */
    public boolean isRegistered(Identifier entityId) {
        return species.containsKey(entityId);
    }

    /**
     * @return the total number of registered species
     */
    public int getSpeciesCount() {
        return species.size();
    }

    /**
     * @return all registered species definitions
     */
    public Collection<SpeciesDefinition> getAllSpecies() {
        return Collections.unmodifiableCollection(species.values());
    }

    /**
     * @return all registered entity IDs
     */
    public Set<Identifier> getAllEntityIds() {
        return Collections.unmodifiableSet(species.keySet());
    }

    /**
     * Gets species by trophic level.
     * 
     * @param level the trophic level
     * @return list of species at that level
     */
    public List<SpeciesDefinition> getSpeciesByTrophicLevel(int level) {
        return species.values().stream()
                .filter(s -> s.getTrophicLevel() == level)
                .toList();
    }

    /**
     * Loads default species definitions from the mod's built-in resources.
     */
    public void loadDefaultSpecies() {
        Trophic.LOGGER.info("Loading default species definitions...");
        SpeciesLoader.loadDefaultSpecies(this);
    }

    /**
     * Clears all registered species. Used for reloading.
     */
    public void clear() {
        species.clear();
        predatorMap.clear();
        preyMap.clear();
    }
}
