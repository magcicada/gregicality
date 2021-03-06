package gregicadditions.machines.energyconverter;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import gregicadditions.GAValues;
import gregicadditions.client.ClientHandler;
import gregicadditions.machines.energyconverter.energy.EnergyConverterCharger;
import gregicadditions.machines.energyconverter.energy.UniversalEnergyStorage;
import gregicadditions.machines.energyconverter.traits.charger.ChargeHandler;
import gregicadditions.machines.energyconverter.utils.Energy;
import gregicadditions.machines.energyconverter.utils.EnergyConverterType;
import gregicadditions.machines.energyconverter.utils.Numbers;
import gregicadditions.machines.energyconverter.utils.Ratio;
import gregicadditions.machines.overrides.GATieredMetaTileEntity;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class MetaTileEntityEnergyConverter extends GATieredMetaTileEntity implements EnergyConverterCharger {
    protected final EnergyConverterType type;
    protected final int invSize;
    private UniversalEnergyStorage energyStorage;
    private ChargeHandler itemChargeHandler;

    public MetaTileEntityEnergyConverter(final ResourceLocation id, final int tier, final EnergyConverterType type, int invSize) {
        super(id, tier);
        this.type = type;
        this.invSize = invSize;
        this.reinitializeEnergyContainer();
        this.initializeInventory();
    }

    public UniversalEnergyStorage getEnergyStorage() {
        return this.energyStorage;
    }

    public ChargeHandler getChargeHandler() {
        return this.itemChargeHandler;
    }

    public EnergyConverterType getType() {
        return this.type;
    }

    public int getSize() {
        return this.invSize;
    }

    public long getEUStoredSum(final boolean includeWrapped) {
        return Numbers.addWithOverflowCheck(this.getChargeHandler().storedEU(includeWrapped), this.getEnergyStorage().getEnergyStored());
    }

    public long getEUCapacitySum(final boolean includeWrapped) {
        return Numbers.addWithOverflowCheck(this.getChargeHandler().capacityEU(includeWrapped), this.getEnergyStorage().getEnergyCapacity());
    }

    public long extractEU(final long max, final boolean includeWrapped, final boolean simulate) {
        if (max <= 0L) {
            return 0L;
        }
        long usage = this.getChargeHandler().dischargeEU(max, false, true, includeWrapped, simulate);
        if (max > usage) {
            usage += (simulate ? Math.min(this.getEnergyStorage().getEnergyStored(), max - usage) : Math.abs(this.getEnergyStorage().removeEnergy(max - usage)));
        }
        return usage;
    }

    public long insertEU(final long max, final boolean includeWrapped, final boolean simulate) {
        if (max <= 0L) {
            return 0L;
        }
        long inserted = this.getChargeHandler().chargeEU(max, false, includeWrapped, simulate);
        if (max > inserted) {
            inserted += (simulate ? Math.min(this.getEnergyStorage().getEnergyCapacity() - this.getEnergyStorage().getEnergyStored(), max - inserted) : this.getEnergyStorage().addEnergy(max - inserted));
        }
        return inserted;
    }

    public Number getStoredSum(final Energy e, final boolean includeWrapped) {
        if (e == Energy.GTEU) {
            return this.getEUStoredSum(includeWrapped);
        }
        if (e != this.type.getConverterType().getEnergyOutput()) {
            throw new IllegalArgumentException();
        }
        return Numbers.addWithOverflowCheck(this.getChargeHandler().getStoredSum(e, includeWrapped), this.ratioGteuAsInput().convert(this.getEnergyStorage().getEnergyStored(), e.getNumberType()));
    }

    public Number getCapacitySum(final Energy e, final boolean includeWrapped) {
        if (e == Energy.GTEU) {
            return this.getEUCapacitySum(includeWrapped);
        }
        if (e != this.type.getConverterType().getEnergyOutput()) {
            throw new IllegalArgumentException();
        }
        return Numbers.addWithOverflowCheck(this.getChargeHandler().getCapacitySum(e, includeWrapped), this.ratioGteuAsInput().convert(this.getEnergyStorage().getEnergyCapacity(), e.getNumberType()));
    }

    public Number extractEnergy(final Energy e, final Number max, final boolean extractWrapped, final boolean simulate) {
        if (e == Energy.GTEU) {
            return this.extractEU(max.longValue(), extractWrapped, simulate);
        }
        if (e != this.type.getConverterType().getEnergyOutput()) {
            throw new IllegalArgumentException();
        }
        final Number n = this.getChargeHandler().extractEnergy(e, max, extractWrapped, simulate);
        return Numbers.addWithOverflowCheck(n, this.ratioGteuAsInput().convert(this.getEnergyStorage().asElectricItem().discharge(this.ratioGteuAsOutput().convertToLong(Numbers.sub(max, n)), this.getTier(), true, false, simulate), e.getNumberType()));
    }

    public Number insertEnergy(final Energy e, final Number max, final boolean insertWrapped, final boolean simulate) {
        if (e == Energy.GTEU) {
            return this.insertEU(max.longValue(), insertWrapped, simulate);
        }
        if (e != this.type.getConverterType().getEnergyOutput()) {
            throw new IllegalArgumentException();
        }
        final Number n = this.getChargeHandler().insertEnergy(e, max, insertWrapped, simulate);
        return Numbers.addWithOverflowCheck(n, this.ratioGteuAsInput().convert(this.getEnergyStorage().asElectricItem().charge(this.ratioGteuAsOutput().convertToLong(Numbers.sub(max, n)), this.getTier(), true, simulate), e.getNumberType()));
    }

    public Ratio ratio() {
        return this.getType().ratio();
    }

    public Ratio ratioGteuAsInput() {
        return this.isGTEU() ? this.ratio() : this.ratio().reverse();
    }

    public Ratio ratioGteuAsOutput() {
        return this.isGTEU() ? this.ratio().reverse() : this.ratio();
    }

    public boolean isThisEnabled() {
        return !this.getType().isDisabled();
    }

    public boolean isGTEU() {
        return this.type.isGTEU();
    }

    public void renderMetaTileEntity(final CCRenderState renderState, final Matrix4 translation, final IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        ClientHandler.CONVERTER_FACES.get(this.type).renderSided(this.getFrontFacing(), renderState, translation, pipeline);
    }

    protected void initializeInventory() {
        super.initializeInventory();
        this.itemInventory = this.importItems;
    }

    protected void reinitializeEnergyContainer() {
        if (this.type == null) {
            return;
        }
        this.energyStorage = new UniversalEnergyStorage(this, GAValues.V[this.getTier()] * 32L);
        this.type.getInput().createEnergyReceiverTrait(this);
        this.type.getOutput().createEnergyEmitterTrait(this);
        this.itemChargeHandler = this.type.getConverterType().getEnergyOutput().createChargeHandler(this);
    }

    public int getActualComparatorValue() {
        final long energyStored = this.energyStorage.getEnergyStored();
        final long energyCapacity = this.energyStorage.getEnergyCapacity();
        final float f = (energyCapacity == 0L) ? 0.0f : (energyStored / (float) energyCapacity);
        return MathHelper.floor(f * 14.0f) + ((energyStored > 0L) ? 1 : 0);
    }

    protected boolean isEnergyEmitter() {
        return !this.isGTEU();
    }

    public void addInformation(final ItemStack stack, @Nullable final World player, final List<String> tooltip, final boolean advanced) {
        tooltip.add(I18n.format("gtadditions.converter.description", this.type.getInput(), this.type.getOutput()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", this.invSize));
        if (this.getType().isDisabled()) {
            tooltip.add(I18n.format("gtadditions.converter.disabled"));
        } else {
            if (this.type.getInput() == Energy.GTEU) {
                tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_in", this.energyStorage.getInputVoltage(), GAValues.VN[this.getTier()]));
            } else {
                tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_out", this.energyStorage.getOutputVoltage(), GAValues.VN[this.getTier()]));
            }
            tooltip.add(I18n.format(this.isGTEU() ? "gtadditions.converter.energy_out" : "gtadditions.converter.energy_in", this.type.getConverterType().getEnergyOutput(), this.ratioGteuAsInput().convert(GAValues.V[this.getTier()] * this.invSize, this.type.getConverterType().getEnergyOutput().getNumberType())));
            tooltip.add(I18n.format("gregtech.universal.tooltip.energy_storage_capacity", this.energyStorage.getEnergyCapacity()));
        }
    }

    public MetaTileEntity createMetaTileEntity(final MetaTileEntityHolder holder) {
        return new MetaTileEntityEnergyConverter(this.metaTileEntityId, this.getTier(), this.type, this.invSize);
    }

    public boolean isValidFrontFacing(final EnumFacing facing) {
        return true;
    }

    protected IItemHandlerModifiable createImportItemHandler() {
        return new ItemStackHandler(this.invSize) {
            protected void onContentsChanged(final int slot) {
                MetaTileEntityEnergyConverter.this.onEnergyChanged(MetaTileEntityEnergyConverter.this.energyStorage, false);
            }

            public ItemStack insertItem(final int slot, @Nonnull final ItemStack stack, final boolean simulate) {
                if (MetaTileEntityEnergyConverter.this.itemChargeHandler.getBatteryContainer(stack, true) == null) {
                    return stack;
                }
                return super.insertItem(slot, stack, simulate);
            }

            public int getSlotLimit(final int slot) {
                return 1;
            }
        };
    }

    protected IItemHandlerModifiable createExportItemHandler() {
        return new EmptyHandler();
    }

    protected ModularUI createUI(final EntityPlayer entityPlayer) {
        final ModularUI.Builder b = ModularUI.builder(GuiTextures.BACKGROUND, 176, 18 + 18 * ((this.invSize / 9) + 1) + 94).label(10, 5, this.getMetaFullName());
        for (int x = 0; x < this.invSize; ++x) {
            b.widget(new SlotWidget(this.importItems, x, 8 + (x % 9) * 18, ((x / 9) + 1) * 18, true, true).setBackgroundTexture(GuiTextures.SLOT, GuiTextures.BATTERY_OVERLAY));
        }
        return b.bindPlayerInventory(entityPlayer.inventory, GuiTextures.SLOT, 8, 18 + 18 * ((this.invSize / 9) + 1) + 12).build(this.getHolder(), entityPlayer);
    }

    @Nullable
    public <T> T getCapability(final Capability<T> cap, final EnumFacing side) {
        if (cap == null) {
            return null;
        }
        if (cap == this.type.getInput().getCapability()) {
            return (T) ((this.getFrontFacing() != side) ? super.getCapability((Capability) cap, side) : null);
        }
        if (cap == this.type.getOutput().getCapability()) {
            return (T) ((this.getFrontFacing() == side) ? super.getCapability((Capability) cap, side) : null);
        }
        return (T) super.getCapability((Capability) cap, side);
    }

    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("EnergyStorage", this.energyStorage.serializeNBT());
        return data;
    }

    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.energyStorage.deserializeNBT(data.getCompoundTag("EnergyStorage"));
    }
}
