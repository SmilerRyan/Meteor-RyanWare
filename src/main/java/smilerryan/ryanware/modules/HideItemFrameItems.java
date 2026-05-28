package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack; // Added Import
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class HideItemFrameItems extends Module {

    public enum FilterMode {
        ItemAndName("Item And Name"), NameAndItem("Name And Item"),
        ItemOrName("Item Or Name"), NameOrItem("Name Or Item"),
        NameOnly("Name Only"), ItemOnly("Item Only"),
        BlockAll("Block All"), BlockNone("Block None");

        private final String name;
        FilterMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public enum ListMode { Whitelist, Blacklist }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItem = settings.createGroup("Item Filter");
    private final SettingGroup sgName = settings.createGroup("Name Filter");

    private final Setting<FilterMode> filterMode = sgGeneral.add(new EnumSetting.Builder<FilterMode>()
        .name("filter-mode").defaultValue(FilterMode.ItemAndName).build());

    private final Setting<Item> replacementItem = sgGeneral.add(new ItemSetting.Builder()
        .name("replacement-item").defaultValue(Items.BARRIER).build());

    private final Setting<String> customName = sgGeneral.add(new StringSetting.Builder()
        .name("custom-name")
        .description("Use %item% for original item name, %name% for original display name or & for color codes.")
        .defaultValue("&cHidden &7- &c%item%")
        .build());

    private final Setting<ListMode> itemMode = sgItem.add(new EnumSetting.Builder<ListMode>()
        .name("item-list-mode").defaultValue(ListMode.Whitelist).build());

    private final Setting<List<Item>> itemFilter = sgItem.add(new ItemListSetting.Builder()
        .name("item-filter").defaultValue(List.of(Items.FILLED_MAP)).build());

    private final Setting<ListMode> nameMode = sgName.add(new EnumSetting.Builder<ListMode>()
        .name("name-list-mode").defaultValue(ListMode.Blacklist).build());

    private final Setting<List<String>> nameFilter = sgName.add(new StringListSetting.Builder()
        .name("name-filter").defaultValue(List.of("safe")).build());

    private final Setting<Boolean> exactMatch = sgName.add(new BoolSetting.Builder()
        .name("exact-name-match").defaultValue(false).build());

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public HideItemFrameItems() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Hide-Item-Frame-Items", "Prevents rendering of items in item frames.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        Box box = mc.player.getBoundingBox().expand(64);
        for (ItemFrameEntity frame : mc.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true)) {
            ItemStack stackInFrame = frame.getHeldItemStack();
            Item heldItem = stackInFrame.getItem();
            String name = stackInFrame.getName().getString();
            
            if (heldItem == Items.AIR) continue;

            if (shouldHide(heldItem, name)) {
                ItemStack stack = replacementItem.get().getDefaultStack().copy();
                
                String processedName = customName.get()
                    //.replace("%item%", heldItem.getName().getString())
                    .replace("%name%", name)
                    .replace("&", "§");

                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(processedName));

                frame.setHeldItemStack(stack);
            }
        }
    }

    private boolean shouldHide(Item item, String name) {
        boolean itemRes;
        boolean nameRes;

        switch (filterMode.get()) {
            case BlockAll -> { return true; }
            case BlockNone -> { return false; }
            case ItemOnly -> { return checkItem(item); }
            case NameOnly -> { return checkName(name); }
            case ItemAndName -> { itemRes = checkItem(item); nameRes = checkName(name); return itemRes && nameRes; }
            case NameAndItem -> { nameRes = checkName(name); itemRes = checkItem(item); return nameRes && itemRes; }
            case ItemOrName -> { itemRes = checkItem(item); nameRes = checkName(name); return itemRes || nameRes; }
            case NameOrItem -> { nameRes = checkName(name); itemRes = checkItem(item); return nameRes || itemRes; }
            default -> { return false; }
        }
    }

    private boolean checkItem(Item item) {
        boolean contains = itemFilter.get().contains(item);
        return itemMode.get() == ListMode.Whitelist ? contains : !contains;
    }

    private boolean checkName(String name) {
        boolean match = exactMatch.get() 
            ? nameFilter.get().stream().anyMatch(n -> n.equalsIgnoreCase(name))
            : nameFilter.get().stream().anyMatch(n -> name.toLowerCase().contains(n.toLowerCase()));
        return nameMode.get() == ListMode.Whitelist ? match : !match;
    }
}