package urza.bundlemapcopier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class CopyMapsPlugin extends JavaPlugin {
    private int maxCopies;
    private int mapsPerMap;
    private int feePerCopy;
    private Material feeMaterial;

    private static final InventoryPlan.Stacks<ItemStack> STACKS = new InventoryPlan.Stacks<>() {
        public ItemStack copy(ItemStack item) { return item.clone(); }
        public int amount(ItemStack item) { return item.getAmount(); }
        public ItemStack withAmount(ItemStack item, int amount) {
            ItemStack copy = item.clone();
            copy.setAmount(amount);
            return copy;
        }
    };

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            maxCopies = bounded("max-copies", 1, 64);
            mapsPerMap = bounded("empty-maps-per-map", 1, 64);
            feePerCopy = bounded("fee.per-copy", 0, 1_000_000);
            feeMaterial = Material.matchMaterial(getConfig().getString("fee.material", "EMERALD"));
            if (feeMaterial == null || !feeMaterial.isItem() || feeMaterial.isAir()) {
                throw new IllegalArgumentException("fee.material must name an item");
            }
            Objects.requireNonNull(getCommand("copymaps")).setExecutor(this);
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid CopyMaps config: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private int bounded(String path, int min, int max) {
        if (!getConfig().isInt(path)) throw new IllegalArgumentException(path + " must be an integer");
        int value = getConfig().getInt(path);
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + " must be between " + min + " and " + max);
        }
        return value;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can copy a held map bundle.");
            return true;
        }
        if (!player.hasPermission("saddle.copymaps.use")) return true;
        boolean preview = args.length > 0 && args[0].equalsIgnoreCase("preview");
        int offset = preview ? 1 : 0;
        int copies;
        try {
            if (args.length > offset + 1) throw new NumberFormatException();
            copies = args.length == offset ? 1 : Integer.parseInt(args[offset]);
            if (copies < 1 || copies > maxCopies) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            tell(player, "Use /copymaps [1-" + maxCopies + "] or /copymaps preview [copies].", false);
            return true;
        }
        final int requested = copies;
        // Read and change inventory only on the player's owning region, including on Folia.
        // No inventory snapshot is held across ticks: read the held bundle inside this task.
        player.getScheduler().run(this, task -> {
            if (player.hasPermission("saddle.copymaps.use")) copy(player, requested, preview);
        }, null);
        return true;
    }

    private void copy(Player player, int copies, boolean preview) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() != 1 || !(held.getItemMeta() instanceof BundleMeta source)
                || !source.hasItems()) {
            tell(player, "Hold one nonempty bundle containing only filled maps.", false);
            return;
        }
        List<ItemStack> maps = new ArrayList<>();
        long mapCount = 0;
        for (ItemStack item : source.getItems()) {
            if (item.getType() != Material.FILLED_MAP || item.getAmount() < 1
                    || !(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapId()) {
                tell(player, "The source bundle must contain only filled maps with map IDs.", false);
                return;
            }
            maps.add(item.clone());
            mapCount += item.getAmount();
        }
        if (mapCount < 1 || mapCount > 64) {
            tell(player, "The source bundle must contain between 1 and 64 maps.", false);
            return;
        }
        int emptyMaps = (int) mapCount * copies * mapsPerMap;
        int fee = copies * feePerCopy;
        String cost = copies + " empty bundles + " + emptyMaps + " empty maps"
                + (fee == 0 ? "" : " + " + fee + " " + feeMaterial.name().toLowerCase(Locale.ROOT));
        tell(player, (preview ? "Preview: " : "Cost: ") + copies + " sets ("
                + (mapCount * copies) + " maps). " + cost + ".", true);

        ItemStack[] original = player.getInventory().getStorageContents();
        // Normalize air slots to null; all live stacks are cloned by the planner.
        List<ItemStack> storage = new ArrayList<>(Arrays.asList(original));
        storage.replaceAll(item -> item == null || item.getType().isAir() ? null : item);
        InventoryPlan<ItemStack> plan = new InventoryPlan<>(storage, STACKS,
                player.getInventory().getHeldItemSlot());
        if (!plan.consume(item -> isPlain(item, Material.MAP), emptyMaps)) {
            tell(player, "Not enough ordinary empty maps in your main inventory. Nothing changed.", false);
            return;
        }
        if (!plan.consume(item -> isPlain(item, feeMaterial), fee)) {
            tell(player, "Not enough ordinary fee items after paying map costs. Nothing changed.", false);
            return;
        }
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < copies; i++) {
            ItemStack destination = plan.takeOne(this::isPlainEmptyBundle);
            if (destination == null) {
                tell(player, "Not enough ordinary empty bundles in your main inventory. Nothing changed.", false);
                return;
            }
            BundleMeta meta = (BundleMeta) destination.getItemMeta();
            meta.setItems(maps);
            destination.setItemMeta(meta);
            outputs.add(destination);
        }
        for (ItemStack output : outputs) {
            if (!plan.addUnstackable(output)) {
                tell(player, "Not enough space for the copied bundles. Nothing changed.", false);
                return;
            }
        }
        if (preview) {
            tell(player, "Materials and space are available. Run /copymaps " + copies
                    + " to copy the bundle you are holding then.", true);
            return;
        }
        // No live changes before this point; no async work or scheduling between read and commit.
        player.getInventory().setStorageContents(plan.result().toArray(ItemStack[]::new));
        tell(player, "Created " + copies + " map bundles. Your original is unchanged.", true);
    }

    private boolean isPlain(ItemStack item, Material type) {
        return item.getType() == type && item.isSimilar(new ItemStack(type));
    }

    private boolean isPlainEmptyBundle(ItemStack item) {
        return item.getItemMeta() instanceof BundleMeta meta && !meta.hasItems()
                && isPlain(item, item.getType());
    }

    private void tell(Player player, String message, boolean success) {
        player.sendMessage(Component.text(message,
                success ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
