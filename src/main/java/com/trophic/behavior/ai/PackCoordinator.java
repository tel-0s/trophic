package com.trophic.behavior.ai;

import com.trophic.Trophic;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Manages pack/herd grouping and coordination for social animals.
 */
public class PackCoordinator {
    // Pack membership cache
    private static final Map<UUID, UUID> entityToPackLeader = new HashMap<>();
    private static final Map<UUID, Set<UUID>> packMembers = new HashMap<>();
    
    /**
     * Gets or assigns a pack for an animal.
     * Returns the pack leader's UUID.
     */
    public static UUID getOrAssignPack(AnimalEntity animal) {
        UUID animalId = animal.getUuid();
        
        // Check if already in a pack
        if (entityToPackLeader.containsKey(animalId)) {
            UUID leaderId = entityToPackLeader.get(animalId);
            // Validate leader still exists/is nearby
            if (isValidPackLeader(animal, leaderId)) {
                return leaderId;
            }
            // Leader invalid, need to reassign
            leaveCurrentPack(animalId);
        }
        
        // Find nearby pack or create new one
        return findOrCreatePack(animal);
    }
    
    /**
     * Finds a nearby pack to join or creates a new one.
     */
    private static UUID findOrCreatePack(AnimalEntity animal) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(animal.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        if (species == null || species.getSocial() == null || !species.getSocial().isSocial()) {
            // Not a social animal - it's its own "pack" of one
            entityToPackLeader.put(animal.getUuid(), animal.getUuid());
            packMembers.computeIfAbsent(animal.getUuid(), k -> new HashSet<>()).add(animal.getUuid());
            return animal.getUuid();
        }
        
        // Search for nearby same-species animals
        double searchRange = species.getSocial().territoryRadius();
        Box searchBox = animal.getBoundingBox().expand(searchRange);
        
        List<AnimalEntity> nearbyAnimals = animal.getEntityWorld().getEntitiesByClass(
                AnimalEntity.class,
                searchBox,
                other -> other != animal && 
                         other.getType() == animal.getType() &&
                         other.isAlive()
        );
        
        // Try to join an existing pack
        for (AnimalEntity other : nearbyAnimals) {
            UUID otherPackLeader = entityToPackLeader.get(other.getUuid());
            if (otherPackLeader != null) {
                Set<UUID> members = packMembers.get(otherPackLeader);
                if (members != null && members.size() < species.getSocial().maxPackSize()) {
                    // Join this pack
                    joinPack(animal.getUuid(), otherPackLeader);
                    return otherPackLeader;
                }
            }
        }
        
        // No pack to join - check if we can form a new pack with nearby animals
        List<AnimalEntity> packless = nearbyAnimals.stream()
                .filter(a -> !entityToPackLeader.containsKey(a.getUuid()))
                .limit(species.getSocial().minPackSize() - 1)
                .toList();
        
        if (packless.size() >= species.getSocial().minPackSize() - 1) {
            // Form a new pack with this animal as leader
            UUID leaderId = animal.getUuid();
            entityToPackLeader.put(leaderId, leaderId);
            Set<UUID> members = new HashSet<>();
            members.add(leaderId);
            
            for (AnimalEntity other : packless) {
                members.add(other.getUuid());
                entityToPackLeader.put(other.getUuid(), leaderId);
            }
            
            packMembers.put(leaderId, members);
            return leaderId;
        }
        
        // Can't form minimum pack - be a lone animal
        entityToPackLeader.put(animal.getUuid(), animal.getUuid());
        packMembers.computeIfAbsent(animal.getUuid(), k -> new HashSet<>()).add(animal.getUuid());
        return animal.getUuid();
    }
    
    /**
     * Joins an animal to an existing pack.
     */
    private static void joinPack(UUID animalId, UUID leaderId) {
        entityToPackLeader.put(animalId, leaderId);
        packMembers.computeIfAbsent(leaderId, k -> new HashSet<>()).add(animalId);
    }
    
    /**
     * Removes an animal from its current pack.
     */
    public static void leaveCurrentPack(UUID animalId) {
        UUID leaderId = entityToPackLeader.remove(animalId);
        if (leaderId != null) {
            Set<UUID> members = packMembers.get(leaderId);
            if (members != null) {
                members.remove(animalId);
                if (members.isEmpty()) {
                    packMembers.remove(leaderId);
                } else if (animalId.equals(leaderId)) {
                    // Leader left - assign new leader
                    UUID newLeader = members.iterator().next();
                    Set<UUID> updatedMembers = new HashSet<>(members);
                    packMembers.remove(leaderId);
                    packMembers.put(newLeader, updatedMembers);
                    
                    for (UUID memberId : updatedMembers) {
                        entityToPackLeader.put(memberId, newLeader);
                    }
                }
            }
        }
    }
    
    /**
     * Checks if a pack leader is still valid.
     */
    private static boolean isValidPackLeader(AnimalEntity animal, UUID leaderId) {
        Set<UUID> members = packMembers.get(leaderId);
        return members != null && !members.isEmpty();
    }
    
    /**
     * Gets the pack leader entity for an animal.
     */
    public static AnimalEntity getPackLeader(AnimalEntity animal) {
        UUID leaderId = entityToPackLeader.get(animal.getUuid());
        if (leaderId == null || leaderId.equals(animal.getUuid())) {
            return animal;
        }
        
        // Find leader entity
        double range = 64;
        Box searchBox = animal.getBoundingBox().expand(range);
        
        return animal.getEntityWorld().getEntitiesByClass(
                AnimalEntity.class,
                searchBox,
                other -> other.getUuid().equals(leaderId)
        ).stream().findFirst().orElse(animal);
    }
    
    /**
     * Gets all pack members for an animal's pack.
     */
    public static List<AnimalEntity> getPackMembers(AnimalEntity animal) {
        UUID leaderId = entityToPackLeader.get(animal.getUuid());
        if (leaderId == null) {
            return List.of(animal);
        }
        
        Set<UUID> memberIds = packMembers.get(leaderId);
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of(animal);
        }
        
        double range = 128;
        Box searchBox = animal.getBoundingBox().expand(range);
        
        return animal.getEntityWorld().getEntitiesByClass(
                AnimalEntity.class,
                searchBox,
                other -> memberIds.contains(other.getUuid())
        );
    }
    
    /**
     * Checks if an animal is the pack leader.
     */
    public static boolean isPackLeader(AnimalEntity animal) {
        UUID leaderId = entityToPackLeader.get(animal.getUuid());
        return leaderId != null && leaderId.equals(animal.getUuid());
    }
    
    /**
     * Gets the pack size for an animal.
     */
    public static int getPackSize(AnimalEntity animal) {
        UUID leaderId = entityToPackLeader.get(animal.getUuid());
        if (leaderId == null) {
            return 1;
        }
        Set<UUID> members = packMembers.get(leaderId);
        return members != null ? members.size() : 1;
    }
    
    /**
     * Calculates the center position of a pack.
     */
    public static Vec3d getPackCenter(AnimalEntity animal) {
        List<AnimalEntity> members = getPackMembers(animal);
        if (members.isEmpty()) {
            return new Vec3d(animal.getX(), animal.getY(), animal.getZ());
        }
        
        double x = 0, y = 0, z = 0;
        for (AnimalEntity member : members) {
            x += member.getX();
            y += member.getY();
            z += member.getZ();
        }
        
        return new Vec3d(x / members.size(), y / members.size(), z / members.size());
    }
    
    /**
     * Clean up pack data for removed entities.
     */
    public static void onEntityRemoved(UUID entityId) {
        leaveCurrentPack(entityId);
    }
}
