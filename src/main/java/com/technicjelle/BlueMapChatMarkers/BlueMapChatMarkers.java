package com.technicjelle.BlueMapChatMarkers;

import com.technicjelle.BMUtils.BMCopy;
import com.technicjelle.MCUtils.ConfigUtils;
import com.technicjelle.UpdateChecker;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class BlueMapChatMarkers extends JavaPlugin implements Listener {
	private Config config;
	private UpdateChecker updateChecker;
	private SchedulerAdapter schedulerAdapter;

	@Override
	public void onEnable() {
		new Metrics(this, 16424);
		schedulerAdapter = new SchedulerAdapter(this);

		updateChecker = new UpdateChecker("TechnicJelle", "BlueMapChatMarkers", getDescription().getVersion());
		updateChecker.checkAsync();

		getServer().getPluginManager().registerEvents(this, this);

		BlueMapAPI.onEnable(onEnableListener);
	}

	Consumer<BlueMapAPI> onEnableListener = api -> {
		updateChecker.logUpdateMessage(getLogger());

		config = new Config(this);

		final String styleFile = "textStyle.css";
		try {
			ConfigUtils.copyPluginResourceToConfigDir(this, styleFile, styleFile, false);
			BMCopy.fileToWebApp(api, getDataFolder().toPath().resolve(styleFile), styleFile, true);
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "Failed to copy " + styleFile + " to BlueMap", e);
		}
	};

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		if (config == null) return;
		if (event.isCancelled() && !config.getForceful()) return;
		final Player player = event.getPlayer();
		final String message = ChatColor.stripColor(event.getMessage());

		schedulerAdapter.runOnPlayer(player, () -> {
			final World world = player.getWorld();
			final Location location = player.getLocation();
			final String playerName = player.getName();

			schedulerAdapter.runGlobal(() -> createMarker(player.getUniqueId(), playerName, world, location, message));
		});
	}

	private void createMarker(UUID playerId, String playerName, World world, Location location, String message) {
		final BlueMapAPI api = BlueMapAPI.getInstance().orElse(null);
		if (api == null) return; //BlueMap not loaded, ignore

		final BlueMapWorld bmWorld = api.getWorld(world).orElse(null);
		if (bmWorld == null) return; //world not loaded in BlueMap, ignore

		if (!api.getWebApp().getPlayerVisibility(playerId)) return; //player hidden on BlueMap, ignore

		final HtmlMarker marker = HtmlMarker.builder()
				.label(playerName + ": " + message)
				.position(location.getX(), location.getY() + 1.8, location.getZ()) // +1.8 to put the marker at the player's head level
				.styleClasses("chat-marker")
				.html(message)
				.build();
		final String key = "chat-marker_" + UUID.randomUUID();

		//for all BlueMap Maps belonging to the BlueMap World the Player is in, add the Marker to the MarkerSet of that BlueMap World
		bmWorld.getMaps().forEach(map -> {
			// get marker-set of map (or create new marker set if none found)
			final MarkerSet markerSet = map.getMarkerSets().computeIfAbsent(Config.MARKER_SET_ID, id -> MarkerSet.builder()
					.label(config.getMarkerSetName())
					.toggleable(config.isToggleable())
					.defaultHidden(config.isDefaultHidden())
					.build());

			//add Marker to the MarkerSet
			markerSet.put(key, marker);

			//wait Seconds and remove the Marker
			schedulerAdapter.runLaterGlobal(() -> markerSet.remove(key), config.getMarkerDuration() * 20L);
		});
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
		BlueMapAPI.unregisterListener(onEnableListener);
	}
}
