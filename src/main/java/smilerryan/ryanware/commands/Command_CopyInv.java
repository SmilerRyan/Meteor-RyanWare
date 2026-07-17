package smilerryan.ryanware.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import meteordevelopment.meteorclient.commands.Command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Command_CopyInv extends Command {

    private static final File DIR = new File("meteor-client/ryanware/copyInv");

    public Command_CopyInv() {
        super(
            smilerryan.ryanware.RyanWare.commandPrefix + "copyInv",
            "Save/load/delete inventory snapshots."
        );
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {

        builder.then(literal("save")
            .then(argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> suggestFiles(b))
                .executes(ctx -> {
                    save(StringArgumentType.getString(ctx, "name"));
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("load")
            .then(argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> suggestFiles(b))
                .executes(ctx -> {
                    load(StringArgumentType.getString(ctx, "name"));
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("delete")
            .then(argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> suggestFiles(b))
                .executes(ctx -> {
                    delete(StringArgumentType.getString(ctx, "name"));
                    return SINGLE_SUCCESS;
                })));
    }


    private CompletableFuture<Suggestions> suggestFiles(SuggestionsBuilder builder) {

        DIR.mkdirs();

        File[] files = DIR.listFiles((dir, name) -> name.endsWith(".nbt"));

        if (files != null) {
            for (File file : files) {
                String n = file.getName();
                builder.suggest(n.substring(0, n.length() - 4));
            }
        }

        return builder.buildFuture();
    }


    private void save(String name) {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) return;

        DIR.mkdirs();

        File file = new File(DIR, name + ".nbt");

        try {

            NbtCompound root = new NbtCompound();
            NbtList list = new NbtList();

            RegistryOps<NbtElement> ops = RegistryOps.of(
                NbtOps.INSTANCE,
                mc.player.getRegistryManager()
            );

            for (int i = 0; i < mc.player.getInventory().size(); i++) {

                ItemStack stack = mc.player.getInventory().getStack(i);

                NbtCompound slot = new NbtCompound();

                slot.putInt("Slot", i);

                ItemStack.CODEC.encodeStart(
                    ops,
                    stack
                ).result().ifPresent(nbt ->
                    slot.put("Item", nbt)
                );

                list.add(slot);
            }

            root.put("Inventory", list);

            NbtIo.write(root, file.toPath());

            mc.player.sendMessage(
                Text.of("§aSaved inventory as '" + name + "'"),
                false
            );

        } catch (IOException e) {

            mc.player.sendMessage(
                Text.of("§cFailed to save inventory."),
                false
            );
        }
    }


    private void load(String name) {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) return;


        File file = new File(DIR, name + ".nbt");


        if (!file.exists()) {

            mc.player.sendMessage(
                Text.of("§cInventory '" + name + "' does not exist."),
                false
            );

            return;
        }


        try {

            NbtCompound root = NbtIo.read(file.toPath());

            if (root == null) return;


            NbtList list = root.getList("Inventory")
                .orElse(new NbtList());


            RegistryOps<NbtElement> ops = RegistryOps.of(
                NbtOps.INSTANCE,
                mc.player.getRegistryManager()
            );


            mc.player.getInventory().clear();


            for (int i = 0; i < list.size(); i++) {

                NbtCompound slot = list.getCompound(i)
                    .orElse(new NbtCompound());


                int index = slot.getInt("Slot", -1);


                if (index < 0 || !slot.contains("Item"))
                    continue;


                NbtElement itemNbt = slot.get("Item");


                if (itemNbt == null)
                    continue;


                ItemStack stack = ItemStack.CODEC.parse(
                    ops,
                    itemNbt
                ).result().orElse(ItemStack.EMPTY);


                if (stack.isEmpty())
                    continue;


                mc.player.getInventory().setStack(index, stack);


                if (mc.player.getAbilities().creativeMode) {

                    mc.getNetworkHandler().sendPacket(
                        new CreativeInventoryActionC2SPacket(
                            index,
                            stack
                        )
                    );
                }
            }


            mc.player.getInventory().markDirty();
            mc.player.playerScreenHandler.sendContentUpdates();
            mc.player.currentScreenHandler.sendContentUpdates();


            mc.player.sendMessage(
                Text.of("§aLoaded inventory '" + name + "'"),
                false
            );


        } catch (IOException e) {

            mc.player.sendMessage(
                Text.of("§cFailed to load inventory."),
                false
            );

            e.printStackTrace();
        }
    }


    private void delete(String name) {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) return;


        File file = new File(DIR, name + ".nbt");


        if (file.delete()) {

            mc.player.sendMessage(
                Text.of("§aDeleted inventory '" + name + "'"),
                false
            );

        } else {

            mc.player.sendMessage(
                Text.of("§cFailed to delete inventory."),
                false
            );
        }
    }
}