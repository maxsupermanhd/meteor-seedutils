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
import com.seedfinding.mcfeature.structure.*;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.StringNbtWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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

        @Override
        public void initWidgets() {
            WVerticalList list = theme.verticalList();

            fillStructuresSection(theme, list.add(theme.section("Structures")).expandX().widget());

            fillLiftingSection(theme, list.add(theme.section("Lifting")).expandX().widget());

            add(list);
        }

        public void fillStructuresSection(GuiTheme theme, WSection section) {
            WTable table = section.add(theme.table()).widget();

            table.add(theme.label("Structure"));
            table.add(theme.label("Chunk X"));
            table.add(theme.label("Chunk Z"));
            table.add(theme.label("Rm")).widget().tooltip = "Remove";
            table.row();

            SeedUtilsSystem sys = SeedUtilsSystem.get();
            var structs = sys.getAllStructureData();

            for (var u : structs.keySet().stream().sorted().toList()) {
                var d = structs.get(u);

                var l = theme.label(d.name);
                l.tooltip = String.format("UUID: %s\nSalt: %d\nSpacing: %d\nSeparation: %d", u.toString(), d.salt, d.spacing, d.separation);
                table.add(l);

                WIntEdit cxe = theme.intEdit(d.chunkX, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
                cxe.action = () -> sys.setStructureData(u, d);
                table.add(cxe);

                WIntEdit cze = theme.intEdit(d.chunkZ, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
                cze.action = () -> sys.setStructureData(u, d);
                table.add(cze);

                table.add(theme.minus()).widget().action = () -> {
                    sys.removeStructureData(d);
                    reload();
                };
                table.row();
            }

            WHorizontalList addOpts = theme.horizontalList();
            section.add(addOpts);
            String[] defaultStructureNames = {"igloo", "desert_pyramid", "jungle_pyramid", "swamp_hut", "shipwreck", "monument", "pillager_outpost"};
            WDropdown<String> addStructType = addOpts.add(theme.dropdown(defaultStructureNames, "desert_pyramid")).widget();
            var addX = addOpts.add(theme.intEdit(0, Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();
            var addZ = addOpts.add(theme.intEdit(0, Integer.MIN_VALUE, Integer.MAX_VALUE, true)).widget();
            WDropdown<MCVersion> addVer = addOpts.add(theme.dropdown(MCVersion.values(), MCVersion.v1_21)).widget();
            WButton addBtn = addOpts.add(theme.button("Add structure")).widget();
            addBtn.action = () -> {
                var s = defaultStructures.get(addStructType.get()).create(addVer.get());
                sys.addStructureData(new StructureLifting.Data((UniformStructure<?>) s, addX.get(), addZ.get()));
                reload();
            };

            WHorizontalList clipOpts = theme.horizontalList();
            section.add(clipOpts);
            WButton fromClipBtn = clipOpts.add(theme.button("From clipboard")).widget();
            fromClipBtn.action = () -> {
                try {
                    SeedUtilsSystem.get().fromTag(new StringNbtReader(new StringReader(mc.keyboard.getClipboard())).parseCompound());
                } catch (CommandSyntaxException ignored) {}
            };
            WButton toClipBtn = clipOpts.add(theme.button("To clipboard")).widget();
            toClipBtn.action = () -> mc.keyboard.setClipboard(new StringNbtWriter().apply(sys.toTag()));
        }

        public void fillLiftingSection(GuiTheme theme, WSection section) {
            StructureLifting.statusLabel = section.add(theme.label(StructureLifting.getLiftingStatus())).expandX().widget();
            WButton startBtn = section.add(theme.button("Start lifting")).expandX().widget();
            startBtn.action = () -> {
                if (StructureLifting.currentLifting != null &&
                    !StructureLifting.currentLifting.isCancelled() &&
                    !StructureLifting.currentLifting.isDone()) {
                    return;
                }

                StructureLifting.Progress progressListener = progress -> MinecraftClient.getInstance().execute(() -> {
                    if (StructureLifting.statusLabel == null) {
                        return;
                    }
                    StructureLifting.statusLabel.set(StructureLifting.getLiftingStatus());
                });

                StructureLifting.currentLifting = StructureLifting.crack(SeedUtilsSystem.get().getAllStructureData().values().stream().toList(), MCVersion.v1_21, progressListener);

                StructureLifting.currentLifting.thenAcceptAsync(seeds -> {
                    if (StructureLifting.statusLabel == null) {
                        return;
                    }
                    StructureLifting.statusLabel.set(StructureLifting.getLiftingStatus());
                }, MinecraftClient.getInstance());
            };
            WButton structureSeedsToClipBtn = section.add(theme.button("Copy structure seeds")).widget();
            structureSeedsToClipBtn.action = () -> {
                long[] structureSeeds;
                try {
                    structureSeeds = StructureLifting.currentLifting.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
                StringBuilder b = new StringBuilder();
                for (long structureSeed : structureSeeds) {
                    b.append(structureSeed);
                    b.append('\n');
                }
                mc.keyboard.setClipboard(b.toString());
            };
            WButton worldSeedsToClipBtn = section.add(theme.button("Copy random world seeds")).widget();
            worldSeedsToClipBtn.action = () -> {
                long[] structureSeeds;
                try {
                    structureSeeds = StructureLifting.currentLifting.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
                StringBuilder b = new StringBuilder();
                for (long structureSeed : structureSeeds) {
                    for (Long randomWorldSeed : StructureSeed.toRandomWorldSeeds(structureSeed)) {
                        b.append(randomWorldSeed);
                        b.append('\n');
                    }
                }
                mc.keyboard.setClipboard(b.toString());
            };
        }

        public static Map<String, FeatureFactory<? extends Structure<?, ?>>> defaultStructures = new HashMap<>();

        static {
            defaultStructures.put("buried_treasure", BuriedTreasure::new);
            defaultStructures.put("desert_pyramid", DesertPyramid::new);
            defaultStructures.put("end_city", EndCity::new);
            defaultStructures.put("igloo", Igloo::new);
            defaultStructures.put("jungle_pyramid", JunglePyramid::new);
            defaultStructures.put("monument", Monument::new);
            defaultStructures.put("pillager_outpost", PillagerOutpost::new);
            defaultStructures.put("shipwreck", Shipwreck::new);
            defaultStructures.put("swamp_hut", SwampHut::new);
        }

        interface FeatureFactory<T extends Feature<?, ?>> {
            T create(MCVersion version);
        }
    }
}
