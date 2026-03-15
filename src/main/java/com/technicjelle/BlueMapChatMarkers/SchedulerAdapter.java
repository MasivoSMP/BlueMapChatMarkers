package com.technicjelle.BlueMapChatMarkers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;

final class SchedulerAdapter {
	private final JavaPlugin plugin;

	private final Method getGlobalRegionSchedulerMethod;
	private final Method globalRunMethod;
	private final Method globalRunDelayedMethod;

	private final Method getEntitySchedulerMethod;
	private final Method entityRunMethod;

	SchedulerAdapter(JavaPlugin plugin) {
		this.plugin = plugin;

		Method globalSchedulerGetter = null;
		Method globalSchedulerRun = null;
		Method globalSchedulerRunDelayed = null;
		try {
			globalSchedulerGetter = Bukkit.class.getMethod("getGlobalRegionScheduler");
			globalSchedulerRun = globalSchedulerGetter.getReturnType()
					.getMethod("run", Plugin.class, Consumer.class);
			globalSchedulerRunDelayed = globalSchedulerGetter.getReturnType()
					.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
		} catch (ReflectiveOperationException ignored) {
		}

		Method entitySchedulerGetter = null;
		Method entitySchedulerRun = null;
		try {
			entitySchedulerGetter = Player.class.getMethod("getScheduler");
			entitySchedulerRun = entitySchedulerGetter.getReturnType()
					.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
		} catch (ReflectiveOperationException ignored) {
		}

		this.getGlobalRegionSchedulerMethod = globalSchedulerGetter;
		this.globalRunMethod = globalSchedulerRun;
		this.globalRunDelayedMethod = globalSchedulerRunDelayed;
		this.getEntitySchedulerMethod = entitySchedulerGetter;
		this.entityRunMethod = entitySchedulerRun;
	}

	void runGlobal(Runnable task) {
		if (getGlobalRegionSchedulerMethod != null && globalRunMethod != null) {
			try {
				final Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
				globalRunMethod.invoke(globalScheduler, plugin, (Consumer<Object>) ignored -> task.run());
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				plugin.getLogger().log(Level.WARNING, "Failed to schedule task on the global scheduler", e);
				return;
			}
		}

		Bukkit.getScheduler().runTask(plugin, task);
	}

	void runOnPlayer(Player player, Runnable task) {
		if (getEntitySchedulerMethod != null && entityRunMethod != null) {
			try {
				final Object entityScheduler = getEntitySchedulerMethod.invoke(player);
				entityRunMethod.invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> task.run(), null);
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				plugin.getLogger().log(Level.WARNING, "Failed to schedule player task on the entity scheduler", e);
				return;
			}
		}

		Bukkit.getScheduler().runTask(plugin, task);
	}

	void runLaterGlobal(Runnable task, long delayTicks) {
		if (getGlobalRegionSchedulerMethod != null && globalRunDelayedMethod != null) {
			try {
				final Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
				globalRunDelayedMethod.invoke(globalScheduler, plugin, (Consumer<Object>) ignored -> task.run(), Math.max(1L, delayTicks));
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				plugin.getLogger().log(Level.WARNING, "Failed to schedule delayed task on the global scheduler", e);
				return;
			}
		}

		Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
	}
}
