package winterwolfsv.transferable_pets;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PetTransferTests {
    @GameTest
    public void transfersAllEligiblePetsWithVanillaFeedback(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            List<TamableAnimal> pets = List.of(
                    fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner),
                    fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner),
                    fixture.pet(EntityTypes.CAT, fixture.owner, fixture.owner),
                    fixture.pet(EntityTypes.PARROT, fixture.owner, fixture.owner));
            for (int i = 0; i < pets.size(); i++) {
                pets.get(i).setCustomName(Component.literal("Pet" + i));
            }
            fixture.clearPackets();
            InteractionResult result = fixture.owner.interactOn(fixture.recipient, InteractionHand.MAIN_HAND, Vec3.ZERO);

            helper.assertTrue(result == InteractionResult.PASS, "Preserve vanilla interaction result");
            for (TamableAnimal pet : pets) {
                fixture.assertTransferred(pet);
                helper.assertTrue(pet.isTame(), "Transfer must not untame the pet");
            }
            helper.assertItemEntityNotPresent(net.minecraft.world.item.Items.LEAD);
            for (RecordingConnection connection : fixture.connections) {
                long hearts = connection.packets.stream()
                        .filter(packet -> packet instanceof ClientboundLevelParticlesPacket particles
                                && particles.particle() == ParticleTypes.HEART && particles.count() == 1)
                        .count();
                helper.assertTrue(hearts == 14L * pets.size(), "Seven hearts on each pet and recipient per transfer");
            }
            for (int i = 0; i < pets.size(); i++) {
                helper.assertTrue(fixture.connections.get(0).messages().get(i).equals("Recipient is now the owner of Pet" + i),
                        "Original owner action-bar text");
                helper.assertTrue(fixture.connections.get(1).messages().get(i).equals("Owner has transferred Pet" + i + " to you"),
                        "Original recipient action-bar text");
            }
        }
        helper.succeed();
    }

    @GameTest
    public void rejectsPetsNotBothOwnedAndLeashedToInitiator(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            ServerPlayer thirdPlayer = fixture.player("Third");
            TamableAnimal eligible = fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner);
            TamableAnimal otherOwner = fixture.pet(EntityTypes.WOLF, thirdPlayer, fixture.owner);
            TamableAnimal otherHolder = fixture.pet(EntityTypes.WOLF, fixture.owner, thirdPlayer);
            TamableAnimal noLeash = fixture.pet(EntityTypes.WOLF, fixture.owner, null);
            TamableAnimal noOwner = fixture.pet(EntityTypes.WOLF, null, fixture.owner);
            var cow = fixture.mob(EntityTypes.COW);
            cow.setLeashedTo(fixture.owner, true);
            fixture.owner.interactOn(fixture.recipient, InteractionHand.MAIN_HAND, Vec3.ZERO);

            fixture.assertTransferred(eligible);
            helper.assertTrue(otherOwner.isOwnedBy(thirdPlayer) && otherOwner.getLeashHolder() == fixture.owner, "Cannot give another player's pet");
            helper.assertTrue(otherHolder.isOwnedBy(fixture.owner) && otherHolder.getLeashHolder() == thirdPlayer, "Cannot transfer another holder's leash");
            helper.assertTrue(noLeash.isOwnedBy(fixture.owner) && !noLeash.isLeashed(), "Unleashed pets stay with owner");
            helper.assertTrue(noOwner.getOwner() == null && noOwner.getLeashHolder() == fixture.owner, "Untamed animals remain unchanged");
            helper.assertTrue(cow.getLeashHolder() == fixture.owner, "Non-tameable animals remain unchanged");
        }
        helper.succeed();
    }

    @GameTest
    public void requiresSneakingMainHandAndPlayerTarget(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            TamableAnimal pet = fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner);
            fixture.clearPackets();
            fixture.owner.setShiftKeyDown(false);
            fixture.owner.interactOn(fixture.recipient, InteractionHand.MAIN_HAND, Vec3.ZERO);
            fixture.assertUnchanged(pet);
            fixture.owner.setShiftKeyDown(true);
            fixture.owner.interactOn(fixture.recipient, InteractionHand.OFF_HAND, Vec3.ZERO);
            fixture.assertUnchanged(pet);
            Entity nonPlayer = helper.spawn(EntityTypes.ITEM_DISPLAY, new Vec3(3, 2, 4));
            fixture.entities.add(nonPlayer);
            fixture.owner.interactOn(nonPlayer, InteractionHand.MAIN_HAND, Vec3.ZERO);
            fixture.assertUnchanged(pet);
            helper.assertTrue(fixture.connections.getFirst().messages().isEmpty(), "No transfer messages for invalid gestures");
        }
        helper.succeed();
    }

    @GameTest
    public void preservesOriginalSearchBox(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            TamableAnimal corner = fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner);
            corner.setPos(fixture.owner.position().add(5.5, 0, 5.5));
            TamableAnimal outside = fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner);
            outside.setPos(fixture.owner.position().add(7, 0, 0));
            fixture.owner.interactOn(fixture.recipient, InteractionHand.MAIN_HAND, Vec3.ZERO);
            fixture.assertTransferred(corner);
            fixture.assertUnchanged(outside);
        }
        helper.succeed();
    }

    @GameTest
    public void spectatorAndNonServerPlayersCannotTransfer(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            TamableAnimal pet = fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner);
            fixture.owner.setGameMode(GameType.SPECTATOR);
            fixture.owner.interactOn(fixture.recipient, InteractionHand.MAIN_HAND, Vec3.ZERO);
            fixture.assertUnchanged(pet);
            var nonServerPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
            nonServerPlayer.setPos(fixture.owner.position());
            nonServerPlayer.setShiftKeyDown(true);
            pet.setOwner(nonServerPlayer);
            pet.setLeashedTo(nonServerPlayer, true);
            nonServerPlayer.interactOn(fixture.recipient, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(pet.isOwnedBy(nonServerPlayer) && pet.getLeashHolder() == nonServerPlayer, "Only the logical server may transfer");
        }
        helper.succeed();
    }

    @GameTest
    public void vanillaInteractionPacketTriggersTransfer(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            TamableAnimal pet = fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner);
            fixture.owner.setShiftKeyDown(false);
            for (int i = 0; i < 60; i++) {
                fixture.owner.connection.tickClientLoadTimeout();
            }
            fixture.owner.connection.handleInteract(new ServerboundInteractPacket(
                    fixture.recipient.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, true));
            fixture.assertTransferred(pet);
        }
        helper.succeed();
    }

    @GameTest
    public void ownershipAndLeashSurviveVanillaSaveLoad(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            TamableAnimal pet = fixture.pet(EntityTypes.WOLF, fixture.owner, fixture.owner);
            pet.setOrderedToSit(true);
            fixture.owner.interactOn(fixture.recipient, InteractionHand.MAIN_HAND, Vec3.ZERO);
            ProblemReporter.Collector problems = new ProblemReporter.Collector();
            TagValueOutput output = TagValueOutput.createWithContext(problems, helper.getLevel().registryAccess());
            pet.saveWithoutId(output);
            TamableAnimal restored = Objects.requireNonNull(EntityTypes.WOLF.create(helper.getLevel(), EntitySpawnReason.LOAD));
            restored.load(TagValueInput.create(problems, helper.getLevel().registryAccess(), output.buildResult()));
            Leashable.tickLeash(helper.getLevel(), restored);

            helper.assertTrue(problems.isEmpty(), "Vanilla serialization errors: " + problems.getReport());
            fixture.assertTransferred(restored);
            helper.assertTrue(restored.isTame() && restored.isOrderedToSit(), "Taming and sitting survive save/load");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private final List<RecordingConnection> connections = new ArrayList<>();
        private final ServerPlayer owner;
        private final ServerPlayer recipient;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            owner = player("Owner");
            recipient = player("Recipient");
            recipient.setPos(owner.position().add(1, 0, 0));
            owner.setShiftKeyDown(true);
        }

        private ServerPlayer player(String name) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
            RecordingConnection connection = new RecordingConnection();
            new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, CommonListenerCookie.createInitial(profile, false));
            player.setPos(helper.absoluteVec(new Vec3(3, 2, 3)));
            helper.getLevel().addNewPlayer(player);
            player.setGameMode(GameType.SURVIVAL);
            connections.add(connection);
            entities.add(player);
            return player;
        }

        private <E extends Mob> E mob(EntityType<E> type) {
            E mob = helper.spawn(type, new Vec3(3, 2, 4));
            mob.setNoAi(true);
            entities.add(mob);
            return mob;
        }

        private <E extends TamableAnimal> E pet(EntityType<E> type, @Nullable ServerPlayer petOwner, @Nullable Entity holder) {
            E pet = mob(type);
            pet.setTame(petOwner != null, true);
            pet.setOwner(petOwner);
            if (holder != null) {
                pet.setLeashedTo(holder, true);
            }
            return pet;
        }

        private void clearPackets() {
            connections.forEach(connection -> connection.packets.clear());
        }

        private void assertTransferred(TamableAnimal pet) {
            helper.assertTrue(pet.isOwnedBy(recipient), "Recipient must own pet");
            helper.assertTrue(pet.getLeashHolder() == recipient, "Recipient must hold leash");
        }

        private void assertUnchanged(TamableAnimal pet) {
            helper.assertTrue(pet.isOwnedBy(owner) && pet.getLeashHolder() == owner, "Ownership and leash must stay with initiator");
        }

        @Override
        public void close() {
            entities.forEach(Entity::discard);
        }
    }

    private static final class RecordingConnection extends Connection {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
            packets.add(packet);
        }

        private List<String> messages() {
            return packets.stream()
                    .filter(packet -> packet instanceof ClientboundSystemChatPacket chat && chat.overlay())
                    .map(packet -> ((ClientboundSystemChatPacket) packet).content().getString())
                    .toList();
        }
    }
}
