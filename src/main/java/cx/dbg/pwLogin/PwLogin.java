package cx.dbg.pwLogin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PwLogin extends JavaPlugin implements Listener {

    private final Set<UUID> authenticated = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().isConfigurationSection("users")) {
            getConfig().createSection("users");
            saveConfig();
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Starting pwLogin plugin...");
    }

    @Override
    public void onDisable() {
        authenticated.clear();
        getLogger().info("Manetti ist raus");
    }

    // ---- Helper methods -----------------------------------------------------

    private String keyFor(Player player) {
        return "users." + player.getName().toLowerCase();
    }

    private boolean hasPassword(Player player) {
        return getConfig().isSet(keyFor(player));
    }

    private String getStoredPassword(Player player) {
        return getConfig().getString(keyFor(player), null);
    }

    // SHA-1 hashing helper
    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Die Person von welcher ist das auf Stack overflow geklaut habe anschreien", e);
        }
    }

    private void setPassword(Player player, String password) {
        String hash = sha1(password);
        getConfig().set(keyFor(player), hash);
        saveConfig();
    }

    private boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId());
    }

    private void setAuthenticated(Player player, boolean value) {
        if (value) {
            authenticated.add(player.getUniqueId());
        } else {
            authenticated.remove(player.getUniqueId());
        }
    }

    private void sendAuthMessage(Player player, String msg) {
        player.sendMessage(ChatColor.GOLD + "[Auth] " + ChatColor.RESET + msg);
    }

    // ---- Commands -----------------------------------------------------------

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender,
                             org.bukkit.command.Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Versteh ich nicht");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        return switch (cmd) {
            case "login" -> {
                handleLogin(player, args);
                yield true;
            }
            case "register" -> {
                handleRegister(player, args);
                yield true;
            }
            default -> false;
        };
    }

    private void handleLogin(Player player, String[] args) {
        if (!hasPassword(player)) {
            sendAuthMessage(player, "Use /register <password> <password>.");
            return;
        }

        if (args.length < 1) {
            sendAuthMessage(player, "Usage: /login <password>");
            return;
        }

        String stored = getStoredPassword(player);   // SHA-1 hex string
        String providedHash = sha1(args[0]);         // hash of entered password

        if (stored != null && stored.equals(providedHash)) {
            setAuthenticated(player, true);
            sendAuthMessage(player, ChatColor.GREEN + "Login successful");
        } else {
            sendAuthMessage(player, ChatColor.RED + "Incorrect password");
        }
    }

    private void handleRegister(Player player, String[] args) {
        if (hasPassword(player)) {
            sendAuthMessage(player, "Bereits registriert");
            return;
        }

        if (args.length < 2) {
            sendAuthMessage(player, "Usage: /register <password> <password>");
            return;
        }

        String pass1 = args[0];
        String pass2 = args[1];

        if (!pass1.equals(pass2)) {
            sendAuthMessage(player, ChatColor.RED + "Passwords do not match");
            return;
        }

        setPassword(player, pass1);
        setAuthenticated(player, true);
        sendAuthMessage(player, ChatColor.GREEN + "Registration successful");
    }

    // ---- Events -------------------------------------------------------------

    private boolean shouldBlock(Player player) {
        return !isAuthenticated(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        setAuthenticated(player, false);

        if (hasPassword(player)) {
            sendAuthMessage(player, "Login with /login <password>");
        } else {
            sendAuthMessage(player, "Register with /register <password> <password>");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Drop auth on quit
        setAuthenticated(event.getPlayer(), false);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (shouldBlock(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (shouldBlock(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (shouldBlock(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (shouldBlock(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (shouldBlock(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityTargetLiving(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player) {
            if (!isAuthenticated(player)) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;

        // melee
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        }
        // Bow / crossbow
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null) {
            return;
        }
        if (shouldBlock(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player) {
            if (!isAuthenticated(player)) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isAuthenticated(player)) return;

        String msg = event.getMessage();
        if (!msg.startsWith("/")) {
            return; // ? Minecraft spaghetti code
        }

        String withoutSlash = msg.substring(1);
        String[] split = withoutSlash.split("\\s+");
        String baseCmd = split[0].toLowerCase();

        if (!baseCmd.equals("login") && !baseCmd.equals("register")) {
            event.setCancelled(true);
            sendAuthMessage(player, "Login erst");
        }
    }
}
