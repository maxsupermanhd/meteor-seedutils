/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package flexcoral.seedutils;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.seedfinding.mccore.rand.seed.StructureSeed;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.Feature;
import com.seedfinding.mcfeature.decorator.Decorator;
import com.seedfinding.mcfeature.loot.LootChest;
import com.seedfinding.mcfeature.loot.LootGenerator;
import com.seedfinding.mcfeature.structure.*;
import flexcoral.seedutils.screens.LongsViewScreen;
import flexcoral.seedutils.screens.StringPickScreen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.StringNbtWriter;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.ReloadableRegistries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.World;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SeedUtilsTab extends Tab {
    public SeedUtilsTab() {
        super("SeedUtils");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new SeedUtilsScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof SeedUtilsScreen;
    }

    private static class SeedUtilsScreen extends WindowTabScreen {
        public SeedUtilsScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
        }

        private pageType currentPage = pageType.Lifting;
        private enum pageType {
            Lifting("Lifting"),
            StructureSets("Structure sets"),
            Utilities("Utilities"),
            HashedSeeds("Hashed seeds");

            private final String labelText;
            pageType(String lifting) {
                labelText = lifting;
            }
            @Override
            public String toString() {
                return labelText;
            }
        }

        private RegistryKey<LootTable> lootSearchSelectedLootTable = LootTables.DESERT_PYRAMID_CHEST;
        private long lootSearchSelectedWorldSeed = 123;
        private BlockPos.Mutable lootSearchChestPos = new BlockPos.Mutable(0, 0, 0);

        @Override
        public void initWidgets() {
            var s = add(theme.dropdown(pageType.values(), currentPage)).centerX().widget();
            s.action = () -> {
                currentPage = s.get();
                reload();
            };
            WVerticalList list = add(theme.verticalList()).widget();
            switch (currentPage) {
                case Lifting:
                    fillActiveStructureSetSection(list.add(theme.section("Structures")).expandX().widget());
                    fillLiftingSection(list.add(theme.section("Lifting")).expandX().widget());
                    break;
                case StructureSets:
                    fillActiveStructureSetSection(list.add(theme.section("Active set")).expandX().widget());
                    fillSavedStructureSetsSection(list.add(theme.section("Saved sets")).expandX().widget());
                    break;
                case Utilities:
                    fillLootLookupSection(list.add(theme.section("Loot search")).expandX().widget());
                    fillStructureToRandomWorldSeedsSection(list.add(theme.section("To random world seeds")).expandX().widget());
                case HashedSeeds:
                    fillHashedSeedsSection(list.add(theme.section("Hashed seeds")).expandX().widget());
            }
        }

        public void fillHashedSeedsSection(WSection section) {
            SeedUtilsSystem sys = SeedUtilsSystem.get();

            if (mc.world != null) {
                var currentHashedSeed = section.add(theme.horizontalList()).widget();
                currentHashedSeed.add(theme.label(String.format("Current dimension hashed seed: %d", mc.world.getBiomeAccess().seed)));
            }

            var t = section.add(theme.table()).expandX().widget();

            t.add(theme.label("when"));
            t.add(theme.label("hashed seed"));
            t.add(theme.label("copy"));

            List<Long> keys = new ArrayList<>(sys.savedHashedSeeds.keySet().stream().toList());
            keys.sort(Long::compare);

            for (Long key : keys) {
                t.add(theme.label(localTimeString(key)));
                var s = sys.savedHashedSeeds.get(key).toString();
                t.add(theme.label(s));
                var copyBtn = t.add(theme.button("copy")).widget();
                copyBtn.action = () -> {
                    mc.keyboard.setClipboard(s);
                    copyBtn.set("copied");
                };
            }

        }

        public void fillLootLookupSection(WSection section) {
            var lootTableBox = section.add(theme.horizontalList()).widget();
            var changeLootTableBtn = lootTableBox.add(theme.button("Change")).widget();
            changeLootTableBtn.action = () -> {
                mc.setScreen(new StringPickScreen<>(theme, "Pick loot table", LootTables.getAll().stream().toList(), selected -> {
                    lootSearchSelectedLootTable = selected;
                }, element -> element.getValue().getPath()));
            };
            lootTableBox.add(theme.label("Loot table: " + lootSearchSelectedLootTable.getValue().getPath()));

            var worldSeedBox = section.add(theme.horizontalList()).expandX().widget();
            worldSeedBox.add(theme.label("World seed: "));
            var worldSeedTextBox = worldSeedBox.add(theme.textBox(String.valueOf(lootSearchSelectedWorldSeed))).expandX().widget();
            worldSeedTextBox.action = () -> {
                var v = worldSeedTextBox.get();
                try {
                    lootSearchSelectedWorldSeed = Long.parseLong(v);
                } catch(NumberFormatException ignored) {
                    lootSearchSelectedWorldSeed = v.hashCode();
                }
                reload();
            };

            var blockPosBox = section.add(theme.horizontalList()).widget();
            blockPosBox.add(theme.label("Chest pos: "));
            var posEditX = blockPosBox.add(theme.intEdit(lootSearchChestPos.getX(), Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();
            posEditX.action = () -> lootSearchChestPos.setX(posEditX.get());
            var posEditY = blockPosBox.add(theme.intEdit(lootSearchChestPos.getY(), Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();
            posEditY.action = () -> lootSearchChestPos.setY(posEditY.get());
            var posEditZ = blockPosBox.add(theme.intEdit(lootSearchChestPos.getZ(), Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();
            posEditZ.action = () -> lootSearchChestPos.setZ(posEditZ.get());
            var lookAtBtn = blockPosBox.add(theme.button(GuiRenderer.CIRCLE)).widget();
            lookAtBtn.action = () -> {
                var rc = mc.cameraEntity.raycast(20, 0, false);
                if (rc.getType() == HitResult.Type.BLOCK) {
                    lootSearchChestPos = new BlockPos(new Vec3i((int) rc.getPos().x, (int) rc.getPos().y, (int) rc.getPos().z)).mutableCopy();
                    reload();
                }
            };

            var randIndexBox = section.add(theme.horizontalList()).widget();
            randIndexBox.add(theme.label("Rand index: "));
            var randIndexEdit = randIndexBox.add(theme.intEdit(0, Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();

            var randStepBox = section.add(theme.horizontalList()).widget();
            randStepBox.add(theme.label("Rand step: "));
            var randStepEdit = randStepBox.add(theme.intEdit(0, Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();

            var genList = section.add(theme.verticalList()).widget();
            var genResults = section.add(theme.verticalList()).widget();
            var genBtn = genList.add(theme.button("Generate")).widget();
            genBtn.action = () -> {
//                ReloadableRegistries.Lookup lookup = mc.getServer().getReloadableRegistries();
//                lookup.getIds(RegistryKeys.LOOT_TABLE);
//            lookup.getLootTable(lootSearchSelectedLootTable).generateLoot()
//                LootWorldContext lootWorldContext = new LootWorldContext.Builder(mc.getServer().getWorld(mc.world.getRegistryKey()))
//                    .add(LootContextParameters.ORIGIN, lootSearchChestPos.toCenterPos())
//                    .add(LootContextParameters.THIS_ENTITY, mc.player)
//                    .build(LootContextTypes.CHEST);
//            LootWorldContext lootWorldContext = new net.minecraft.loot.context.LootWorldContext.Builder(mc.world.getServer().getOverworld()).build(LootContextTypes.EMPTY);
//                ObjectArrayList<ItemStack> objectArrayList = LootTable.builder().build().generateLoot(lootWorldContext, lootSearchSelectedWorldSeed);

                ChestBlockEntity chestBlockEntity = (ChestBlockEntity) ((BlockEntityProvider)Blocks.CHEST).createBlockEntity(lootSearchChestPos, Blocks.CHEST.getDefaultState());
                var chunkRandom = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));

                var chunkPos = new ChunkPos(lootSearchChestPos);
                long l = chunkRandom.setPopulationSeed(lootSearchSelectedWorldSeed, chunkPos.x*16, chunkPos.z*16);
                chunkRandom.setDecoratorSeed(l, randIndexEdit.get(), randStepEdit.get());
                chunkRandom.nextInt(3);
                chestBlockEntity.setLootTable(lootSearchSelectedLootTable, chunkRandom.nextLong());
                var serverWorld = mc.getServer().getWorld(World.OVERWORLD);
                chestBlockEntity.setWorld(serverWorld);
                chestBlockEntity.generateLoot(mc.player);

                genResults.clear();
                genResults.add(theme.label("World seed " + lootSearchSelectedWorldSeed));
                genResults.add(theme.label("Chunk " + lootSearchChestPos + chunkPos));
                genResults.add(theme.label("Rng index " + randIndexEdit.get() + " step " + randStepEdit.get() + " loot " + chestBlockEntity.getLootTableSeed()));
                genResults.add(theme.label("Generated " + chestBlockEntity.size() + " item stacks"));
                var rest = genResults.add(theme.table()).widget();
                {
                    var i = 1;
                    for (ItemStack s : chestBlockEntity) {
                        rest.add(theme.item(s));
                        if (i % 9 == 0) {
                            rest.row();
                        }
                        i++;
                    }
                }

                var checkBtn = genResults.add(theme.button("check actual")).widget();
                checkBtn.action = () -> {
                    genResults.add(theme.label("checking at " + lootSearchChestPos));
                    var ent = mc.world.getBlockEntity(lootSearchChestPos, BlockEntityType.CHEST);
                    if (ent.isPresent()) {
                        genResults.add(theme.label("Rng loot " + ent.get().getLootTableSeed()));
                        {
                            var i = 1;
                            for (ItemStack s : ent.get()) {
                                rest.add(theme.item(s));
                                if (i % 9 == 0) {
                                    rest.row();
                                }
                                i++;
                            }
                        }
                    } else {
                        genResults.add(theme.label("no chest"));

                    }
                };
            };
        }

        public void fillStructureToRandomWorldSeedsSection(WSection section) {
            WTextBox inputBox = section.add(theme.textBox("", "Enter structure seeds here")).expandCellX().expandX().expandCellX().widget();
            inputBox.minWidth = 400;
            WTextBox outputBox = section.add(theme.textBox("Random world seeds will appear here")).expandX().widget();
            inputBox.action = () -> {
                StringBuilder b = new StringBuilder();
                for (String ss : inputBox.get().split("\n")) {
                    try {
                        long s = Long.parseLong(ss);
                        for (Long randomWorldSeed : StructureSeed.toRandomWorldSeeds(s)) {
                            b.append(randomWorldSeed);
                            b.append('\n');
                        }
                    } catch (NumberFormatException ignored) {}
                }
                outputBox.set(b.toString());
            };
        }

        public void fillSavedStructureSetsSection(WSection section) {
            SeedUtilsSystem sys = SeedUtilsSystem.get();

            section.add(theme.label(String.format("Saved structure sets: %d", sys.savedStructureDataSets.size())));

            WTable table = section.add(theme.table()).widget();
            table.add(theme.label("Set name"));
            table.add(theme.label("Created at"));
            table.add(theme.label("Structures"));
            table.add(theme.label("Actions"));
            table.row();

            for (SeedUtilsSystem.StructureDataSet savedStructureDataSet : sys.savedStructureDataSets) {
                table.add(theme.label(savedStructureDataSet.name));
                table.add(theme.label(LocalDateTime.ofEpochSecond(
                    savedStructureDataSet.createdAt/1000,
                    (int)(savedStructureDataSet.createdAt%1000)*1000000,
                    ZoneOffset.UTC).toString()));
                table.add(theme.label(String.valueOf(savedStructureDataSet.data.size())));
                WHorizontalList actions = table.add(theme.horizontalList()).widget();
                WMinus m = actions.add(theme.minus()).widget();
                m.action = () -> {
                    sys.savedStructureDataSets.remove(savedStructureDataSet);
                    reload();
                };
                WButton a = actions.add(theme.button("Activate")).widget();
                a.action = () -> {
                    sys.activeStructureDataSet = savedStructureDataSet;
                    reload();
                };
            }

        }

        public void fillActiveStructureSetSection(WSection section) {
            SeedUtilsSystem sys = SeedUtilsSystem.get();

            if (sys.activeStructureDataSet == null) {
                section.add(theme.label("No active structure set"));
                var createEmptyBtn = section.add(theme.button("Create empty")).widget();
                createEmptyBtn.action = () -> {
                    sys.activeStructureDataSet = new SeedUtilsSystem.StructureDataSet("New structure set");
                    reload();
                };
                var selectDatasetBtn = section.add(theme.button("Select from saved")).widget();
                selectDatasetBtn.action = () -> {
                    currentPage = pageType.StructureSets;
                    reload();
                };
                return;
            }

            section.add(theme.label(String.format("Active structure set: %s", sys.activeStructureDataSet.name)));

            WTable table = section.add(theme.table()).widget();
            table.add(theme.label("Structure"));
            table.add(theme.label("Chunk X"));
            table.add(theme.label("Chunk Z"));
            table.add(theme.label("Rm")).widget().tooltip = "Remove";
            table.row();
            for (var d : sys.activeStructureDataSet.data) {
                var l = theme.label(d.name);
                l.tooltip = String.format("Added at: %d\nSalt: %d\nSpacing: %d\nSeparation: %d", d.addedAt, d.salt, d.spacing, d.separation);
                table.add(l);

                WIntEdit cxe = theme.intEdit(d.chunkX, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
//                cxe.action = () -> sys.activeStructureDataSet.data;
                table.add(cxe);

                WIntEdit cze = theme.intEdit(d.chunkZ, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
//                cze.action = () -> sys.setActiveStructureDataElement(u, d);
                table.add(cze);

                table.add(theme.minus()).widget().action = () -> {
                    sys.activeStructureDataSet.data.remove(d);
//                    sys.removeActiveStructureData(d);
                    reload();
                };
                table.row();
            }

            WHorizontalList addOpts = theme.horizontalList();
            section.add(addOpts);
            WDropdown<String> addStructType = addOpts.add(theme.dropdown(defaultStructures.keySet().toArray(new String[0]), "desert_pyramid")).widget();
            var addX = addOpts.add(theme.intEdit(0, Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();
            var addZ = addOpts.add(theme.intEdit(0, Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();
            WDropdown<MCVersion> addVer = addOpts.add(theme.dropdown(MCVersion.values(), MCVersion.v1_21)).widget();
            WButton addBtn = addOpts.add(theme.button("Add structure")).widget();
            addBtn.action = () -> {
                var s = defaultStructures.get(addStructType.get()).create(addVer.get());
                sys.activeStructureDataSet.data.add(new SeedUtilsSystem.StructureData((UniformStructure<?>) s, addX.get(), addZ.get()));
                reload();
            };

            WHorizontalList clipOpts = theme.horizontalList();
            section.add(clipOpts);
            WButton fromClipBtn = clipOpts.add(theme.button("From clipboard")).widget();
            fromClipBtn.action = () -> {
                try {
                    sys.activeStructureDataSet = new SeedUtilsSystem.StructureDataSet("Pasted").fromTag(StringNbtReader.readCompound(mc.keyboard.getClipboard()));
                } catch (CommandSyntaxException ignored) {}
                reload();
            };
            WButton toClipBtn = clipOpts.add(theme.button("To clipboard")).widget();
            toClipBtn.action = () -> {
                var snbtWriter = new StringNbtWriter();
                snbtWriter.visitCompound(sys.activeStructureDataSet.toTag());
                mc.keyboard.setClipboard(snbtWriter.getString());
                toClipBtn.set(String.format("Copied %d structures", sys.activeStructureDataSet.data.size()));
            };
            WButton moveToSavedSetsBtn = clipOpts.add(theme.button("Move to saved sets")).widget();
            moveToSavedSetsBtn.action = () -> {
                if (!sys.savedStructureDataSets.contains(sys.activeStructureDataSet)) {
                    sys.activeStructureDataSet.createdAt = Instant.now().getEpochSecond();
                    sys.savedStructureDataSets.add(sys.activeStructureDataSet);
                }
                sys.activeStructureDataSet = null;
            };
        }

        public void fillLiftingSection(WSection section) {
            StructureLifting.statusLabel = section.add(theme.label(StructureLifting.getLiftingStatus())).expandX().widget();
            WTable controls = section.add(theme.table()).expandX().widget();
            if (SeedUtilsSystem.get().activeStructureDataSet != null) {
                WButton startBtn = controls.add(theme.button("Start")).expandX().widget();
                startBtn.action = () -> {
                    if (StructureLifting.currentLifting != null &&
                        !StructureLifting.currentLifting.isCancelled() &&
                        !StructureLifting.currentLifting.isDone()) {
                        return;
                    }
                    var d = SeedUtilsSystem.get().activeStructureDataSet.data;
                    if (d.size() < 4) {
                        String warnText = "Warning! Lifting with <4 setructures will result in millions of seeds, are you sure you want to try this?";
                        if (!startBtn.getText().equals(warnText)) {
                            startBtn.set(warnText);
                            return;
                        } else {
                            startBtn.set("Start");
                        }
                    }

                    StructureLifting.Progress progressListener = progress -> MinecraftClient.getInstance().execute(() -> {
                        if (StructureLifting.statusLabel == null) {
                            return;
                        }
                        StructureLifting.currentLiftingProgress = progress;
                        StructureLifting.statusLabel.set(StructureLifting.getLiftingStatus());
                    });

                    StructureLifting.currentLifting = StructureLifting.crack(d.stream().map(structureData -> (StructureLifting.Data)structureData).toList(), MCVersion.v1_21, progressListener);

                    StructureLifting.currentLifting.thenAcceptAsync(seeds -> {
                        if (StructureLifting.statusLabel == null) {
                            return;
                        }
                        StructureLifting.statusLabel.set(StructureLifting.getLiftingStatus());
                    }, MinecraftClient.getInstance());
                };
            }
//            WButton cancelBtn = controls.add(theme.button("Cancel lifting")).expandX().widget();
//            cancelBtn.action = () -> {
//                mc.execute(() -> {
//                    reload();
//                });
//            };
            controls.row();
            WTable results = section.add(theme.table()).expandX().widget();
            WButton structureSeedsToClipBtn = results.add(theme.button("Copy structure seeds")).expandX().widget();
            structureSeedsToClipBtn.action = () -> {
                List<Long> structureSeeds = StructureLifting.getStructureSeeds();
                StringBuilder b = new StringBuilder();
                for (long structureSeed : structureSeeds) {
                    b.append(structureSeed);
                    b.append('\n');
                }
                mc.keyboard.setClipboard(b.toString());
                structureSeedsToClipBtn.set(String.format("Copied %d structure seeds", structureSeeds.size()));
            };
            WButton worldSeedsToClipBtn = results.add(theme.button("Copy random world seeds")).expandX().widget();
            worldSeedsToClipBtn.action = () -> {
                List<Long> structureSeeds = StructureLifting.getStructureSeeds();
                StringBuilder b = new StringBuilder();
                int copyLength = 0;
                for (long structureSeed : structureSeeds) {
                    for (Long randomWorldSeed : StructureSeed.toRandomWorldSeeds(structureSeed)) {
                        copyLength++;
                        b.append(randomWorldSeed);
                        b.append('\n');
                    }
                }
                mc.keyboard.setClipboard(b.toString());
                worldSeedsToClipBtn.set(String.format("Copied %d random world seeds", copyLength));
            };
            results.row();
            WButton structureSeedsShowBtn = results.add(theme.button("View structure seeds")).expandX().widget();
            structureSeedsShowBtn.action = () -> {
                List<Long> structureSeeds = StructureLifting.getStructureSeeds();
                mc.setScreen(new LongsViewScreen(theme, "Lifted structure seeds", structureSeeds));
            };
            WButton worldSeedsShowBtn = results.add(theme.button("View random world seeds")).expandX().widget();
            worldSeedsShowBtn.action = () -> {
                List<Long> structureSeeds = StructureLifting.getStructureSeeds();
                List<Long> ws = new ArrayList<>();
                for (long structureSeed : structureSeeds) {
                    ws.addAll(StructureSeed.toRandomWorldSeeds(structureSeed));
                }
                mc.setScreen(new LongsViewScreen(theme, "Lifted random world seeds", ws));
            };
            results.row();
        }

        public static String localTimeString(Long t) {
            return LocalDateTime.ofEpochSecond(t/1000, (int)(t%1000)*1000000, ZoneOffset.UTC).toString();
        }

        public static Map<String, FeatureFactory<? extends Structure<?, ?>>> defaultStructures = new HashMap<>();

        static {
            defaultStructures.put("igloo", Igloo::new);
            defaultStructures.put("desert_pyramid", DesertPyramid::new);
            defaultStructures.put("jungle_pyramid", JunglePyramid::new);
            defaultStructures.put("swamp_hut", SwampHut::new);
            defaultStructures.put("monument", Monument::new);
            defaultStructures.put("pillager_outpost", PillagerOutpost::new);
            defaultStructures.put("shipwreck", Shipwreck::new);
        }

        interface FeatureFactory<T extends Feature<?, ?>> {
            T create(MCVersion version);
        }
    }
}
