package com.youssgm3o8.smoothtp;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.potion.Effect;
import cn.nukkit.scheduler.TaskHandler;

import java.util.UUID;

/**
 * Manages the entire GTA-style teleportation animation process.
 * This class coordinates the sequence of animations and teleportation.
 */
public class TransmissionProcess {
    private final Main plugin;
    private final Player player;
    private final Location destination;
    private final VirtualEntity entity;
    private final UUID playerId;
    private TaskHandler animationTask;
    
    /**
     * Creates a new transmission process for a player
     * @param plugin The plugin instance
     * @param player The player to teleport
     * @param destination The destination to teleport to
     */
    public TransmissionProcess(Main plugin, Player player, Location destination) {
        this.plugin = plugin;
        this.player = player;
        this.destination = destination;
        this.playerId = player.getUniqueId();
        this.entity = new VirtualEntity(plugin, player);
        
        // Start the teleportation process
        start();
    }
    
    /**
     * Begins the teleportation sequence
     */
    private void start() {
        // Load animation durations from config
        int upDuration = plugin.getConfig().getInt("gta.up-duration", 40);
        int fadeInDuration = plugin.getConfig().getInt("gta.fade-in-duration", 20);
        int stayDuration = plugin.getConfig().getInt("gta.stay-duration", 20);
        int fadeOutDuration = plugin.getConfig().getInt("gta.fade-out-duration", 40);
        int downDuration = plugin.getConfig().getInt("gta.down-duration", 40);
        int downStayDuration = plugin.getConfig().getInt("gta.down-stay-duration", 20);
        double upOffset = plugin.getConfig().getDouble("gta.up-offset", 100.0);
        boolean playSound = plugin.getConfig().getBoolean("play-sound", true);
        boolean useTitle = plugin.getConfig().getString("animation-type", "GTA").contains("BOTH");
        
        if (plugin.isDebug()) {
            plugin.getLogger().info("Starting GTA teleport for " + player.getName());
            plugin.getLogger().info("From: " + player.getLocation() + " To: " + destination);
            plugin.getLogger().info("Up duration: " + upDuration + " ticks");
        }
        
        // Create upward and downward locations
        Location startLocation = player.getLocation().clone();
        
        Location upLocation = startLocation.clone();
        upLocation.y += upOffset;
        upLocation.pitch = 90; // Look down
        
        Location downLocation = destination.clone();
        downLocation.y += upOffset;
        downLocation.pitch = 90; // Look down
        
        try {
            // Spawn and attach the camera entity
            entity.spawn();
            entity.attachCamera();
            
            // Play teleport sound if enabled
            if (playSound) {
                playTeleportSound(startLocation);
            }
            
            // If using both effects, start fade out
            if (useTitle) {
                int fadeDuration = plugin.getConfig().getInt("title.fade-duration", 15);
                player.sendTitle("§0", "", 0, fadeDuration, 0);
            }
            
            // Create a comprehensive animation sequence that chains all movements
            runAnimationSequence(
                startLocation,
                upLocation,
                downLocation,
                destination,
                upDuration,
                fadeInDuration,
                stayDuration,
                fadeOutDuration,
                downDuration,
                downStayDuration,
                playSound,
                useTitle
            );
            
        } catch (Exception e) {
            // If anything goes wrong, clean up
            cleanup(e);
        }
    }
    
    /**
     * Runs the complete animation sequence with smooth transitions
     */
    private void runAnimationSequence(
            Location startLocation,
            Location upLocation,
            Location downLocation,
            Location finalDestination,
            int upDuration,
            int fadeInDuration,
            int stayDuration,
            int fadeOutDuration,
            int downDuration,
            int downStayDuration,
            boolean playSound,
            boolean useTitle
    ) {
        try {
            if (plugin.isDebug()) {
                plugin.getLogger().info("Stage 1: Moving up from " + startLocation + " to " + upLocation);
            }
            
            // Start animation with the entity moving upward
            animationTask = entity.move(upLocation, upDuration);
            
            // After moving up, continuously update camera during the delay
            for (int i = 0; i < fadeInDuration; i++) {
                final int tick = i;
                plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                    entity.updateCamera(upLocation);
                }, upDuration + tick);
            }
            
