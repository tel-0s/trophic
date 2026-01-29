package com.trophic.behavior;

/**
 * Interface for entities that participate in the ecological simulation.
 * Implemented by AnimalEntity via mixin.
 */
public interface EcologicalEntity {
    
    /**
     * Gets the entity's current hunger level (0.0 = starving, 1.0 = full).
     */
    double trophic_getHunger();
    
    /**
     * Sets the entity's hunger level.
     * @param hunger value between 0.0 and 1.0
     */
    void trophic_setHunger(double hunger);
    
    /**
     * Feeds the entity, restoring hunger based on nutritional value.
     * @param nutritionalValue the nutritional value of the food
     */
    void trophic_feed(int nutritionalValue);
    
    /**
     * @return true if the entity is hungry (hunger < 0.5)
     */
    boolean trophic_isHungry();
    
    /**
     * @return true if the entity is starving (hunger < 0.2)
     */
    boolean trophic_isStarving();
    
    /**
     * Gets the remaining hunt cooldown in ticks.
     */
    int trophic_getHuntCooldown();
    
    /**
     * Sets the hunt cooldown.
     * @param ticks cooldown duration in ticks
     */
    void trophic_setHuntCooldown(int ticks);
    
    /**
     * @return true if the entity can currently hunt
     */
    boolean trophic_canHunt();
    
    /**
     * Gets the world time when the entity last ate.
     */
    long trophic_getLastMealTick();
}
