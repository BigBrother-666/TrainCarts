package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntityHead;

/**
 * A type of entity that can be spectated, that has a particular appearance
 * when the player views himself in third-person (F5) view.
 */
public abstract class FirstPersonSpectatedEntity {
    protected final CartAttachmentSeat seat;
    protected final FirstPersonViewSpectator view;
    protected final VehicleMountController vmc;
    protected final Player player;

    public FirstPersonSpectatedEntity(CartAttachmentSeat seat, FirstPersonViewSpectator view, VehicleMountController vmc) {
        this.seat = seat;
        this.view = view;
        this.vmc = vmc;
        this.player = vmc.getPlayer();
    }

    /**
     * Spawns whatever entity needs to be spectated, and starts spectating that entity
     *
     * @param eyeTransform Transformation matrix defining the position and orientation of
     *                     the camera/eye view.
     */
    public abstract void start(Matrix4x4 eyeTransform);

    /**
     * Stops spectating and despawns the entity/others used for spectating
     */
    public abstract void stop();

    /**
     * Updates the first-person view
     *
     * @param eyeTransform Transformation matrix defining the position and orientation of
     *                     the camera/eye view.
     */
    public abstract void updatePosition(Matrix4x4 eyeTransform);

    public abstract void syncPosition(boolean absolute);

    protected static float computeAltPitch(double currPitch, float currAltPitch) {
        currPitch = MathUtil.wrapAngle(currPitch); // Wrap between -180 and 180 degrees

        if (currPitch > 90.0) {
            return 181.0f;
        } else if (currPitch < -90.0) {
            return 179.0f;
        } else {
            return currAltPitch;
        }
    }

    public static FirstPersonSpectatedEntity create(CartAttachmentSeat seat, FirstPersonViewSpectator view, VehicleMountController vmc) {
        // In these two modes the actual player is made invisible
        if (view.getLiveMode() == FirstPersonViewMode.INVISIBLE ||
            view.getLiveMode() == FirstPersonViewMode.THIRD_P)
        {
            return new FirstPersonSpectatedEntityInvisible(seat, view, vmc);
        }

        // View through a floating armorstand. Head will be slightly above where the
        // camera view is.
        if (seat.seated instanceof SeatedEntityHead) {
            return new FirstPersonSpectatedEntityHead(seat, view, vmc);
        }

        // Default mode of showing the player itself
        return new FirstPersonSpectatedEntityPlayer(seat, view, vmc);
    }
}
