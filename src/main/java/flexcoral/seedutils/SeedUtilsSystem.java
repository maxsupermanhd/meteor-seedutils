package flexcoral.seedutils;

import com.seedfinding.mcfeature.structure.UniformStructure;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import javax.annotation.Nullable;
import java.util.*;

public class SeedUtilsSystem extends System<SeedUtilsSystem> {
    public SeedUtilsSystem() {
        super("seedutils");
    }
    public static SeedUtilsSystem get() {
        return Systems.get(SeedUtilsSystem.class);
    }

    public List<StructureDataSet> savedStructureDataSets = new ArrayList<>();
    @Nullable
    public StructureDataSet activeStructureDataSet;

    public Map<Long, Long> savedHashedSeeds = new HashMap<>();

    public synchronized void addHashedSeed(long s) {
        savedHashedSeeds.put(java.lang.System.currentTimeMillis(), s);
    }

    public static class StructureDataSet implements ISerializable<StructureDataSet> {
        public List<StructureData> data;
        public String name;
        public long createdAt;

        public StructureDataSet(String name) {
            this.data = new ArrayList<>();
            this.name = name;
        }

        public StructureDataSet(NbtCompound tag) {
            fromTag(tag);
        }

        @Override
        public NbtCompound toTag() {
            NbtCompound tag = new NbtCompound();
            tag.putString("name", name);
            tag.putLong("createdAt", createdAt);
            NbtList structs = new NbtList();
            for (StructureData d : data) {
                structs.add(d.toTag());
            }
            tag.put("structs", structs);
            return tag;
        }

        @Override
        public StructureDataSet fromTag(NbtCompound tag) {
            name = tag.getString("server").orElse("");
            createdAt = tag.getLong("createdAt").orElse(0L);
            data = new ArrayList<>();
            Optional<NbtList> structs = tag.getList("structs");
            if (structs.isPresent()) {
                for (NbtElement struct : structs.get()) {
                    data.add(new StructureData((NbtCompound) struct));
                }
            }
            return this;
        }
    }

    public static class StructureData extends StructureLifting.Data {
        public long addedAt = 0;

        public StructureData(UniformStructure<?> structure, int chunkX, int chunkZ) {
            super(structure, chunkX, chunkZ);
            addedAt = java.lang.System.currentTimeMillis();
        }

        public StructureData(NbtCompound tag) {
            super(tag);
            fromTag(tag);
        }

        @Override
        public StructureLifting.Data fromTag(NbtCompound tag) {
            super.fromTag(tag);
            addedAt = tag.getLong("addedAt").orElse(0L);
            return this;
        }

        @Override
        public NbtCompound toTag() {
            NbtCompound tag = super.toTag();
            tag.putLong("addedAt", addedAt);
            return tag;
        }
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        NbtList saved = new NbtList();
        for (var e : savedStructureDataSets) {
            saved.add(e.toTag());
        }
        tag.put("saved", saved);
        if (activeStructureDataSet != null) {
            tag.put("active", activeStructureDataSet.toTag());
        }
        return tag;
    }
    @Override
    public SeedUtilsSystem fromTag(NbtCompound tag) {
        Optional<NbtList> saved = tag.getList("saved");
        savedStructureDataSets = new ArrayList<>();
        if (saved.isPresent()) {
            for (NbtElement s : saved.get()) {
                savedStructureDataSets.add(new StructureDataSet((NbtCompound) s));
            }
        }
        Optional<NbtCompound> active = tag.getCompound("active");
        activeStructureDataSet = active.map(StructureDataSet::new).orElse(null);
        return this;
    }
}
