package openperipheral.addons.pim;

import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import openmods.tileentity.OpenTileEntity;
import openperipheral.api.architecture.IArchitectureAccess;
import openperipheral.api.architecture.IAttachable;
import org.apache.commons.lang3.ArrayUtils;

public class TileEntityPIM extends OpenTileEntity implements IInventory, IAttachable {

    private Optional<Integer> playerId = Optional.empty();

    private Set<IArchitectureAccess> computers = Sets.newIdentityHashSet();

    public Optional<EntityPlayer> getPlayer() {
        return playerId.map(worldObj::getEntityByID).map(p -> (EntityPlayer) p);
    }

    @Override
    public int getSizeInventory() {
        return getPlayer().map(p -> p.inventory.getSizeInventory()).orElse(0);
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return getPlayer().map(p -> p.inventory.getStackInSlot(i)).orElse(null);
    }

    @Override
    public ItemStack decrStackSize(int i, int j) {
        return getPlayer().map(p -> p.inventory.decrStackSize(i, j)).orElse(null);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack) {
        getPlayer().ifPresent(p -> p.inventory.setInventorySlotContents(i, itemstack));
    }

    @Override
    public String getInventoryName() {
        return getPlayer().map(p -> p.getCommandSenderName()).orElse("pim");
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return getPlayer().map(p -> p.inventory.getInventoryStackLimit()).orElse(0);
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer entityplayer) {
        return true;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack) {
        return getPlayer().isPresent();
    }

    @Override
    public void addComputer(IArchitectureAccess computer) {
        synchronized (computers) {
            computers.add(computer);
        }
    }

    @Override
    public synchronized void removeComputer(IArchitectureAccess computer) {
        synchronized (computers) {
            computers.remove(computer);
        }
    }

    public boolean hasPlayer() {
        if (worldObj == null) return false;
        return worldObj.getBlockMetadata(xCoord, yCoord, zCoord) == 1;
    }

    private void setPlayer(EntityPlayer newPlayer) {
        worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.1D, zCoord + 0.5D, "random.click", 0.3F, 0.6F);
        worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, newPlayer == null ? 0 : 1, 3);

        getPlayer().ifPresent(prevPlayer -> {
            GameProfile profile = prevPlayer.getGameProfile();
            final UUID uuid = profile.getId();
            fireEvent("player_off", profile.getName(), uuid != null ? uuid.toString() : "?");
        });

        if (newPlayer != null) {
            playerId = Optional.of(newPlayer.getEntityId());
            GameProfile profile = newPlayer.getGameProfile();
            final UUID uuid = profile.getId();
            fireEvent("player_on", profile.getName(), uuid != null ? uuid.toString() : "?");
        } else
            playerId = Optional.empty();
    }

    public void trySetPlayer(EntityPlayer newPlayer) {
        if (newPlayer == null) return;
        if (!getPlayer().isPresent() && isPlayerValid(newPlayer)) setPlayer(newPlayer);
    }

    private void fireEvent(String eventName, Object... args) {
        synchronized (computers) {
            for (IArchitectureAccess computer : computers) {
                Object[] extendedArgs = ArrayUtils.add(args, computer.peripheralName());
                computer.signal(eventName, extendedArgs);
            }
        }
    }

    private boolean isPlayerValid(EntityPlayer player) {
        if (player == null) return false;
        int playerX = MathHelper.floor_double(player.posX);
        int playerY = MathHelper.floor_double(player.posY + 0.5D);
        int playerZ = MathHelper.floor_double(player.posZ);
        return playerX == xCoord && playerY == yCoord && playerZ == zCoord;
    }

    /**
     * TODO: fix this. This doesnt seem.. efficient.
     */
    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            if (getPlayer().isPresent() && !isPlayerValid(getPlayer().get()))
                setPlayer(null);
        }
    }
}
