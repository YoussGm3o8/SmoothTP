# SmoothTP

A Nukkit plugin that adds smooth animations to player teleportation events.

## Features

- Adds fade in/out animations when players teleport
- Customizable animation duration and effects
- Works with all teleportation methods except ender pearls and chorus fruit
- Configurable sound effects
- Low performance impact
- No commands to learn - works automatically

## Installation

1. Download the latest release from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. (Optional) Configure the plugin in the `plugins/SmoothTP/config.yml` file

## Configuration

The plugin creates a `config.yml` file in the `plugins/SmoothTP` directory with the following options:

```yaml
# Fade duration in ticks (20 ticks = 1 second)
# Recommended values: 10-40 (0.5-2 seconds)
fade-duration: 15

# Sound settings
play-sound: true

# Animation settings
# Possible values: TITLE, BLINDNESS, BOTH
animation-type: TITLE

# Debug mode (additional logging)
debug: false

# Message shown when a player teleports (leave empty for no message)
teleport-message: ""

# The delay (in ticks) between teleport and fade-in animation
fade-in-delay: 5
```

### Configuration Options Explained

- `fade-duration`: The duration of the fade effect in ticks (20 ticks = 1 second)
  - Higher values make teleportation slower but smoother
  - Lower values make teleportation faster but less smooth
  - Recommended range: 10-40 ticks (0.5-2 seconds)

- `play-sound`: Whether to play the teleport sound effect
  - `true`: Play enderman-like teleport sound at start and end
  - `false`: No sound effects

- `animation-type`: The type of animation to use
  - `TITLE`: Uses the title screen for fade effects (recommended)
  - `BLINDNESS`: Uses potion effects (not fully implemented)
  - `BOTH`: Uses both methods

- `debug`: Enables debug mode with additional logging
  - Useful for diagnosing teleportation issues

- `teleport-message`: A message to show to the player after teleporting (leave empty for no message)

- `fade-in-delay`: The delay in ticks between teleporting and starting the fade-in animation
  - Higher values give more time for the new area to load
  - Recommended range: 3-10 ticks

## Troubleshooting

If you experience any issues with the plugin, try these steps:

1. **Infinite teleportation loop** - This has been fixed in v1.1. Update to the latest version.
2. **No animation occurs** - Make sure you're using a supported teleport method (not ender pearl or chorus fruit).
3. **Screen stays black** - Try increasing the `fade-in-delay` value to give more time for the new area to load.
4. **Plugin conflicts** - Check for other teleportation or title screen plugins that might interfere.

## Compatibility

This plugin is compatible with Nukkit 1.0.0 and higher.

## Credits

- Developed by [YoussGm3o8](https://github.com/YoussGm3o8)
- Inspired by Cubecraft's teleport animations

## License

This project is licensed under the MIT License - see the LICENSE file for details. 