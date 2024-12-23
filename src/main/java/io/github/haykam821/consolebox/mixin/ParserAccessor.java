package io.github.haykam821.consolebox.mixin;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.Section;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Parser.class)
public interface ParserAccessor {
    @Invoker
    static void callOnSection(WasmModule.Builder module, Section s) {
        throw new UnsupportedOperationException();
    }
}
