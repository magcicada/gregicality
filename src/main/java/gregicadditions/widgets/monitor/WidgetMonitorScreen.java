package gregicadditions.widgets.monitor;

import gregicadditions.machines.multi.centralmonitor.MetaTileEntityMonitorScreen;
import gregicadditions.client.renderer.RenderHelper;
import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.Widget;
import gregtech.api.util.Position;
import gregtech.api.util.Size;
import net.minecraft.client.renderer.GlStateManager;

public class WidgetMonitorScreen extends Widget {
    private final MetaTileEntityMonitorScreen screen;

    public WidgetMonitorScreen(int x, int y, int w, MetaTileEntityMonitorScreen screen) {
        super(new Position(x, y), new Size(w + 4, w + 4));
        this.screen = screen;
    }

    @Override
    public void drawInBackground(int mouseX, int mouseY, IRenderContext context) {
        Position position = this.getPosition();
        Size size = this.getSize();
        RenderHelper.renderRect(position.x, position.y, size.width, size.height, 0, 0XFF7B7A7C);
        RenderHelper.renderRect(position.x + 2, position.y + 2, size.width - 4, size.height - 4, 0, 0XFF000000);

        if (screen != null && screen.isActive()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(position.x + 2 + 0.5 * (size.width - 4), position.y + 2 + 0.5 * (size.height - 4),0);
            GlStateManager.scale(size.getWidth(), size.getWidth(), 1.0f / size.getWidth());
            GlStateManager.scale(1 / screen.scale,1 / screen.scale,1 / screen.scale);
            GlStateManager.translate(-(screen.scale - 1) * 0.5, -(screen.scale - 1) * 0.5, 0);

            screen.renderScreen(0);
            GlStateManager.popMatrix();
        }

    }
}
