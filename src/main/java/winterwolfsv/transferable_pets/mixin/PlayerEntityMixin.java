package winterwolfsv.transferable_pets.mixin;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Retain upstream's priority for compatibility with other interaction mixins.
@Mixin(value = Player.class, priority = 999)
public class PlayerEntityMixin {

    @Inject(method = "interactOn", at = @At("TAIL"))
    private void interact(Entity entity, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(entity instanceof Player targetPlayer) || hand != InteractionHand.MAIN_HAND) return;
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer) || !player.isShiftKeyDown()) return;
        ServerLevel world = serverPlayer.level();

        for (Entity localEntity : world.getEntities(player, AABB.ofSize(player.position(), 12, 12, 12), EntitySelector.ENTITY_STILL_ALIVE)) {
            if (!(localEntity instanceof TamableAnimal pet)) continue;
            if (!pet.isLeashed() || pet.getLeashHolder() != player || !pet.isOwnedBy(player)) continue;

            pet.setOwner(targetPlayer);
            pet.removeLeash();
            pet.setLeashedTo(targetPlayer, true);
            showHearts(world, pet);
            showHearts(world, targetPlayer);

            player.sendOverlayMessage(targetPlayer.getDisplayName().copy().append(" is now the owner of ").append(pet.getDisplayName()));
            targetPlayer.sendOverlayMessage(player.getDisplayName().copy().append(" has transferred ").append(pet.getDisplayName()).append(" to you"));
        }
    }

    @Unique
    private void showHearts(ServerLevel world, Entity entity) {
        for (int i = 0; i < 7; i++) {
            double d = world.getRandom().nextGaussian() * 0.02;
            double e = world.getRandom().nextGaussian() * 0.02;
            double f = world.getRandom().nextGaussian() * 0.02;
            world.sendParticles(ParticleTypes.HEART, entity.getRandomX(1.0), entity.getRandomY() + 0.5, entity.getRandomZ(1.0), 1, d, e, f, 1);
        }
    }
}

