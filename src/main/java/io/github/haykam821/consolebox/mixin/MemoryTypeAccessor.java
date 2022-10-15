package io.github.haykam821.consolebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import io.github.kawamuray.wasmtime.MemoryType;

@Mixin(value = MemoryType.class, remap = false)
public interface MemoryTypeAccessor {
	@Accessor("maximum")
	@Mutable
	public void setMaximum(long maximum);
}
