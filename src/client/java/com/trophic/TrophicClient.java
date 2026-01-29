package com.trophic;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side initialization for Trophic.
 * 
 * Currently minimal as Trophic focuses on server-side simulation,
 * but can be extended for client-side rendering or UI features.
 */
public class TrophicClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        Trophic.LOGGER.info("Trophic client initialized");
    }
}
