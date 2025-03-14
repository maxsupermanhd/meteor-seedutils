package flexcoral.seedutils;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.UniformStructure;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

public class StructureLifting {
    public static CompletableFuture<long[]> crack(List<Data> dataList, MCVersion version, Progress progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            ThreadLocal<ChunkRand> threadLocal = ThreadLocal.withInitial(ChunkRand::new);
            progressCallback.report(0d);

            LongStream lowerBitsStream = LongStream.range(0, 1L << 19).filter(lowerBits -> {
                ChunkRand rand = threadLocal.get();
                for (Data data : dataList) {
                    rand.setRegionSeed(lowerBits, data.regionX, data.regionZ, data.salt, version);
                    if ((rand.nextInt(data.offset) & 3) != (data.offsetX & 3) || (rand.nextInt(data.offset) & 3) != (data.offsetZ & 3)) {
                        return false;
                    }
                }
                return true;
            });

            long[] lowerBits = lowerBitsStream.parallel().toArray();

            // calculate the amount of progress bar updates to report
            int lowerBitsCount = lowerBits.length;
            int updatesPerUpperBits = MathHelper.ceil((float) 1000 / lowerBitsCount);
            int maskBits = 29 - MathHelper.ceilLog2(updatesPerUpperBits);
            long progressUpdateMask = (1L << maskBits) - 1;
            int progressUpdateCount = MathHelper.smallestEncompassingPowerOfTwo(updatesPerUpperBits) * lowerBitsCount;

            AtomicLong counter = new AtomicLong();

            LongStream seedStream = LongStream.of(lowerBits).mapMulti((lowBits, consumer) -> {
                for (long upperBits = 0; upperBits < (1L << 48 - 19); upperBits++) {
                    consumer.accept((upperBits << 19) | lowBits);

                    if ((upperBits & progressUpdateMask) == 0) {
                        progressCallback.report((double) counter.incrementAndGet() / progressUpdateCount);
                    }
                }
            });

            LongStream structureSeedStream = seedStream.filter(seed -> {
                ChunkRand rand = threadLocal.get();
                for (Data data : dataList) {
                    rand.setRegionSeed(seed, data.regionX, data.regionZ, data.salt, version);
                    if (rand.nextInt(data.offset) != data.offsetX || rand.nextInt(data.offset) != data.offsetZ) {
                        return false;
                    }
                }
                return true;
            });

            long[] crackedSeeds = structureSeedStream.parallel().toArray();
            progressCallback.report(1d);
            return crackedSeeds;
        }, MeteorExecutor.executor);
    }

    @FunctionalInterface
    public interface Progress {
        void report(double progress);
    }

    public static class Data implements ISerializable<Data> {
        public int spacing;
        public int separation;
        public int salt;
        public int chunkX, chunkZ;
        public String name;

        public int regionX, regionZ;
        public int offsetX, offsetZ;
        public int offset;

        public Data(UniformStructure<?> structure, int chunkX, int chunkZ) {
            var regionData = structure.at(chunkX, chunkZ);
            this.regionX = regionData.regionX;
            this.regionZ = regionData.regionZ;
            this.offsetX = regionData.offsetX;
            this.offsetZ = regionData.offsetZ;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.salt = structure.getSalt();
            this.offset = structure.getOffset();
            this.spacing = structure.getSpacing();
            this.separation = structure.getSeparation();
            this.name = structure.getName() + "@" + structure.getVersion().name;
        }

        public Data(NbtCompound tag) {
            this.fromTag(tag);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Data data = (Data) o;
            return spacing == data.spacing && separation == data.separation && salt == data.salt && chunkX == data.chunkX && chunkZ == data.chunkZ && regionX == data.regionX && regionZ == data.regionZ && offsetX == data.offsetX && offsetZ == data.offsetZ && offset == data.offset && Objects.equals(name, data.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(spacing, separation, salt, chunkX, chunkZ, name, regionX, regionZ, offsetX, offsetZ, offset);
        }

        @Override
        public NbtCompound toTag() {
            NbtCompound tag = new NbtCompound();
            tag.putInt("spacing", this.spacing);
            tag.putInt("separation", this.separation);
            tag.putInt("salt", this.salt);
            tag.putInt("chunkX", this.chunkX);
            tag.putInt("chunkZ", this.chunkZ);
            tag.putInt("regionX", this.regionX);
            tag.putInt("regionZ", this.regionZ);
            tag.putInt("offsetX", this.offsetX);
            tag.putInt("offsetZ", this.offsetZ);
            tag.putInt("offset", this.offset);
            tag.putString("name", this.name);
            return tag;
        }

        @Override
        public Data fromTag(NbtCompound tag) {
            this.spacing = tag.getInt("spacing");
            this.separation = tag.getInt("separation");
            this.salt = tag.getInt("salt");
            this.chunkX = tag.getInt("chunkX");
            this.chunkZ = tag.getInt("chunkZ");
            this.regionX = tag.getInt("regionX");
            this.regionZ = tag.getInt("regionZ");
            this.offsetX = tag.getInt("offsetX");
            this.offsetZ = tag.getInt("offsetZ");
            this.offset = tag.getInt("offset");
            this.name = tag.getString("name");
            return this;
        }
    }
}