            // Stage 2: Move horizontally (x/z) after the delay
            plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                try {
                    if (plugin.isDebug()) {
                        plugin.getLogger().info("Stage 2: Moving horizontally from " + upLocation + " to " + downLocation);
                    }
                    
                    // Keep camera at the elevated position
                    entity.teleport(upLocation); // Ensure we're at the correct upLocation
                    animationTask = entity.move(downLocation, stayDuration);
                    
                    // After moving horizontally, continuously update camera during the delay
                    for (int i = 0; i < fadeOutDuration; i++) {
                        final int tick = i;
                        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                            entity.updateCamera(downLocation);
                        }, stayDuration + tick);
                    }
                    
                    // Stage 3: Teleport player and move downward
                    plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                        try {
                            if (plugin.isDebug()) {
                                plugin.getLogger().info("Stage 3: Actually teleporting player to " + finalDestination);
                            }
                            
                            // Apply no fall damage effect before teleporting
                            Effect noFall = Effect.getEffect(Effect.DAMAGE_RESISTANCE)
                                .setDuration(60)  // 3 seconds
                                .setAmplifier(4)  // Level 5 resistance (immunity)
                                .setVisible(false);  // Hide particles
                            player.addEffect(noFall);
                            
                            // Teleport the player to the destination (silently)
                            player.teleport(finalDestination);
                            
                            if (playSound) {
                                playTeleportSound(finalDestination);
                            }
                            
                            if (useTitle) {
                                // Fade in
                                int fadeDuration = plugin.getConfig().getInt("title.fade-duration", 15);
                                player.sendTitle("", "", 0, fadeDuration, 0);
                            }
                            
                            if (plugin.isDebug()) {
                                plugin.getLogger().info("Stage 4: Moving down from " + downLocation + " to " + finalDestination);
                            }
                            
                            // Ensure we're at the correct downLocation
                            entity.teleport(downLocation); 
                            animationTask = entity.move(finalDestination, downDuration);
                            
                            // After moving down, continuously update camera during the delay
                            for (int i = 0; i < downStayDuration; i++) {
                                final int tick = i;
                                plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                                    entity.updateCamera(finalDestination);
                                }, downDuration + tick);
                            }
                            
                            // Final stage: Cleanup
                            plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                                if (plugin.isDebug()) {
                                    plugin.getLogger().info("Final stage: Cleanup for " + player.getName());
                                }
                                
                                // Show teleport message if configured
                                String teleportMessage = plugin.getConfig().getString("teleport-message", "");
                                if (!teleportMessage.isEmpty()) {
                                    player.sendMessage(teleportMessage);
                                }
                                
                                cleanup(null);
                                
                            }, downDuration + downStayDuration);
                            
                        } catch (Exception e) {
                            cleanup(e);
                        }
                    }, stayDuration + fadeOutDuration);
                    
                } catch (Exception e) {
                    cleanup(e);
                }
            }, upDuration + fadeInDuration);
            
        } catch (Exception e) {
            cleanup(e);
        }
    }
    
    /**
     * Handles cleanup of the animation process
     */
    private void cleanup(Exception error) {
        try {
            if (error != null) {
                plugin.getLogger().error("Error during teleport animation: " + error.getMessage(), error);
                
                // Make sure player reaches destination
                player.teleport(destination);
            }
            
            // Cancel any ongoing animation
            if (animationTask != null) {
                animationTask.cancel();
                animationTask = null;
            }
            
            // Detach camera and remove entity
            entity.detachCamera();
            entity.remove();
            
            // Make sure to clean everything up
            plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                // Second attempt at cleanup to make sure entity is fully removed
                entity.remove();
                
                // IMPORTANT: Remove player from processing set
                plugin.removeProcessingPlayer(playerId);
                
                if (plugin.isDebug()) {
                    plugin.getLogger().info("Animation cleanup complete for " + player.getName());
                }
            }, 5); // Small delay to ensure clean removal
            
        } catch (Exception e) {
            plugin.getLogger().error("Error during cleanup: " + e.getMessage(), e);
            plugin.removeProcessingPlayer(playerId);
        }
    }
    
    /**
     * Plays the Enderman teleport sound at the specified location
     * @param location The location to play the sound at
     */
    private void playTeleportSound(Location location) {
        LevelEventPacket soundPacket = new LevelEventPacket();
        soundPacket.evid = LevelEventPacket.EVENT_SOUND_ENDERMAN_TELEPORT;
        soundPacket.x = (float) location.x;
        soundPacket.y = (float) location.y;
        soundPacket.z = (float) location.z;
        soundPacket.data = 0;
        player.dataPacket(soundPacket);
    }
} 