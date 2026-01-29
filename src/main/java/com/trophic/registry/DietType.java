package com.trophic.registry;

/**
 * Represents the dietary classification of a species.
 */
public enum DietType {
    /**
     * Feeds exclusively on plants and vegetation.
     */
    HERBIVORE,
    
    /**
     * Feeds exclusively on other animals.
     */
    CARNIVORE,
    
    /**
     * Feeds on both plants and animals.
     */
    OMNIVORE;
    
    /**
     * Parses a diet type from a string (case-insensitive).
     * 
     * @param value the string to parse
     * @return the corresponding DietType
     * @throws IllegalArgumentException if the value is not a valid diet type
     */
    public static DietType fromString(String value) {
        return switch (value.toLowerCase()) {
            case "herbivore" -> HERBIVORE;
            case "carnivore" -> CARNIVORE;
            case "omnivore" -> OMNIVORE;
            default -> throw new IllegalArgumentException("Unknown diet type: " + value);
        };
    }
    
    /**
     * @return true if this diet type can hunt prey
     */
    public boolean canHunt() {
        return this == CARNIVORE || this == OMNIVORE;
    }
    
    /**
     * @return true if this diet type can forage for plants
     */
    public boolean canForage() {
        return this == HERBIVORE || this == OMNIVORE;
    }
}
