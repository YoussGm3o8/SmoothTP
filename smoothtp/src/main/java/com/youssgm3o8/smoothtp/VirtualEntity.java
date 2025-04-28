package com.youssgm3o8.smoothtp;

import cn.nukkit.Player;
import cn.nukkit.entity.data.ByteEntityData;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.data.FloatEntityData;
import cn.nukkit.level.Location;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.MobEffectPacket;
import cn.nukkit.network.protocol.MoveEntityAbsolutePacket;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.protocol.RemoveEntityPacket;
import cn.nukkit.network.protocol.SetEntityDataPacket;
import cn.nukkit.potion.Effect;
import cn.nukkit.scheduler.TaskHandler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a virtual entity used for GTA-style teleportation animations.
 * This entity is only visible to the teleporting player and guides their camera movement.
 */
public class VirtualEntity {
    // Entity metadata constants
    private static final int DATA_FLAGS = 0;
    private static final int DATA_SCALE = 3;
    private static final int DATA_AIR = 7;
    private static final int DATA_NO_AI = 15;
    private static final int DATA_ALWAYS_SHOW_NAMETAG = 82;
    private static final int DATA_NAMETAG_VISIBLE = 4;
    private static final int DATA_TICKING_AREA_BOUND_X = 58;
    private static final int DATA_TICKING_AREA_BOUND_Y = 59;
    private static final int DATA_TICKING_AREA_BOUND_Z = 60;
    private static final int DATA_HEALTH = 7;
    private static final byte INVISIBLE_FLAG = 0x20;
    
    // Additional bit flags
    private static final int ENTITY_SILENT = 0x0400;
    private static final int ENTITY_NO_GRAVITY = 0x0800;
    
    // Entity type - using an entity that doesn't render properly (for our advantage)
    private static final int ENTITY_TYPE_AREA_EFFECT_CLOUD = 95; // This is essentially invisible
    
    // Effect IDs
    private static final int EFFECT_INVISIBILITY = 14;
    private static final int HIDE_PARTICLES_FLAG = 0x40;
    
    // Camera location offset from entity (entity is way below camera view)
    private static final double CAMERA_Y_OFFSET = 10000.0;

    private final Main plugin;
    private final Player player;
    private final long entityId;
    private Location location;
    private boolean removed = false;
    private TaskHandler currentMoveTask;

    /**
     * Creates a new virtual entity for the given player
     * @param plugin The plugin instance
     * @param player The player to create the entity for
     */
    public VirtualEntity(Main plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        // Use negative entity ID to avoid conflicts with real entities
        this.entityId = -player.getId() - 1000; // Adding an offset to ensure it doesn't conflict
        this.location = player.getLocation();
    }

    /**
     * Spawns the virtual entity and makes it invisible
     */
    public void spawn() {
        if (plugin.isDebug()) {
            plugin.getLogger().info("Spawning virtual entity with ID " + entityId + " for player " + player.getName());
        }
        
        // Position where we'll place the actual entity - far below the player's view
        Location entityLocation = location.clone();
        entityLocation.y -= CAMERA_Y_OFFSET; // Place it far below the player's view
        
        // Create entity spawn packet
        AddEntityPacket spawnPacket = new AddEntityPacket();
        spawnPacket.entityUniqueId = entityId;
        spawnPacket.entityRuntimeId = entityId;
        spawnPacket.type = ENTITY_TYPE_AREA_EFFECT_CLOUD; // Invisible effect cloud
        spawnPacket.x = (float) entityLocation.getX();
        spawnPacket.y = (float) entityLocation.getY();
        spawnPacket.z = (float) entityLocation.getZ();
        spawnPacket.speedX = 0;
        spawnPacket.speedY = 0;
        spawnPacket.speedZ = 0;
        spawnPacket.yaw = (float) entityLocation.getYaw();
        spawnPacket.pitch = (float) entityLocation.getPitch();
        player.dataPacket(spawnPacket);

        // Make the entity completely invisible
        SetEntityDataPacket dataPacket = new SetEntityDataPacket();
        dataPacket.eid = entityId;
        
        // Create comprehensive invisibility metadata
        int combinedFlags = INVISIBLE_FLAG | ENTITY_SILENT | ENTITY_NO_GRAVITY;
        
        EntityMetadata metadata = new EntityMetadata()
                .putByte(DATA_FLAGS, (byte)combinedFlags)  // Invisible, silent, no gravity
                .putFloat(DATA_SCALE, 0.0f)                // Scale to 0 (effectively invisible)
                .putByte(DATA_NO_AI, (byte)1)              // No AI/movement
                .putByte(DATA_ALWAYS_SHOW_NAMETAG, (byte)0) // Hide name tag
                .putByte(DATA_NAMETAG_VISIBLE, (byte)0)    // Hide name tag (alternative way)
                .putInt(DATA_HEALTH, 0);                   // Set health to 0 (makes some entities invisible)
        
        dataPacket.metadata = metadata;
        player.dataPacket(dataPacket);
        
        // Apply invisibility potion effect (Infinite duration: 1000000 ticks)
        applyInvisibilityEffect(true);
    }
    
