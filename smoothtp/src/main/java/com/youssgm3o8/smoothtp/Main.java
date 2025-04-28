package com.youssgm3o8.smoothtp;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.level.Location;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.SetTitlePacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.Config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Main extends PluginBase implements Listener {

    private final Map<UUID, TaskHandler> teleportTasks = new HashMap<>();
    // Track players that are currently being processed for teleportation
    private final Set<UUID> processingTeleport = new HashSet<>();
    private int fadeDuration;
    private int fadeInDelay;
    private boolean playSound;
    private String animationType;
    private boolean debug;
    private String teleportMessage;
    
    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Load configuration
        loadConfig();
        
        // Register event listener
        this.getServer().getPluginManager().registerEvents(this, this);
        
        // Log plugin enabled
        this.getLogger().info("SmoothTP has been enabled! Enjoy smooth teleportation experiences.");
        
        // Debug info
        if (debug) {
            this.getLogger().info("Debug mode enabled");
            this.getLogger().info("Animation type: " + animationType);
            this.getLogger().info("Play sound: " + playSound);
            
            if (animationType.equals("TITLE") || animationType.equals("BOTH")) {
                this.getLogger().info("Title fade duration: " + fadeDuration + " ticks");
                this.getLogger().info("Title fade-in delay: " + fadeInDelay + " ticks");
            }
            
            if (animationType.equals("GTA") || animationType.equals("BOTH")) {
                this.getLogger().info("GTA up duration: " + getConfig().getInt("gta.up-duration") + " ticks");
                this.getLogger().info("GTA up offset: " + getConfig().getDouble("gta.up-offset") + " blocks");
            }
        }
    }
    
    private void loadConfig() {
        Config config = getConfig();
        
        // Load settings from config
        fadeDuration = config.getInt("title.fade-duration", 15);
        fadeInDelay = config.getInt("title.fade-in-delay", 5);
        playSound = config.getBoolean("play-sound", true);
        animationType = config.getString("animation-type", "GTA").toUpperCase();
        debug = config.getBoolean("debug", false);
        teleportMessage = config.getString("teleport-message", "");
        
        // Validate animation type
        if (!animationType.equals("TITLE") && !animationType.equals("GTA") && !animationType.equals("BOTH")) {
            this.getLogger().warning("Invalid animation type: " + animationType + ". Defaulting to GTA.");
            animationType = "GTA";
        }
        
        // Validate fade duration
        if (fadeDuration < 1) {
            this.getLogger().warning("Invalid fade duration: " + fadeDuration + ". Defaulting to 15 ticks.");
            fadeDuration = 15;
        }
        
        // Validate fade-in delay
        if (fadeInDelay < 0) {
            this.getLogger().warning("Invalid fade-in delay: " + fadeInDelay + ". Defaulting to 5 ticks.");
            fadeInDelay = 5;
        }
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // Skip if player is already being processed
        if (isProcessingTeleport(player.getUniqueId())) {
            return;
        }
        
        // Skip for specific teleport causes
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
            event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }
        
        // Check if this is a cross-world teleport
        boolean isCrossWorld = !from.getLevel().getName().equals(to.getLevel().getName());
        
        try {
            // Add player to processing list
            addProcessingPlayer(player.getUniqueId());
            
            // For cross-world teleports, just use instant teleportation
            if (isCrossWorld) {
                // Use default teleportation for cross-world teleports
                player.teleport(to);
                removeProcessingPlayer(player.getUniqueId());
            } else {
                // Cancel the original teleport event
                event.setCancelled(true);
                
                // Use normal GTA-style animation for same-world teleports
                new TransmissionProcess(this, player, to);
            }
        } catch (Exception e) {
            getLogger().error("Error starting teleport animation: " + e.getMessage());
            // Clean up and do direct teleport if animation fails
            removeProcessingPlayer(player.getUniqueId());
            player.teleport(to);
        }
    }
    
    private void startFadeOut(Player player) {
        // Play teleport sound if enabled
        if (playSound) {
            LevelEventPacket soundPacket = new LevelEventPacket();
            soundPacket.evid = LevelEventPacket.EVENT_SOUND_ENDERMAN_TELEPORT;
            soundPacket.x = (float) player.getX();
            soundPacket.y = (float) player.getY();
            soundPacket.z = (float) player.getZ();
            soundPacket.data = 0;
            player.dataPacket(soundPacket);
        }
        
        // Use sendTitle for fade out effect if using title animation
        if (animationType.equals("TITLE") || animationType.equals("BOTH")) {
            // Send a title with a black screen. '§0' is the Minecraft color code for black.
            player.sendTitle("§0", "", 0, fadeDuration, 0);
        }
    }
    
    private void startFadeIn(Player player) {
        // Use sendTitle for fade in effect if using title animation
        if (animationType.equals("TITLE") || animationType.equals("BOTH")) {
            // Clear the black screen by sending an empty title
            player.sendTitle("", "", 0, fadeDuration, 0);
        }
        
        // Play teleport sound at new location if enabled
        if (playSound) {
            LevelEventPacket soundPacket = new LevelEventPacket();
            soundPacket.evid = LevelEventPacket.EVENT_SOUND_ENDERMAN_TELEPORT;
            soundPacket.x = (float) player.getX();
            soundPacket.y = (float) player.getY();
            soundPacket.z = (float) player.getZ();
            soundPacket.data = 0;
            player.dataPacket(soundPacket);
        }
    }
    
    /**
     * Check if debug mode is enabled
     * @return true if debug mode is enabled
     */
    public boolean isDebug() {
        return debug;
    }
    
    @Override
    public void onDisable() {
        // Cancel all pending teleport tasks
        for (TaskHandler task : teleportTasks.values()) {
            task.cancel();
        }
        teleportTasks.clear();
        processingTeleport.clear();
        
        this.getLogger().info("SmoothTP has been disabled.");
    }
    
    /**
     * Removes a player from the processing set
     * @param playerId The UUID of the player to remove
     */
    public void removeProcessingPlayer(UUID playerId) {
        if (processingTeleport.contains(playerId)) {
            processingTeleport.remove(playerId);
            if (debug) {
                this.getLogger().info("Removed player " + playerId + " from processing list");
            }
        }
    }
    
    /**
     * Checks if a player is currently being processed for teleportation
     * @param playerId The UUID of the player to check
     * @return true if the player is being processed, false otherwise
     */
    public boolean isProcessingTeleport(UUID playerId) {
        return processingTeleport.contains(playerId);
    }
    
    /**
     * Adds a player to the processing set
     * @param playerId The UUID of the player to add
     */
    public void addProcessingPlayer(UUID playerId) {
        processingTeleport.add(playerId);
        if (debug) {
            this.getLogger().info("Added player " + playerId + " to processing list");
        }
    }
} 