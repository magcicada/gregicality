package gregicadditions.item.behaviors.monitorPlugin;

import gregicadditions.client.renderer.RenderHelper;
import gregicadditions.widgets.WidgetARGB;
import gregicadditions.widgets.monitor.WidgetPluginConfig;
import gregtech.api.gui.IUIHolder;
import gregtech.api.gui.widgets.TextFieldWidget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;

public class TextPluginBehavior extends MonitorPluginBaseBehavior {
    public String[] texts;
    public int[] colors;

    public void setText(int line, String text, int color) {
        if (line < 0 || line > texts.length || (texts[line].equals(text) && colors[line] == color)) return;
        this.texts[line] = text;
        this.colors[line] = color;
        writePluginData(1, packetBuffer -> {
            packetBuffer.writeInt(texts.length);
            for (int i = 0; i < texts.length; i++) {
                packetBuffer.writeString(texts[i]);
                packetBuffer.writeInt(colors[i]);
            }
        });
        markDirty();
    }

    @Override
    public void readPluginData(int id, PacketBuffer buf) {
        if(id == 1){
            texts = new String[buf.readInt()];
            colors = new int[texts.length];
            for (int i = 0; i < texts.length; i++) {
                texts[i] = buf.readString(100);
                colors[i] = buf.readInt();
            }
        }
    }

    @Override
    public MonitorPluginBaseBehavior createPlugin() {
        TextPluginBehavior plugin =  new TextPluginBehavior();
        plugin.texts = new String[16];
        plugin.colors = new int[16];
        return plugin;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        for (int i = 0; i < texts.length; i++) {
            data.setString("t" + i, texts[i]);
        }
        data.setIntArray("color", colors);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        for (int i = 0; i < texts.length; i++) {
            texts[i] = data.hasKey("t" + i)? data.getString("t" + i) : "";
        }
        if(data.hasKey("color")) {
            colors = data.getIntArray("color");
        } else {
            Arrays.fill(colors, 0XFFFFFFFF);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderPlugin(float partialTicks) {
        for (int i = 0; i < texts.length; i++) {
            RenderHelper.renderText(-0.5f, -0.4625f + i / 16f, 0.002f, 1/128f, colors[i], texts[i], false);
        }
    }

    @Override
    public boolean hasUI() {
        return true;
    }

    @Override
    public WidgetPluginConfig customUI(WidgetPluginConfig widgets, IUIHolder holder, EntityPlayer entityPlayer) {
        widgets.setSize(260, 210);
        for (int i = 0; i < texts.length; i++) {
            int finalI = i;
            widgets.addWidget(new TextFieldWidget(25, 25 + i * 10, 100, 10, true, ()-> this.texts[finalI], (text)->{
                setText(finalI, text, this.colors[finalI]);
            }).setValidator((data)->true));
            widgets.addWidget(new WidgetARGB(135, 25 + i * 10, 10, colors[i], color->{
                setText(finalI, this.texts[finalI], color);
            }));
        }
        return widgets;
    }

}