    /**
     * Applies or removes invisibility effect
     * @param apply true to apply, false to remove
     */
    private void applyInvisibilityEffect(boolean apply) {
        MobEffectPacket effectPacket = new MobEffectPacket();
        effectPacket.eid = entityId;
        effectPacket.effectId = EFFECT_INVISIBILITY;
        effectPacket.amplifier = 1;  // Level 2 invisibility
        effectPacket.particles = false;  // No particles
        effectPacket.duration = 1000000;  // Very long duration
        
        // Apply or remove effect
        effectPacket.eventId = apply ? 
                MobEffectPacket.EVENT_ADD : 
                MobEffectPacket.EVENT_REMOVE;
                
        player.dataPacket(effectPacket);
    }

    /**
     * Teleports the virtual entity to a new location
     * @param location The location to teleport to
     */
    public void teleport(Location location) {
        if (removed) return;
        
        // Store the actual camera location
        this.location = location.clone(); 
        
        // The entity itself is positioned below the camera
        Location entityLocation = location.clone();
        entityLocation.y -= CAMERA_Y_OFFSET;

        // Move the entity below the view
        MoveEntityAbsolutePacket packet = new MoveEntityAbsolutePacket();
        packet.eid = entityId;
        packet.x = (float) entityLocation.getX();
        packet.y = (float) entityLocation.getY();
        packet.z = (float) entityLocation.getZ();
        packet.yaw = (float) entityLocation.getYaw();
        packet.pitch = (float) entityLocation.getPitch();
        packet.headYaw = (float) entityLocation.getYaw();
        packet.onGround = false;
        
        player.dataPacket(packet);
        
        // Also update the camera instantly to the actual view position
        updateCamera(location);
    }

