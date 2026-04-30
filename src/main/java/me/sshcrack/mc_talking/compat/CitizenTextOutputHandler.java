package me.sshcrack.mc_talking.compat;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/*? if neoforge {*/
import net.neoforged.fml.ModList;
import com.mrbysco.nbt.network.message.AddBubblePayload;
/*?}*/
/*? if forge {*/
/*import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import com.mrbysco.nbt.network.message.AddBubbleMessage;
import com.mrbysco.nbt.network.PacketHandler;
*//*?}*/

/**
 * Either prints a bubble above the citizen's head with the text, or sends a chat message to the player with the text, depending on the compatibility mode.
 */
public class CitizenTextOutputHandler {
    private static final boolean hasNBTMod = ModList.get().isLoaded("nbt");

    public static void outputText(String message, @Nullable Player player, AbstractEntityCitizen entity) {
        if (player != null) {
            player.sendSystemMessage(entity.getDisplayName().copy().append(": ").append(Component.literal(message.trim())));
        }

        if (hasNBTMod) {
            for (Player playerForBubble : entity.level().players()) {
                if (!(playerForBubble instanceof ServerPlayer sPlayer))
                    continue;

                /*? if neoforge {*/
                var payload = new AddBubblePayload(entity.getUUID(), entity.getStringUUID(), message);
                sPlayer.connection.send(payload);
                /*?}*/
                /*? if forge {*/
                /*var payload = new AddBubbleMessage(entity.getUUID(), entity.getStringUUID(), message);
                PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sPlayer), payload);
                *//*?}*/

            }
        }
    }
}
