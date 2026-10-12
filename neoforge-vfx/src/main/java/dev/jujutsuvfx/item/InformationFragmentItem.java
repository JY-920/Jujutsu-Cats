package dev.jujutsuvfx.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/** The obfuscated style is native Minecraft formatting, not literal section-sign text. */
public final class InformationFragmentItem extends Item {
    public InformationFragmentItem(){super(new Properties().stacksTo(1));}
    @Override public Component getName(ItemStack stack){
        return Component.translatable(getDescriptionId()).withStyle(ChatFormatting.OBFUSCATED);
    }
    @Override public boolean isFoil(ItemStack stack){return true;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("item.jujutsuvfx.information_fragment.hint").withStyle(ChatFormatting.GRAY));
    }
}
