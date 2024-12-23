package io.github.haykam821.consolebox.mixin;

import com.dylibso.chicory.runtime.Memory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

@Mixin(Memory.class)
public interface MemoryAccessor {
    @Accessor
    ByteBuffer getBuffer();
}
