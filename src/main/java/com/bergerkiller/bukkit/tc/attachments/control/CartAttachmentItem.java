package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;

public class CartAttachmentItem extends CartAttachment {
    private VirtualEntity entity;
    private ItemStack item;
    private ItemTransformType transformType;

    @Override
    public void onAttached() {
        super.onAttached();

        this.item = this.config.get("item", ItemStack.class);

        if (this.config.isNode("position")) {
            this.transformType = this.config.get("position.transform", ItemTransformType.HEAD);
        } else {
            this.transformType = ItemTransformType.HEAD;
        }

        this.entity = new VirtualEntity(this.controller);
        this.entity.setEntityType(EntityType.ARMOR_STAND);
        this.entity.setSyncMode(SyncMode.ITEM);
        this.entity.setRelativeOffset(0.0, -1.2, 0.0);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.local_transform = new Matrix4x4();
        this.local_transform.translate(this.position);
        this.local_transform.rotateYawPitchRoll(this.rotation);
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity != null && this.entity.getEntityId() == entityId;
    }

    @Override
    public int getMountEntityId() {
        return this.entity.getEntityId();
    }

    @Override
    public void makeVisible(Player viewer) {
        // Send entity spawn packet
        entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));

        // Set pose information before making equipment visible
        //PacketUtil.sendPacket(viewer, getPosePacket());

        // Set equipment
        if (this.item != null) {
            PacketPlayOutEntityEquipmentHandle equipment = PacketPlayOutEntityEquipmentHandle.createNew(
                    this.entity.getEntityId(), this.transformType.getSlot(), this.item);
            PacketUtil.sendPacket(viewer, equipment);
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        entity.destroy(viewer);
    }

    @Override
    public void onPositionUpdate() {
        // Perform additional translation for certain attached pose positions
        // This correct model offsets
        if (this.transformType == ItemTransformType.LEFT_HAND) {
            Matrix4x4 tmp = this.transform.clone();
            tmp.translate(-0.4, 0.3, 0.9375);
            tmp.multiply(this.local_transform);
            this.entity.updatePosition(tmp);
            super.onPositionUpdate();
        } else if (this.transformType == ItemTransformType.RIGHT_HAND) {
            Matrix4x4 tmp = this.transform.clone();
            tmp.translate(-0.4, 0.3, -0.9375);
            tmp.multiply(this.local_transform);
            this.entity.updatePosition(tmp);
            super.onPositionUpdate();
        } else {
            super.onPositionUpdate();
            this.entity.updatePosition(this.transform);
        }

        // Convert the pitch/roll into an appropriate pose
        Vector in_ypr = this.entity.getYawPitchRoll();

        Vector rotation;
        if (this.transformType == ItemTransformType.LEFT_HAND) {
            rotation = new Vector(180.0, -90.0 + in_ypr.getZ(), 90.0 - in_ypr.getX());
        } else if (this.transformType == ItemTransformType.RIGHT_HAND) {
            rotation = new Vector(0.0, 90.0 + in_ypr.getZ(), 90.0 - in_ypr.getX());
        } else {
            rotation = new Vector(90.0, 90.0 + in_ypr.getZ(), 90.0 - in_ypr.getX());
        }

        DataWatcher meta = this.entity.getMetaData();
        if (this.transformType == ItemTransformType.HEAD) {
            meta.set(EntityArmorStandHandle.DATA_POSE_HEAD, rotation);
        } else if (this.transformType == ItemTransformType.CHEST) {
            meta.set(EntityArmorStandHandle.DATA_POSE_BODY, rotation);
        } else if (this.transformType == ItemTransformType.LEFT_HAND) {
            meta.set(EntityArmorStandHandle.DATA_POSE_ARM_LEFT, rotation);
        } else if (this.transformType == ItemTransformType.RIGHT_HAND) {
            meta.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, rotation);
        } else if (this.transformType == ItemTransformType.LEGS || this.transformType == ItemTransformType.FEET) {
            meta.set(EntityArmorStandHandle.DATA_POSE_LEG_LEFT, rotation);
            meta.set(EntityArmorStandHandle.DATA_POSE_LEG_RIGHT, rotation);
        }
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
    }

    @Override
    public void onTick() {
    }

}