    /**
     * Smoothly moves the entity to a target location over a specified duration
     * @param to The target location
     * @param duration The duration in ticks
     * @return The task handling the movement
     */
    public TaskHandler move(Location to, int duration) {
        if (removed) return null;
        
        if (plugin.isDebug()) {
            plugin.getLogger().info("Moving entity from " + location.toString() + " to " + to.toString() + " over " + duration + " ticks");
        }
        
        // Cancel any existing move task
        if (currentMoveTask != null && !currentMoveTask.isCancelled()) {
            currentMoveTask.cancel();
        }
        
        // Make sure duration is at least 1 tick
        final int finalDuration = Math.max(1, duration);
        
        // Clone locations to avoid reference issues - these are camera positions
        final Location startLoc = location.clone();
        final Location endLoc = to.clone();
        
        AtomicInteger durationCounter = new AtomicInteger(finalDuration);
        AtomicReference<TaskHandler> taskRef = new AtomicReference<>();

        // Create and store the task
        TaskHandler task = plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, () -> {
            if (removed || !player.isOnline()) {
                if (taskRef.get() != null) {
                    taskRef.get().cancel();
                }
                return;
            }
            
            try {
                // Calculate raw progress (0.0 to 1.0)
                float rawProgress = 1.0f - (float)durationCounter.get() / finalDuration;
                
                // Apply easing function for smoother movement with friction
                // Using cubic for extra friction
                float easedProgress = easeInOutCubic(rawProgress);
                
                // Interpolate between start and end positions
                double x = startLoc.x + (endLoc.x - startLoc.x) * easedProgress;
                double y = startLoc.y + (endLoc.y - startLoc.y) * easedProgress;
                double z = startLoc.z + (endLoc.z - startLoc.z) * easedProgress;
                
                // Interpolate rotation
                double yaw = startLoc.yaw + (endLoc.yaw - startLoc.yaw) * easedProgress;
                double pitch = startLoc.pitch + (endLoc.pitch - startLoc.pitch) * easedProgress;
                
                // Create interpolated camera location
                Location cameraPos = new Location(
                    x, y, z,
                    yaw, pitch,
                    startLoc.level  // Keep the same level
                );
                
                // Create entity location (far below camera)
                Location entityPos = cameraPos.clone();
                entityPos.y -= CAMERA_Y_OFFSET;
                
                // Update entity position (which is far below the camera)
                MoveEntityAbsolutePacket packet = new MoveEntityAbsolutePacket();
                packet.eid = entityId;
                packet.x = (float) entityPos.x;
                packet.y = (float) entityPos.y;
                packet.z = (float) entityPos.z;
                packet.yaw = (float) entityPos.yaw;
                packet.pitch = (float) entityPos.pitch;
                packet.headYaw = (float) entityPos.yaw;
                packet.onGround = false;
                
                player.dataPacket(packet);
                
                // Update local position (camera position)
                location = cameraPos;
                
                // Update camera position for player to follow entity
                updateCamera(cameraPos);
                
                // End task when duration is complete
                if (durationCounter.decrementAndGet() <= 0) {
                    // Make sure we reach the exact destination
                    teleport(endLoc);
                    
                    if (taskRef.get() != null) {
                        taskRef.get().cancel();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().error("Error moving entity: " + e.getMessage(), e);
                if (taskRef.get() != null) {
                    taskRef.get().cancel();
                }
            }
        }, 1);
        
        taskRef.set(task);
        currentMoveTask = task;
        return task;
    }
    
    /**
     * Easing function for smooth acceleration and deceleration
     * @param t Progress value from 0.0 to 1.0
     * @return Eased value
     */
    private float easeInOutQuad(float t) {
        return t < 0.5f ? 2.0f * t * t : 1.0f - (float)Math.pow(-2.0f * t + 2.0f, 2) / 2.0f;
    }
    
    /**
     * Easing function with even more friction
     * @param t Progress value from 0.0 to 1.0
     * @return Eased value with extra friction
     */
    private float easeInOutCubic(float t) {
        return t < 0.5f ? 4.0f * t * t * t : 1.0f - (float)Math.pow(-2.0f * t + 2.0f, 3) / 2.0f;
    }
    
    /**
     * Updates the player's camera to follow this entity
     */
    public void updateCamera() {
        updateCamera(location);
    }
    
    /**
     * Updates the player's camera to a specific location
     * @param loc The location to move the camera to
     */
    public void updateCamera(Location loc) {
        if (removed) return;
        
        // In Nukkit, we don't have a spectator mode, so we'll simulate it
        // by constantly updating the player's camera position
        MovePlayerPacket packet = new MovePlayerPacket();
        packet.eid = player.getId();
        packet.x = (float) loc.getX();
        packet.y = (float) loc.getY();
        packet.z = (float) loc.getZ();
        packet.yaw = (float) loc.getYaw();
        packet.pitch = (float) loc.getPitch();
        packet.headYaw = (float) loc.getYaw();
        packet.mode = MovePlayerPacket.MODE_TELEPORT;
        
        player.dataPacket(packet);
    }
    
    /**
     * Makes the player's camera follow this entity
     */
    public void attachCamera() {
        if (removed) return;
        
        if (plugin.isDebug()) {
            plugin.getLogger().info("Attaching camera for " + player.getName() + " to virtual entity");
        }
        
        // Since Nukkit doesn't have the camera API, we'll simulate it
        // by teleporting the player's view to the entity position
        updateCamera();
        
        // Make player temporarily invisible to others
        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.eid = player.getId();
        
        // Create metadata for the player
        EntityMetadata metadata = new EntityMetadata()
                .putByte(DATA_FLAGS, INVISIBLE_FLAG);
        packet.metadata = metadata;
        
        broadcastToOthers(packet);
    }
    
    /**
     * Restores the player's normal camera view
     */
    public void detachCamera() {
        if (plugin.isDebug()) {
            plugin.getLogger().info("Detaching camera for " + player.getName());
        }
        
        // Make player visible again
        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.eid = player.getId();
        
        // Create metadata for the player
        EntityMetadata metadata = new EntityMetadata()
                .putByte(DATA_FLAGS, (byte) 0x00);
        packet.metadata = metadata;
        
        broadcastToOthers(packet);
    }
    
    /**
     * Broadcasts a packet to all players except the owner of this entity
     */
    private void broadcastToOthers(DataPacket packet) {
        for (Player p : plugin.getServer().getOnlinePlayers().values()) {
            if (!p.equals(player)) {
                p.dataPacket(packet);
            }
        }
    }
    
    /**
     * Removes the virtual entity
     */
    public void remove() {
        if (removed) return;
        
        if (plugin.isDebug()) {
            plugin.getLogger().info("Removing virtual entity for " + player.getName());
        }
        
        removed = true;
        
        // Cancel any ongoing movement
        if (currentMoveTask != null && !currentMoveTask.isCancelled()) {
            currentMoveTask.cancel();
            currentMoveTask = null;
        }
        
        // Remove the invisibility effect
        applyInvisibilityEffect(false);
        
        // Send remove packet
        RemoveEntityPacket packet = new RemoveEntityPacket();
        packet.eid = entityId;
        player.dataPacket(packet);
    }
} 