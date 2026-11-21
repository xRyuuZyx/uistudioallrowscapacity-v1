package id.roleplay.uistudioscreencapacityfix;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives UIStudio's existing uistudio:screen client message and stores the
 * maximum supported capacity value. UIStudio 2.2.5 calculates its BossBar
 * capacity from screenHeight / 3. Keeping this value at its maximum prevents
 * the server from discarding different numbers of rows in fullscreen and
 * windowed modes. The client mod then fits the received rows into the current
 * scaled GUI height by adapting the vanilla row spacing.
 *
 * This plugin does not register a new channel or packet type on the client.
 * It is an additional server-side listener for UIStudio's existing channel.
 */
public final class UIStudioScreenCapacityFix extends JavaPlugin implements PluginMessageListener {
    private static final String CHANNEL = "uistudio:screen";
    private static final int MAGIC = 1430868785; // protocol marker used by UIStudio 2.2.5
    private static final int MIN_WIDTH = 160;
    private static final int MAX_WIDTH = 8192;
    private static final int MIN_HEIGHT = 120;
    private static final int MAX_HEIGHT = 4320;

    private final Map<UUID, Integer> lastStoredHeight = new ConcurrentHashMap<>();
    private volatile Method setScreenHeightMethod;
    private volatile Object playerStore;

    @Override
    public void onEnable() {
        Plugin uiStudio = getServer().getPluginManager().getPlugin("UIStudio");
        if (uiStudio == null || !uiStudio.isEnabled()) {
            getLogger().warning("UIStudio was not found or is not enabled. The capacity sync will wait for packets.");
        }
        // A separate plugin may listen to the same incoming channel. UIStudio's
        // own listener remains registered and continues saving the player's width.
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getLogger().info("Listening for UIStudio screen dimensions on " + CHANNEL);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        lastStoredHeight.clear();
        playerStore = null;
        setScreenHeightMethod = null;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || player == null || message == null || message.length != 12) {
            return;
        }

        ByteBuffer input = ByteBuffer.wrap(message).order(ByteOrder.BIG_ENDIAN);
        if (input.getInt() != MAGIC) {
            return;
        }

        int width = input.getInt();
        int reportedHeight = input.getInt();
        if (width < MIN_WIDTH || width > MAX_WIDTH
                || reportedHeight < MIN_HEIGHT || reportedHeight > MAX_HEIGHT) {
            return;
        }

        // UIStudio 2.2.5 clamps screenHeight to 4320 and computes allowedRows
        // from screenHeight / 3. Store that maximum on every valid screen sync
        // so the server sends all configured BossBar rows regardless of whether
        // the client is fullscreen, maximized, or in a small window.
        // The client-side BossBarExtender adapts row spacing to the actual GUI.
        int capacityHeight = MAX_HEIGHT;
        UUID playerId = player.getUniqueId();
        Integer previous = lastStoredHeight.get(playerId);
        if (previous != null && previous == capacityHeight) {
            return;
        }

        try {
            Object store = getPlayerStore();
            Method setter = getScreenHeightMethod(store);
            setter.invoke(store, playerId, capacityHeight);
            lastStoredHeight.put(playerId, capacityHeight);
            getLogger().fine("Enabled full UIStudio row capacity for " + player.getName()
                    + ": GUI=" + width + "x" + reportedHeight
                    + ", UIStudio capacity-height=" + capacityHeight);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("Could not update UIStudio screen height for "
                    + player.getName() + ": " + exception.getMessage());
        }
    }

    private Object getPlayerStore() throws ReflectiveOperationException {
        Object cached = playerStore;
        if (cached != null) {
            return cached;
        }
        Plugin uiStudio = getServer().getPluginManager().getPlugin("UIStudio");
        if (uiStudio == null || !uiStudio.isEnabled()) {
            throw new IllegalStateException("UIStudio is not enabled");
        }
        Object loaded = uiStudio.getClass().getMethod("players").invoke(uiStudio);
        playerStore = loaded;
        return loaded;
    }

    private Method getScreenHeightMethod(Object store) throws NoSuchMethodException {
        Method cached = setScreenHeightMethod;
        if (cached != null) {
            return cached;
        }
        Method found = store.getClass().getMethod("setScreenHeight", UUID.class, int.class);
        setScreenHeightMethod = found;
        return found;
    }
}
