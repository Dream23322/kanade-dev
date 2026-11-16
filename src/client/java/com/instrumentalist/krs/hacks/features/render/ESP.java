package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.instrumentalist.krs.utils.render.IrisCompat;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.renderpearl.backend.opengl.GlDevice;
import com.mojang.renderpearl.backend.opengl.GlTextureView;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.NonNull;
import com.instrumentalist.krs.utils.input.Keys;
import org.lwjgl.BufferUtils;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;

public class ESP extends Module {

    @Setting
    private static final BooleanValue local = new BooleanValue("Local", true)
            .describe("Also draws ESP on your own player.");

    @Setting
    private static final BooleanValue silhouette = new BooleanValue("Silhouette", false)
            .describe("Renders entities as solid silhouettes instead of outlines.");

    @Setting
    private static final ListValue silhouetteStyle = new ListValue("Silhouette Style", new String[]{"Hard", "Glow"}, "Hard", silhouette::get)
            .describe("Hard draws the crisp silhouette edge, Glow only draws the soft outer glow.");

    @Setting
    private static final BooleanValue nameTags = new BooleanValue("Name Tags", true)
            .describe("Draws custom name tags above entities.");

    @Setting
    private static final BooleanValue nameTagOnlyPlayers = new BooleanValue("Name Tags Only Players", true, nameTags::get)
            .describe("Only draws name tags for players.");

    @Setting
    private static final BooleanValue nameTagLocal = new BooleanValue("Name Tags Local", false, nameTags::get)
            .describe("Also draws a name tag for your own player.");

    @Setting
    private static final FloatValue nameTagSize = new FloatValue("Name Tag Size", 1f, 0.5f, 2f, nameTags::get)
            .describe("Scales the name tags drawn above entities.");

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final FloatArrayBuilder TRIANGLES = new FloatArrayBuilder(4096);
    private static final FloatArrayBuilder ARMOR_TRIANGLES = new FloatArrayBuilder(2048);
    private static final SilhouetteVertexConsumer SILHOUETTE_CONSUMER = new SilhouetteVertexConsumer();
    private static final Map<NativeImage, AlphaMask> IMAGE_ALPHA_MASKS = new WeakHashMap<>();
    private static final Map<TextureAtlasSprite, AlphaMask> SPRITE_ALPHA_MASKS = new WeakHashMap<>();
    private static final Map<Identifier, TextureAlphaMask> TEXTURE_ALPHA_MASKS = new HashMap<>();
    private static final Map<Item, String> ITEM_TEXTURE_LOCATIONS = new HashMap<>();
    private static final Map<RenderType, ArmorRenderKind> ARMOR_RENDER_TYPES = new IdentityHashMap<>();
    private static final Map<RenderType, Identifier> RENDER_TYPE_TEXTURES = new IdentityHashMap<>();
    private static final int CELL_PATTERN_CACHE_LIMIT = 8192;
    private static final Map<CellPatternKey, int[]> CELL_PATTERNS = new HashMap<>();
    private static int captureWidth = 0;
    private static int captureHeight = 0;
    private static int captureId = 0;
    private static int renderedCaptureId = -1;
    private static volatile boolean enabled = false;
    private static boolean captureOpen = false;
    private static boolean projectionReady = false;
    private static boolean reversedDepth = false;
    private static boolean zeroToOneDepth = false;
    private static Object itemCaptureState = null;
    private static Object equipmentCaptureState = null;
    private static boolean itemCaptureArmor = false;
    private static final Matrix4f IRIS_PROJECTION = new Matrix4f();
    private static boolean irisProjectionSynced = false;
    private static final float BOX_LINE_WIDTH = 0.8f;
    private static final float BOX_LABEL_FONT = 8f;
    private static final float BOX_NAME_FONT = 8f;
    private static final float BOX_HEART_SIZE = 8f;
    private static final float BOX_BAR_WIDTH = 1.5f;
    private static final Color BOX_LINE = new Color(218, 221, 230, 165);
    private static final Color BOX_LINE_SHADOW = new Color(0, 0, 0, 120);
    private static final Color BOX_TEXT = new Color(238, 238, 242, 240);
    private static final Color BOX_HEART = new Color(255, 73, 92, 235);
    private static final Color BOX_HEALTH_BAR = new Color(101, 228, 127, 235);
    private static final Color BOX_ARMOR_BAR = new Color(105, 135, 241, 235);
    private static final Color BOX_LABEL_BACKGROUND = new Color(3, 4, 7, 178);
    private static final float BOX_ARMOR_ICON_SIZE = 10f;
    private static final float BOX_ARMOR_ICON_GAP = 1.5f;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final float[] BOX_PROJECTION = new float[3];
    private static final float SKELETON_LINE_WIDTH = 0.9f;
    private static final float SKELETON_NEAR_W = 1.0e-4f;
    private static final Color SKELETON_LINE = new Color(232, 236, 245, 200);
    private static final Color SKELETON_LINE_SHADOW = new Color(0, 0, 0, 110);
    // Joints follow the humanoid model parts: head centre, neck and pelvis from the body, then limbs.
    private static final int JOINT_HEAD = 0;
    private static final int JOINT_NECK = 1;
    private static final int JOINT_PELVIS = 2;
    private static final int JOINT_SHOULDER_RIGHT = 3;
    private static final int JOINT_HAND_RIGHT = 4;
    private static final int JOINT_SHOULDER_LEFT = 5;
    private static final int JOINT_HAND_LEFT = 6;
    private static final int JOINT_HIP_RIGHT = 7;
    private static final int JOINT_FOOT_RIGHT = 8;
    private static final int JOINT_HIP_LEFT = 9;
    private static final int JOINT_FOOT_LEFT = 10;
    private static final int JOINT_COUNT = 11;
    private static final int[][] SKELETON_BONES = {
            {JOINT_HEAD, JOINT_NECK},
            {JOINT_NECK, JOINT_PELVIS},
            {JOINT_SHOULDER_RIGHT, JOINT_SHOULDER_LEFT},
            {JOINT_SHOULDER_RIGHT, JOINT_HAND_RIGHT},
            {JOINT_SHOULDER_LEFT, JOINT_HAND_LEFT},
            {JOINT_PELVIS, JOINT_HIP_RIGHT},
            {JOINT_PELVIS, JOINT_HIP_LEFT},
            {JOINT_HIP_RIGHT, JOINT_FOOT_RIGHT},
            {JOINT_HIP_LEFT, JOINT_FOOT_LEFT}
    };
    private static final Matrix4f SKELETON_PROJECTION = new Matrix4f();
    private static final PoseStack SKELETON_POSE = new PoseStack();
    private static final Map<ModelPart, float[]> PART_LOCAL_BOUNDS = new WeakHashMap<>();
    private static final PoseStack SKELETON_LOCAL_POSE = new PoseStack();
    private static final Vector3f SKELETON_BONE_POINT = new Vector3f();
    private static final float SKELETON_MODEL_PIXEL = 1f / 16f;
    private static final float[] SKELETON_JOINTS = new float[JOINT_COUNT * 3];
    private static final float[] SKELETON_CLIPS = new float[JOINT_COUNT * 3];
    private static final boolean[] SKELETON_JOINT_VALID = new boolean[JOINT_COUNT];
    private static final float[] SKELETON_POINT_A = new float[2];
    private static final float[] SKELETON_POINT_B = new float[2];
    private static final Vector4f SKELETON_CLIP_VECTOR = new Vector4f();
    private static final Map<Integer, float[]> SKELETON_SEGMENTS = new HashMap<>();
    private static boolean skeletonProjectionReady = false;
    private final ArrayList<EspBox> boxes = new ArrayList<>();
    private final NameTags nameTagsRenderer = new NameTags();

    static boolean showsNameTags() {
        return nameTags.get();
    }

    @Override
    public String description() {
        return "Highlights players and entities through walls and draws name tags";
    }

    public ESP() {
        super("ESP", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    private static final class EspBox {
        final float x;
        final float y;
        final float width;
        final float height;
        final String name;
        final float health;
        final float healthRatio;
        final float armorRatio;
        final String heldItem;
        final ItemStack heldStack;
        final List<ArmorPiece> armor;
        final int entityId;

        EspBox(float x, float y, float width, float height, String name, float health, float healthRatio,
               float armorRatio, String heldItem, ItemStack heldStack, List<ArmorPiece> armor, int entityId) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.name = name;
            this.health = health;
            this.healthRatio = healthRatio;
            this.armorRatio = armorRatio;
            this.heldItem = heldItem;
            this.heldStack = heldStack;
            this.armor = armor;
            this.entityId = entityId;
        }
    }

    private enum ArmorKind {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS
    }

    private record ArmorPiece(ArmorKind kind, Color color, float durability, ItemStack stack) {
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (enabled) {
            NameTags.onlyPlayersEnabled = nameTagOnlyPlayers.get();
            NameTags.localEnabled = nameTagLocal.get();
            NameTags.scale = nameTagSize.get();
            if (nameTags.get())
                nameTagsRenderer.collect(event);
            else
                nameTagsRenderer.reset();
        }

        boxes.clear();
        if (!enabled || mc.player == null || mc.level == null || !RenderUtil.shouldRenderWorldHudOverlays())
            return;

        float partialTicks = event.partialTicks;
        float scaleX = NanoVGManager.getScaledScreenWidth() / Math.max(1, mc.getWindow().getWidth());
        float scaleY = NanoVGManager.getScaledScreenHeight() / Math.max(1, mc.getWindow().getHeight());
        boolean renderLocal = local.get() && (!mc.options.getCameraType().isFirstPerson() || ModuleManager.getModuleState(Freecam.class));
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();

        for (Player player : mc.level.players()) {
            if (player == null || player.isRemoved())
                continue;
            if (player == mc.player && !renderLocal)
                continue;
            if (!RenderUtil.INSTANCE.isEntityInRenderedWorldFrustum(player, partialTicks))
                continue;

            Vec3 lerpedPos = RenderUtil.INSTANCE.getLerpedPos(player, partialTicks);
            AABB box = player.getBoundingBox().move(
                    lerpedPos.x - player.getX(),
                    lerpedPos.y - player.getY(),
                    lerpedPos.z - player.getZ()
            );

            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            int projected = 0;
            for (int corner = 0; corner < 8; corner++) {
                double x = (corner & 1) == 0 ? box.minX : box.maxX;
                double y = (corner & 2) == 0 ? box.minY : box.maxY;
                double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
                if (!RenderUtil.INSTANCE.renderedWorldToScreen(x, y, z, BOX_PROJECTION))
                    continue;

                float screenX = BOX_PROJECTION[0] * scaleX;
                float screenY = BOX_PROJECTION[1] * scaleY;
                if (!Float.isFinite(screenX) || !Float.isFinite(screenY))
                    continue;

                minX = Math.min(minX, screenX);
                minY = Math.min(minY, screenY);
                maxX = Math.max(maxX, screenX);
                maxY = Math.max(maxY, screenY);
                projected++;
            }

            if (projected < 4)
                continue;

            minX = Math.clamp(minX - 1.5f, 0f, screenWidth);
            minY = Math.clamp(minY - 1.5f, 0f, screenHeight);
            maxX = Math.clamp(maxX + 1.5f, 0f, screenWidth);
            maxY = Math.clamp(maxY + 1.5f, 0f, screenHeight);
            if (maxX - minX < 2f || maxY - minY < 2f)
                continue;

            ItemStack held = player.getMainHandItem();
            if (held.isEmpty())
                held = player.getOffhandItem();
            String heldItem = held.isEmpty() ? "" : held.getHoverName().getString();
            float health = Math.max(0f, player.getHealth());
            float maxHealth = Math.max(1f, player.getMaxHealth());
            float healthRatio = Math.clamp(health / maxHealth, 0f, 1f);
            List<ArmorPiece> armor = new ArrayList<>(4);
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty())
                    continue;

                armor.add(new ArmorPiece(armorKind(slot), armorColor(stack), armorDurability(stack), stack));
            }
            boxes.add(new EspBox(minX, minY, maxX - minX, maxY - minY,
                    player.getName().getString(), health, healthRatio,
                    Math.clamp(player.getArmorValue() / 20f, 0f, 1f), heldItem, held, armor, player.getId()));
        }
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (!enabled || Client.nanoVgManager == null)
            return;

        if (nameTags.get()) {
            NameTags renderer = nameTagsRenderer;
            Client.nanoVgManager.loadWorldAnchored(renderer::renderHud);
        }

        if (boxes.isEmpty())
            return;

        Client.nanoVgManager.loadWorldAnchored(vg -> {
            for (EspBox box : boxes) {
                drawBox(vg, box);
                drawArmorRow(vg, box);
            }
        });
    }

    private static void drawBox(NVGU vg, EspBox box) {
        float x = box.x;
        float y = box.y;
        float right = x + box.width;
        float bottom = y + box.height;
        drawSkeleton(vg, box);
        // Atmosphere uses four short, muted corner brackets rather than a full outline.
        float horizontal = Math.min(box.width / 2f, Math.max(2f, box.width * 0.25f));
        float vertical = Math.min(box.height / 2f, Math.max(2f, box.height * 0.13f));
        drawCorner(vg, x, y, horizontal, vertical, 1f, 1f);
        drawCorner(vg, right, y, horizontal, vertical, -1f, 1f);
        drawCorner(vg, x, bottom, horizontal, vertical, 1f, -1f);
        drawCorner(vg, right, bottom, horizontal, vertical, -1f, -1f);

        // Health grows from the bottom up, just outside the left edge.
        float healthX = x - 5f;
        vg.rectangle(healthX - 0.5f, y, BOX_BAR_WIDTH + 1f, box.height, new Color(0, 0, 0, 165));
        float healthHeight = box.height * box.healthRatio;
        if (healthHeight > 0.01f)
            vg.rectangle(healthX, bottom - healthHeight, BOX_BAR_WIDTH, healthHeight, BOX_HEALTH_BAR);

        // The blue lower bar represents the player's actual armour points (0–20).
        if (box.armorRatio > 0.001f) {
            float barY = bottom + 3f;
            vg.rectangle(x, barY - 0.5f, box.width, BOX_BAR_WIDTH + 1f, new Color(0, 0, 0, 165));
            vg.rectangle(x, barY, box.width * box.armorRatio, BOX_BAR_WIDTH, BOX_ARMOR_BAR);
        }

        if (!nameTags.get())
            drawNameTag(vg, box);
        if (box.heldItem.isEmpty())
            return;

        float iconSize = 9f;
        float iconGap = 2f;
        boolean hasIcon = !itemTextureLocation(box.heldStack).isEmpty();
        float iconSpace = hasIcon ? iconSize + iconGap : 0f;
        float textWidth = NVGFonts.INTER.getWidth(box.heldItem, BOX_LABEL_FONT);
        float contentWidth = iconSpace + textWidth;
        float labelWidth = contentWidth + 6f;
        float labelHeight = 11f;
        float labelX = x + (box.width - labelWidth) / 2f;
        float labelY = Math.min(bottom + 8f, NanoVGManager.getScaledScreenHeight() - labelHeight);
        vg.rectangle(labelX, labelY, labelWidth, labelHeight, BOX_LABEL_BACKGROUND);

        float contentX = labelX + (labelWidth - contentWidth) / 2f;
        if (hasIcon)
            drawItemTexture(vg, box.heldStack, contentX, labelY + (labelHeight - iconSize) / 2f, iconSize);

        NVGFonts.INTER.drawText(box.heldItem, contentX + iconSpace, labelY + 2f, BOX_LABEL_FONT,
                BOX_TEXT, Alignment.LEFT_TOP, true);
    }

    // Draws the stick-figure skeleton captured from the humanoid model parts this frame.
    private static void drawSkeleton(NVGU vg, EspBox box) {
        float[] segments = SKELETON_SEGMENTS.get(box.entityId);
        if (segments == null)
            return;

        for (int i = 0; i + 3 < segments.length; i += 4) {
            float x1 = segments[i];
            float y1 = segments[i + 1];
            float x2 = segments[i + 2];
            float y2 = segments[i + 3];
            vg.line(x1, y1, x2, y2, SKELETON_LINE_WIDTH + 1.2f, SKELETON_LINE_SHADOW);
            vg.line(x1, y1, x2, y2, SKELETON_LINE_WIDTH, SKELETON_LINE);
        }
    }

    public static void captureSkeleton(Model<?> model, Object state, PoseStack poseStack) {
        if (!enabled || mc.player == null || mc.level == null || poseStack == null)
            return;
        if (!(model instanceof HumanoidModel<?> humanoid))
            return;

        Entity entity = getCapturedEntity(state);
        if (!(entity instanceof Player player) || player.isRemoved())
            return;
        if (player instanceof LocalPlayer && (!local.get()
                || mc.options.getCameraType().isFirstPerson() && !ModuleManager.getModuleState(Freecam.class)))
            return;
        if (!skeletonProjectionReady)
            return;

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        if (screenWidth < 1f || screenHeight < 1f)
            return;

        ModelPart rootPart = model.root();
        float[] headBounds = partLocalBounds(humanoid.head);
        float[] bodyBounds = partLocalBounds(humanoid.body);
        float[] rightArmBounds = partLocalBounds(humanoid.rightArm);
        float[] leftArmBounds = partLocalBounds(humanoid.leftArm);
        float[] rightLegBounds = partLocalBounds(humanoid.rightLeg);
        float[] leftLegBounds = partLocalBounds(humanoid.leftLeg);
        if (rootPart == null || headBounds == null || bodyBounds == null
                || rightArmBounds == null || leftArmBounds == null
                || rightLegBounds == null || leftLegBounds == null)
            return;

        Arrays.fill(SKELETON_JOINT_VALID, false);
        setBoneJoint(poseStack, rootPart, humanoid.head,
                boneCenter(headBounds, 0), boneCenter(headBounds, 1), boneCenter(headBounds, 2), JOINT_HEAD);
        // The body pivot sits on the neck line and its cube's far end is the pelvis.
        setBoneJoint(poseStack, rootPart, humanoid.body, 0f, 0f, 0f, JOINT_NECK);
        setBoneJoint(poseStack, rootPart, humanoid.body, 0f, bodyBounds[4], 0f, JOINT_PELVIS);
        // Limbs anchor on their pivot, which is fixed in the body frame, so swinging never moves the joint.
        setBoneJoint(poseStack, rootPart, humanoid.rightArm, 0f, 0f, 0f, JOINT_SHOULDER_RIGHT);
        setBoneJoint(poseStack, rootPart, humanoid.rightArm,
                boneCenter(rightArmBounds, 0), rightArmBounds[4], boneCenter(rightArmBounds, 2), JOINT_HAND_RIGHT);
        setBoneJoint(poseStack, rootPart, humanoid.leftArm, 0f, 0f, 0f, JOINT_SHOULDER_LEFT);
        setBoneJoint(poseStack, rootPart, humanoid.leftArm,
                boneCenter(leftArmBounds, 0), leftArmBounds[4], boneCenter(leftArmBounds, 2), JOINT_HAND_LEFT);
        setBoneJoint(poseStack, rootPart, humanoid.rightLeg, 0f, 0f, 0f, JOINT_HIP_RIGHT);
        setBoneJoint(poseStack, rootPart, humanoid.rightLeg,
                boneCenter(rightLegBounds, 0), rightLegBounds[4], boneCenter(rightLegBounds, 2), JOINT_FOOT_RIGHT);
        setBoneJoint(poseStack, rootPart, humanoid.leftLeg, 0f, 0f, 0f, JOINT_HIP_LEFT);
        setBoneJoint(poseStack, rootPart, humanoid.leftLeg,
                boneCenter(leftLegBounds, 0), leftLegBounds[4], boneCenter(leftLegBounds, 2), JOINT_FOOT_LEFT);

        for (int joint = 0; joint < JOINT_COUNT; joint++) {
            int offset = joint * 3;
            SKELETON_CLIP_VECTOR.set(SKELETON_JOINTS[offset], SKELETON_JOINTS[offset + 1],
                    SKELETON_JOINTS[offset + 2], 1f).mul(SKELETON_PROJECTION);
            SKELETON_CLIPS[offset] = SKELETON_CLIP_VECTOR.x;
            SKELETON_CLIPS[offset + 1] = SKELETON_CLIP_VECTOR.y;
            SKELETON_CLIPS[offset + 2] = SKELETON_CLIP_VECTOR.w;
        }

        float[] segments = new float[SKELETON_BONES.length * 4];
        int count = 0;
        for (int[] bone : SKELETON_BONES) {
            if (!skeletonSegment(bone[0], bone[1], screenWidth, screenHeight))
                continue;

            segments[count++] = SKELETON_POINT_A[0];
            segments[count++] = SKELETON_POINT_A[1];
            segments[count++] = SKELETON_POINT_B[0];
            segments[count++] = SKELETON_POINT_B[1];
        }

        if (count == 0)
            SKELETON_SEGMENTS.remove(entity.getId());
        else
            SKELETON_SEGMENTS.put(entity.getId(), Arrays.copyOf(segments, count));
    }

    private static float boneCenter(float[] bounds, int axis) {
        return (bounds[axis] + bounds[axis + 3]) * 0.5f;
    }

    // Transforms a bone-space offset (pivot-relative, in blocks) through the part's own frame of the model pose.
    private static void setBoneJoint(PoseStack source, ModelPart root, ModelPart part,
                                     float localX, float localY, float localZ, int joint) {
        SKELETON_POSE.last().set(source.last());
        root.translateAndRotate(SKELETON_POSE);
        part.translateAndRotate(SKELETON_POSE);
        SKELETON_BONE_POINT.set(localX, localY, localZ);
        SKELETON_POSE.last().pose().transformPosition(SKELETON_BONE_POINT);
        setJoint(joint, SKELETON_BONE_POINT.x, SKELETON_BONE_POINT.y, SKELETON_BONE_POINT.z);
    }

    private static void setJoint(int joint, float x, float y, float z) {
        int offset = joint * 3;
        SKELETON_JOINTS[offset] = x;
        SKELETON_JOINTS[offset + 1] = y;
        SKELETON_JOINTS[offset + 2] = z;
        SKELETON_JOINT_VALID[joint] = true;
    }

    // Cube coordinates are pivot-relative and unrotated, which is exactly the bone space the joints need.
    private static float[] partLocalBounds(ModelPart part) {
        if (part == null || part.isEmpty())
            return null;

        float[] cached = PART_LOCAL_BOUNDS.get(part);
        if (cached != null)
            return cached;

        float[] bounds = new float[6];
        bounds[0] = bounds[1] = bounds[2] = Float.POSITIVE_INFINITY;
        bounds[3] = bounds[4] = bounds[5] = Float.NEGATIVE_INFINITY;
        SKELETON_LOCAL_POSE.setIdentity();
        part.visit(SKELETON_LOCAL_POSE, (pose, name, index, cube) -> {
            bounds[0] = Math.min(bounds[0], cube.minX * SKELETON_MODEL_PIXEL);
            bounds[1] = Math.min(bounds[1], cube.minY * SKELETON_MODEL_PIXEL);
            bounds[2] = Math.min(bounds[2], cube.minZ * SKELETON_MODEL_PIXEL);
            bounds[3] = Math.max(bounds[3], cube.maxX * SKELETON_MODEL_PIXEL);
            bounds[4] = Math.max(bounds[4], cube.maxY * SKELETON_MODEL_PIXEL);
            bounds[5] = Math.max(bounds[5], cube.maxZ * SKELETON_MODEL_PIXEL);
        });

        if (!(bounds[0] <= bounds[3]))
            return null;

        PART_LOCAL_BOUNDS.put(part, bounds);
        return bounds;
    }

    private static boolean skeletonSegment(int from, int to, float screenWidth, float screenHeight) {
        if (!SKELETON_JOINT_VALID[from] || !SKELETON_JOINT_VALID[to])
            return false;
        if (!skeletonPoint(from, to, screenWidth, screenHeight, SKELETON_POINT_A))
            return false;
        if (!skeletonPoint(to, from, screenWidth, screenHeight, SKELETON_POINT_B))
            return false;

        return clipSkeletonSegment(SKELETON_POINT_A, SKELETON_POINT_B, screenWidth, screenHeight);
    }

    // A joint behind the camera is pulled onto the near plane along its bone so the line stays sane up close.
    private static boolean skeletonPoint(int joint, int other, float screenWidth, float screenHeight, float[] output) {
        int offset = joint * 3;
        int otherOffset = other * 3;
        float clipX = SKELETON_CLIPS[offset];
        float clipY = SKELETON_CLIPS[offset + 1];
        float clipW = SKELETON_CLIPS[offset + 2];
        if (!Float.isFinite(clipX) || !Float.isFinite(clipY) || !Float.isFinite(clipW))
            return false;

        if (clipW <= SKELETON_NEAR_W) {
            float otherX = SKELETON_CLIPS[otherOffset];
            float otherY = SKELETON_CLIPS[otherOffset + 1];
            float otherW = SKELETON_CLIPS[otherOffset + 2];
            if (!Float.isFinite(otherX) || !Float.isFinite(otherY) || otherW <= SKELETON_NEAR_W)
                return false;

            float delta = (SKELETON_NEAR_W - clipW) / (otherW - clipW);
            if (!Float.isFinite(delta))
                return false;

            delta = Math.clamp(delta, 0f, 1f);
            clipX += (otherX - clipX) * delta;
            clipY += (otherY - clipY) * delta;
            clipW = SKELETON_NEAR_W;
        }

        output[0] = (clipX / clipW * 0.5f + 0.5f) * screenWidth;
        output[1] = (1f - (clipY / clipW * 0.5f + 0.5f)) * screenHeight;
        return Float.isFinite(output[0]) && Float.isFinite(output[1]);
    }

    // Liang-Barsky clip to the viewport; a near-plane joint can otherwise land thousands of pixels away.
    private static boolean clipSkeletonSegment(float[] from, float[] to, float width, float height) {
        float deltaX = to[0] - from[0];
        float deltaY = to[1] - from[1];
        float start = 0f;
        float end = 1f;

        for (int boundary = 0; boundary < 4; boundary++) {
            float direction = switch (boundary) {
                case 0 -> -deltaX;
                case 1 -> deltaX;
                case 2 -> -deltaY;
                default -> deltaY;
            };
            float distance = switch (boundary) {
                case 0 -> from[0];
                case 1 -> width - from[0];
                case 2 -> from[1];
                default -> height - from[1];
            };

            if (Math.abs(direction) < 1.0e-6f) {
                if (distance < 0f)
                    return false;
                continue;
            }

            float ratio = distance / direction;
            if (direction < 0f) {
                if (ratio > end)
                    return false;
                start = Math.max(start, ratio);
            } else {
                if (ratio < start)
                    return false;
                end = Math.min(end, ratio);
            }
        }

        if (start > end)
            return false;

        float clippedStartX = from[0] + deltaX * start;
        float clippedStartY = from[1] + deltaY * start;
        to[0] = from[0] + deltaX * end;
        to[1] = from[1] + deltaY * end;
        from[0] = clippedStartX;
        from[1] = clippedStartY;
        return true;
    }

    private static void drawCorner(NVGU vg, float x, float y, float horizontal, float vertical,
                                   float directionX, float directionY) {
        float horizontalX = directionX > 0f ? x : x - horizontal;
        float verticalY = directionY > 0f ? y : y - vertical;
        float lineX = directionX > 0f ? x : x - BOX_LINE_WIDTH;
        float lineY = directionY > 0f ? y : y - BOX_LINE_WIDTH;
        // A faint shadow keeps the pale brackets visible on bright blocks.
        vg.rectangle(horizontalX, lineY - 0.5f, horizontal, BOX_LINE_WIDTH + 1f, BOX_LINE_SHADOW);
        vg.rectangle(lineX - 0.5f, verticalY, BOX_LINE_WIDTH + 1f, vertical, BOX_LINE_SHADOW);
        vg.rectangle(horizontalX, lineY, horizontal, BOX_LINE_WIDTH, BOX_LINE);
        vg.rectangle(lineX, verticalY, BOX_LINE_WIDTH, vertical, BOX_LINE);
    }

    private static void drawNameTag(NVGU vg, EspBox box) {
        String healthText = formatHealth(box.health);
        float nameWidth = NVGFonts.INTER_MEDIUM.getWidth(box.name, BOX_NAME_FONT);
        float healthWidth = NVGFonts.INTER_MEDIUM.getWidth(healthText, BOX_NAME_FONT);
        float heartWidth = NVGFonts.ICON.getWidth(MaterialIcon.HEART, BOX_HEART_SIZE);
        float padding = 3f;
        float labelWidth = padding * 2f + nameWidth + 3f + healthWidth + 2f + heartWidth;
        float labelHeight = 11f;
        float labelX = box.x + (box.width - labelWidth) / 2f;
        float labelY = Math.max(0f, box.y - labelHeight - 2f);
        vg.rectangle(labelX, labelY, labelWidth, labelHeight, BOX_LABEL_BACKGROUND);

        float textY = labelY + 2f;
        NVGFonts.INTER_MEDIUM.drawText(box.name, labelX + padding, textY, BOX_NAME_FONT,
                BOX_TEXT, Alignment.LEFT_TOP, true);
        float heartX = labelX + labelWidth - padding - heartWidth;
        NVGFonts.INTER_MEDIUM.drawText(healthText, heartX - 2f, textY, BOX_NAME_FONT,
                BOX_TEXT, Alignment.RIGHT_TOP, true);
        NVGFonts.ICON.drawText(MaterialIcon.HEART, heartX, labelY + 1.5f, BOX_HEART_SIZE,
                BOX_HEART, Alignment.LEFT_TOP, true);
    }

    private static float armorRowWidth(EspBox box) {
        return box.armor.size() * BOX_ARMOR_ICON_SIZE
                + Math.max(0, box.armor.size() - 1) * BOX_ARMOR_ICON_GAP;
    }

    private static float armorRowX(EspBox box) {
        return box.x + (box.width - armorRowWidth(box)) / 2f;
    }

    private static float armorRowY(EspBox box) {
        float labelY = Math.max(0f, box.y - 13f);
        float rowY = labelY - BOX_ARMOR_ICON_SIZE - 2f;
        // If the player is clipped at the top, keep the equipment icons visible.
        return rowY < 0f ? labelY + 12f : rowY;
    }

    private static void drawArmorRow(NVGU vg, EspBox box) {
        if (box.armor.isEmpty())
            return;
        float rowX = armorRowX(box);
        float rowY = armorRowY(box);
        for (int i = 0; i < box.armor.size(); i++) {
            ArmorPiece piece = box.armor.get(i);
            float iconX = rowX + i * (BOX_ARMOR_ICON_SIZE + BOX_ARMOR_ICON_GAP);
            if (!drawItemTexture(vg, piece.stack(), iconX, rowY, BOX_ARMOR_ICON_SIZE))
                drawArmorIcon(vg, piece.kind(), iconX, rowY, piece.color());
        }
    }

    private static String itemTextureLocation(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return "";

        Item item = stack.getItem();
        String cached = ITEM_TEXTURE_LOCATIONS.get(item);
        if (cached != null)
            return cached;

        String location = "";
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null) {
            for (String folder : new String[]{"item", "block"}) {
                Identifier candidate = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + folder + "/" + id.getPath() + ".png");
                if (isSquareTexture(candidate)) {
                    location = candidate.toString();
                    break;
                }
            }
        }

        ITEM_TEXTURE_LOCATIONS.put(item, location);
        return location;
    }

    private static boolean isSquareTexture(Identifier texture) {
        Optional<Resource> resource = mc.getResourceManager().getResource(texture);
        if (resource.isEmpty())
            return false;

        // Animated textures are stored as horizontal frame strips and would stretch when drawn as an icon.
        try (InputStream stream = resource.get().open()) {
            BufferedImage image = ImageIO.read(stream);
            return image != null && image.getWidth() == image.getHeight();
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean drawItemTexture(NVGU vg, ItemStack stack, float x, float y, float size) {
        String location = itemTextureLocation(stack);
        if (location.isEmpty())
            return false;

        String key = "esp_item/" + location;
        if (!vg.hasTexture(key)) {
            Identifier texture = Identifier.tryParse(location);
            if (texture == null)
                return false;

            Optional<Resource> resource = mc.getResourceManager().getResource(texture);
            if (resource.isEmpty())
                return false;

            try (InputStream stream = resource.get().open()) {
                vg.createTexture(key, stream);
            } catch (IOException | RuntimeException ignored) {
                return false;
            }
        }

        vg.rectangle(x, y, size, size, vg.texture(key, x, y, size, size));
        return true;
    }

    private static void drawArmorIcon(NVGU vg, ArmorKind kind, float x, float y, Color color) {
        Color shade = new Color(
                Math.max(0, color.getRed() - 58),
                Math.max(0, color.getGreen() - 58),
                Math.max(0, color.getBlue() - 58),
                color.getAlpha()
        );

        switch (kind) {
            case HELMET -> {
                armorPixel(vg, x, y, 2, 0, 3, 1, color);
                armorPixel(vg, x, y, 1, 1, 5, 1, color);
                armorPixel(vg, x, y, 0, 2, 7, 3, color);
                armorPixel(vg, x, y, 1, 5, 5, 1, color);
                armorPixel(vg, x, y, 2, 6, 3, 1, color);
                armorPixel(vg, x, y, 1, 3, 5, 1, shade);
            }
            case CHESTPLATE -> {
                armorPixel(vg, x, y, 1, 0, 2, 2, color);
                armorPixel(vg, x, y, 4, 0, 2, 2, color);
                armorPixel(vg, x, y, 0, 1, 7, 4, color);
                armorPixel(vg, x, y, 1, 5, 5, 1, color);
                armorPixel(vg, x, y, 2, 1, 3, 1, shade);
                armorPixel(vg, x, y, 3, 2, 1, 3, shade);
            }
            case LEGGINGS -> {
                armorPixel(vg, x, y, 0, 0, 7, 2, color);
                armorPixel(vg, x, y, 0, 2, 2, 5, color);
                armorPixel(vg, x, y, 5, 2, 2, 5, color);
                armorPixel(vg, x, y, 2, 2, 3, 1, color);
                armorPixel(vg, x, y, 3, 3, 1, 2, shade);
            }
            case BOOTS -> {
                armorPixel(vg, x, y, 0, 0, 2, 5, color);
                armorPixel(vg, x, y, 5, 0, 2, 5, color);
                armorPixel(vg, x, y, 0, 5, 3, 2, color);
                armorPixel(vg, x, y, 4, 5, 3, 2, color);
                armorPixel(vg, x, y, 2, 5, 1, 1, shade);
                armorPixel(vg, x, y, 4, 5, 1, 1, shade);
            }
        }
    }

    private static void armorPixel(NVGU vg, float iconX, float iconY,
                                   float pixelX, float pixelY, float width, float height, Color color) {
        float scale = BOX_ARMOR_ICON_SIZE / 7f;
        vg.rectangle(iconX + pixelX * scale, iconY + pixelY * scale, width * scale, height * scale, color);
    }

    private static ArmorKind armorKind(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorKind.HELMET;
            case CHEST -> ArmorKind.CHESTPLATE;
            case LEGS -> ArmorKind.LEGGINGS;
            default -> ArmorKind.BOOTS;
        };
    }

    private static Color armorColor(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("netherite"))
            return new Color(142, 128, 141, 240);
        if (path.contains("diamond"))
            return new Color(112, 231, 224, 240);
        if (path.contains("gold"))
            return new Color(242, 201, 76, 240);
        if (path.contains("iron"))
            return new Color(218, 218, 224, 240);
        if (path.contains("chain"))
            return new Color(171, 178, 188, 240);
        if (path.contains("leather"))
            return new Color(177, 116, 77, 240);
        if (path.contains("turtle"))
            return new Color(101, 193, 139, 240);
        if (path.contains("copper"))
            return new Color(214, 137, 96, 240);
        return new Color(226, 226, 232, 240);
    }

    private static float armorDurability(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0)
            return 1f;
        return Math.clamp((stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage(), 0f, 1f);
    }

    private static Color durabilityColor(float durability) {
        if (durability > 0.55f)
            return new Color(112, 224, 156, 240);
        if (durability > 0.25f)
            return new Color(242, 201, 76, 240);
        return new Color(255, 86, 96, 240);
    }

    private static String formatHealth(float health) {
        float rounded = Math.round(Math.max(0f, health) * 10f) / 10f;
        if (Math.abs(rounded - Math.round(rounded)) < 0.01f)
            return Integer.toString(Math.round(rounded));
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        boxes.clear();
        nameTagsRenderer.reset();
        resetCapture();
    }

    @Override
    public void onWorld(WorldEvent event) {
        boxes.clear();
        nameTagsRenderer.reset();
        resetCapture();
    }

    public static void beginCapture() {
        SKELETON_SEGMENTS.clear();
        if (!enabled || !silhouette.get()) {
            captureOpen = false;
            projectionReady = false;
            itemCaptureState = null;
            equipmentCaptureState = null;
            itemCaptureArmor = false;
            return;
        }

        TRIANGLES.clear();
        ARMOR_TRIANGLES.clear();
        projectionReady = false;
        captureWidth = 0;
        captureHeight = 0;
        captureId++;
        captureOpen = true;
        reversedDepth = false;
        zeroToOneDepth = false;
        irisProjectionSynced = false;

        updateProjection();
    }

    public static <S> void captureModel(ModelFeatureRenderer.Submit<S> modelSubmit, PoseStack poseStack, RenderType renderType) {
        if (!canCapture() || modelSubmit == null || poseStack == null)
            return;

        boolean captureModelState = shouldCaptureState(modelSubmit.state());
        boolean captureEquipmentState = !captureModelState && equipmentCaptureState != null;
        if (!captureModelState && !captureEquipmentState)
            return;

        if (!projectionReady && !updateProjection())
            return;
        syncIrisProjection();

        Entity entity = getCapturedEntity(captureModelState ? modelSubmit.state() : equipmentCaptureState);
        Model<? super S> model = modelSubmit.model();
        if (captureEquipmentState) {
            VertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                    VIEW_PROJECTION,
                    captureWidth,
                    captureHeight,
                    alphaMaskFor(renderType),
                    ARMOR_TRIANGLES
            );
            model.renderToBuffer(poseStack, consumer, modelSubmit.lightCoords(), modelSubmit.overlayCoords(), 0xFFFFFFFF);
            return;
        }

        ArmorRenderKind armorRenderKind = armorRenderKind(renderType);
        if (armorRenderKind != ArmorRenderKind.NONE) {
            if (armorRenderKind == ArmorRenderKind.OVERLAY)
                return;

            VertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                    VIEW_PROJECTION,
                    captureWidth,
                    captureHeight,
                    alphaMaskFor(renderType),
                    ARMOR_TRIANGLES
            );
            model.renderToBuffer(poseStack, consumer, modelSubmit.lightCoords(), modelSubmit.overlayCoords(), 0xFFFFFFFF);
            return;
        }

        if (!(model instanceof PlayerModel))
            return;

        VertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                VIEW_PROJECTION,
                captureWidth,
                captureHeight,
                alphaMaskFor(model, entity),
                TRIANGLES
        );

        model.renderToBuffer(poseStack, consumer, modelSubmit.lightCoords(), modelSubmit.overlayCoords(), 0xFFFFFFFF);
    }

    public static <S> void captureSubmittedModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords) {
        if (!canCapture() || equipmentCaptureState == null || model == null || state == null || poseStack == null)
            return;
        if (state instanceof EntityRenderState)
            return;

        if (!projectionReady && !updateProjection())
            return;
        syncIrisProjection();

        VertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                VIEW_PROJECTION,
                captureWidth,
                captureHeight,
                alphaMaskFor(renderType),
                ARMOR_TRIANGLES
        );
        model.setupAnim(state);
        model.renderToBuffer(poseStack, consumer, lightCoords, overlayCoords, 0xFFFFFFFF);
    }

    public static void endCapture() {
        captureOpen = false;
        itemCaptureState = null;
        equipmentCaptureState = null;
        itemCaptureArmor = false;
    }

    public static void beginItemCapture(Object state) {
        itemCaptureState = canCapture() && shouldCaptureState(state) ? state : null;
        itemCaptureArmor = false;
    }

    public static void endItemCapture() {
        itemCaptureState = null;
        itemCaptureArmor = false;
    }

    public static void beginEquipmentCapture(Object state) {
        boolean shouldCapture = canCapture() && shouldCaptureState(state);
        itemCaptureState = shouldCapture ? state : null;
        equipmentCaptureState = shouldCapture ? state : null;
        itemCaptureArmor = shouldCapture;
    }

    public static void endEquipmentCapture() {
        itemCaptureState = null;
        equipmentCaptureState = null;
        itemCaptureArmor = false;
    }

    public static void captureItem(PoseStack poseStack, List<BakedQuad> quads) {
        if (!canCapture() || itemCaptureState == null || poseStack == null || quads == null || quads.isEmpty())
            return;

        if (!projectionReady && !updateProjection())
            return;
        syncIrisProjection();

        SilhouetteVertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                VIEW_PROJECTION,
                captureWidth,
                captureHeight,
                null,
                itemCaptureArmor ? ARMOR_TRIANGLES : TRIANGLES
        );
        Matrix4fc poseMatrix = poseStack.last().pose();
        for (BakedQuad quad : quads) {
            consumer.addBakedQuad(poseMatrix, quad, alphaMaskFor(quad));
        }
    }

    public static void captureCustomModel(Object state, PoseStack.Pose pose, Identifier texture, float[] positions, float[] uvs, int[] indices) {
        if (!canCapture() || pose == null || !shouldCaptureState(state))
            return;

        if (!projectionReady && !updateProjection())
            return;
        syncIrisProjection();

        SilhouetteVertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                VIEW_PROJECTION,
                captureWidth,
                captureHeight,
                alphaMaskFor(texture),
                TRIANGLES
        );
        Matrix4fc poseMatrix = pose.pose();
        for (int i = 0; i + 2 < indices.length; i += 3)
            consumer.addMeshTriangle(poseMatrix, positions, uvs, indices[i], indices[i + 1], indices[i + 2]);
    }

    public static void renderCapturedShadow() {
        if (!enabled || !silhouette.get() || renderedCaptureId == captureId)
            return;
        if (mc.player == null || mc.level == null)
            return;
        if (captureWidth <= 0 || captureHeight <= 0 || TRIANGLES.vertexCount() < 3)
            return;

        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        if (target == null || target.width <= 0 || target.height <= 0)
            return;

        if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
            renderedCaptureId = captureId;
            GraphicsApiCompatibility.renderOffscreenLayer(
                    GraphicsApiCompatibility.Layer.ESP,
                    () -> drawCapturedShadow(0, 0, 0, 0)
            );
            return;
        }

        MainTargetHandle targetHandle = mainTargetHandle(target);
        if (targetHandle == null)
            return;

        renderedCaptureId = captureId;
        drawCapturedShadow(
                targetHandle.framebuffer(),
                target.width,
                target.height,
                targetHandle.depthTexture()
        );
    }

    private static void drawCapturedShadow(int targetFramebuffer, int targetWidth, int targetHeight, int sourceDepthTexture) {
        boolean glowOnly = silhouetteStyle.get().equalsIgnoreCase("glow");
        float edgeAlpha = glowOnly ? 0f : 80f / 255f;
        float throughBlockAlpha = glowOnly ? 0f : 130f / 255f;
        Shader2DRenderer.INSTANCE.drawSilhouetteShadow(
                captureWidth,
                captureHeight,
                TRIANGLES.toBuffer(),
                TRIANGLES.vertexCount(),
                ARMOR_TRIANGLES.vertexCount() >= 3 ? ARMOR_TRIANGLES.toBuffer() : null,
                ARMOR_TRIANGLES.vertexCount(),
                80f,
                3f,
                edgeAlpha,
                edgeAlpha,
                throughBlockAlpha,
                reversedDepth,
                Color.WHITE,
                targetFramebuffer,
                targetWidth,
                targetHeight,
                sourceDepthTexture
        );
    }

    public static void updateProjection(Matrix4fc projectionMatrix, Matrix4fc viewRotationMatrix) {
        if (projectionMatrix == null || viewRotationMatrix == null)
            return;

        // Model submissions are captured in camera-relative space, so the skeleton shares the silhouette matrix.
        SKELETON_PROJECTION.set(projectionMatrix).mul(viewRotationMatrix);
        skeletonProjectionReady = true;

        if (!canCapture())
            return;

        captureWidth = mc.getWindow().getWidth();
        captureHeight = mc.getWindow().getHeight();
        if (captureWidth <= 0 || captureHeight <= 0)
            return;

        VIEW_PROJECTION.set(projectionMatrix).mul(viewRotationMatrix);
        reversedDepth = isReversedDepthProjection(projectionMatrix);
        zeroToOneDepth = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        projectionReady = true;
    }

    private static boolean shouldCaptureState(Object state) {
        if (!(state instanceof EntityRenderState entityRenderState))
            return false;

        Entity entity = ((IEntityRenderState) entityRenderState).client$getEntity();
        if (!(entity instanceof Player))
            return false;
        if (entity.isCurrentlyGlowing())
            return false;

        if (mc.player == null || mc.level == null)
            return false;

        if (entity instanceof LocalPlayer)
            return local.get() && (!mc.options.getCameraType().isFirstPerson() || ModuleManager.getModuleState(Freecam.class));

        return true;
    }

    private static boolean canCapture() {
        return captureOpen && !GuiEntityRenderGuard.isActive() && !IrisCompat.isRenderingShadowPass();
    }

    private static void syncIrisProjection() {
        if (irisProjectionSynced)
            return;
        irisProjectionSynced = true;
        if (!IrisCompat.isShaderPackInUse() || !IrisCompat.gbufferMatrices(IRIS_PROJECTION, VIEW_PROJECTION))
            return;

        reversedDepth = isReversedDepthProjection(IRIS_PROJECTION);
    }

    private static boolean updateProjection() {
        if (mc.player == null || mc.level == null)
            return false;

        captureWidth = mc.getWindow().getWidth();
        captureHeight = mc.getWindow().getHeight();
        if (captureWidth <= 0 || captureHeight <= 0)
            return false;

        mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(VIEW_PROJECTION.identity());
        zeroToOneDepth = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        projectionReady = true;
        return true;
    }

    private static void resetCapture() {
        TRIANGLES.clear();
        ARMOR_TRIANGLES.clear();
        IMAGE_ALPHA_MASKS.clear();
        SPRITE_ALPHA_MASKS.clear();
        TEXTURE_ALPHA_MASKS.clear();
        ARMOR_RENDER_TYPES.clear();
        RENDER_TYPE_TEXTURES.clear();
        CELL_PATTERNS.clear();
        ITEM_TEXTURE_LOCATIONS.clear();
        PART_LOCAL_BOUNDS.clear();
        SKELETON_SEGMENTS.clear();
        captureOpen = false;
        projectionReady = false;
        reversedDepth = false;
        zeroToOneDepth = false;
        itemCaptureState = null;
        equipmentCaptureState = null;
        itemCaptureArmor = false;
        irisProjectionSynced = false;
        captureWidth = 0;
        captureHeight = 0;
        captureId++;
        renderedCaptureId = captureId;
    }

    private static AlphaMask alphaMaskFor(Model<?> model, Entity entity) {
        if (!(model instanceof PlayerModel) || !(entity instanceof AbstractClientPlayer player))
            return null;

        return alphaMaskForTexture(player.getSkin().body().texturePath());
    }

    private static ArmorRenderKind armorRenderKind(RenderType renderType) {
        if (renderType == null)
            return ArmorRenderKind.NONE;

        return ARMOR_RENDER_TYPES.computeIfAbsent(renderType, ESP::detectArmorRenderKind);
    }

    private static ArmorRenderKind detectArmorRenderKind(RenderType renderType) {
        String description = renderType.toString().toLowerCase(Locale.ROOT);
        String name = renderTypeName(description);
        if (name.startsWith("armor_trim")
                || name.equals("trimmed_armor_glint")
                || name.equals("armor_entity_glint")) {
            return ArmorRenderKind.OVERLAY;
        }

        if (name.startsWith("armor_cutout_no_cull")
                || name.startsWith("armor_decal_cutout_no_cull")
                || name.startsWith("armor_translucent")
                || description.contains("textures/models/armor")
                || description.contains("textures/entity/equipment/humanoid")) {
            return ArmorRenderKind.ARMOR;
        }

        return ArmorRenderKind.NONE;
    }

    private static String renderTypeName(String description) {
        String prefix = "rendertype[";
        if (!description.startsWith(prefix))
            return description;

        int end = description.indexOf(':', prefix.length());
        return end < 0 ? description.substring(prefix.length()) : description.substring(prefix.length(), end);
    }

    private static AlphaMask alphaMaskFor(Identifier texture) {
        return alphaMaskForTexture(texture);
    }

    private static AlphaMask alphaMaskFor(BakedQuad quad) {
        if (quad == null)
            return null;

        return alphaMaskFor(quad.materialInfo().sprite());
    }

    private static AlphaMask alphaMaskFor(TextureAtlasSprite sprite) {
        if (sprite == null || !sprite.contents().transparency().hasTransparent())
            return null;

        return SPRITE_ALPHA_MASKS.computeIfAbsent(sprite, SpriteAlphaMask::new);
    }

    private static AlphaMask alphaMaskFor(RenderType renderType) {
        return alphaMaskForTexture(textureIdentifierFor(renderType));
    }

    private static AlphaMask alphaMaskForTexture(Identifier texture) {
        if (texture == null)
            return null;

        AbstractTexture boundTexture = mc.getTextureManager().getTexture(texture);
        if (boundTexture instanceof DynamicTexture dynamicTexture) {
            NativeImage image = dynamicTexture.getPixels();
            if (image == null || image.isClosed() || image.getWidth() <= 0 || image.getHeight() <= 0)
                return null;

            return IMAGE_ALPHA_MASKS.computeIfAbsent(image, NativeImageAlphaMask::new);
        }

        Object gpuTexture;
        try {
            gpuTexture = boundTexture.getTexture();
        } catch (IllegalStateException exception) {
            return null;
        }

        TextureAlphaMask cached = TEXTURE_ALPHA_MASKS.get(texture);
        if (cached != null && cached.gpuTexture() == gpuTexture)
            return cached.mask();

        AlphaMask loaded = loadTextureAlphaMask(texture);
        TEXTURE_ALPHA_MASKS.put(texture, new TextureAlphaMask(gpuTexture, loaded));
        return loaded;
    }

    private record TextureAlphaMask(Object gpuTexture, AlphaMask mask) {
    }

    private static Identifier textureIdentifierFor(RenderType renderType) {
        if (renderType == null)
            return null;

        if (RENDER_TYPE_TEXTURES.containsKey(renderType))
            return RENDER_TYPE_TEXTURES.get(renderType);

        String name = renderType.toString().toLowerCase(Locale.ROOT);
        int start = 0;
        while (start < name.length()) {
            int extension = name.indexOf(".png", start);
            if (extension < 0)
                break;

            int candidateStart = findTextureIdentifierStart(name, extension);
            if (candidateStart >= 0) {
                Identifier texture = Identifier.tryParse(name.substring(candidateStart, extension + 4));
                if (texture != null && !texture.getPath().contains("glint")) {
                    RENDER_TYPE_TEXTURES.put(renderType, texture);
                    return texture;
                }
            }

            start = extension + 4;
        }

        RENDER_TYPE_TEXTURES.put(renderType, null);
        return null;
    }

    private static int findTextureIdentifierStart(String text, int extensionStart) {
        int namespaceSeparator = -1;
        int index = extensionStart - 1;

        while (index >= 0 && isTexturePathCharacter(text.charAt(index))) {
            if (text.charAt(index) == ':')
                namespaceSeparator = index;
            index--;
        }

        if (namespaceSeparator <= index + 1 || namespaceSeparator >= extensionStart)
            return -1;

        for (int i = index + 1; i < namespaceSeparator; i++) {
            if (!isTextureNamespaceCharacter(text.charAt(i)))
                return -1;
        }

        return index + 1;
    }

    private static boolean isTextureNamespaceCharacter(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-';
    }

    private static boolean isTexturePathCharacter(char c) {
        return isTextureNamespaceCharacter(c) || c == '/' || c == ':';
    }

    private static AlphaMask loadTextureAlphaMask(Identifier texture) {
        Optional<Resource> resource = mc.getResourceManager().getResource(texture);
        if (resource.isEmpty())
            return null;

        try (InputStream inputStream = resource.get().open(); NativeImage image = NativeImage.read(inputStream)) {
            if (image.isClosed() || image.getWidth() <= 0 || image.getHeight() <= 0 || !image.computeTransparency().hasTransparent())
                return null;

            return new NativeImageAlphaMask(image);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static Entity getCapturedEntity(Object state) {
        if (!(state instanceof EntityRenderState entityRenderState))
            return null;

        return ((IEntityRenderState) entityRenderState).client$getEntity();
    }

    private static MainTargetHandle mainTargetHandle(RenderTarget target) {
        if (target == null || target.width <= 0 || target.height <= 0)
            return null;

        GpuDeviceBackend backend = RenderSystem.getDevice() instanceof FrontendGpuDevice frontendDevice
                ? frontendDevice.backend
                : null;
        if (!(backend instanceof GlDevice glDevice)
                || !(target.getColorTextureView() instanceof GlTextureView colorView)
                || !(target.getDepthTextureView() instanceof GlTextureView depthView))
            return null;

        int framebuffer = glDevice.frameBufferCache().getFbo(glDevice.directStateAccess(), List.of(colorView), depthView);
        int depthTexture = depthView.glId();
        return framebuffer > 0 && depthTexture > 0
                ? new MainTargetHandle(framebuffer, depthTexture)
                : null;
    }

    private record MainTargetHandle(int framebuffer, int depthTexture) {
    }

    private static boolean isReversedDepthProjection(Matrix4fc projectionMatrix) {
        DepthDirectionSample negativeZ = depthDirectionSample(projectionMatrix, -1f, -512f);
        DepthDirectionSample positiveZ = depthDirectionSample(projectionMatrix, 1f, 512f);
        DepthDirectionSample selected = positiveZ.score() > negativeZ.score() ? positiveZ : negativeZ;
        return selected.valid() && selected.nearDepth > selected.farDepth;
    }

    private static DepthDirectionSample depthDirectionSample(Matrix4fc projectionMatrix, float nearZ, float farZ) {
        return new DepthDirectionSample(projectedDepth(projectionMatrix, nearZ), projectedDepth(projectionMatrix, farZ));
    }

    private static float projectedDepth(Matrix4fc projectionMatrix, float viewZ) {
        Vector4f projected = new Vector4f(0f, 0f, viewZ, 1f).mul(projectionMatrix);
        if (Math.abs(projected.w) < 1.0e-6f)
            return Float.NaN;

        return projected.z / projected.w;
    }

    private static void addScreenTriangle(FloatArrayBuilder target, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 0.01f)
            return;

        target.add(ax, ay, az);
        target.add(bx, by, bz);
        target.add(cx, cy, cz);
    }

    private static final class DepthDirectionSample {
        private final float nearDepth;
        private final float farDepth;

        private DepthDirectionSample(float nearDepth, float farDepth) {
            this.nearDepth = nearDepth;
            this.farDepth = farDepth;
        }

        private boolean valid() {
            return Float.isFinite(nearDepth) && Float.isFinite(farDepth);
        }

        private float score() {
            if (!valid())
                return -1f;

            float score = 0f;
            if (nearDepth >= -1.1f && nearDepth <= 1.1f)
                score += 2f;
            if (farDepth >= -1.1f && farDepth <= 1.1f)
                score += 2f;

            return score + Math.min(Math.abs(nearDepth - farDepth), 1f);
        }
    }

    private static final class SilhouetteVertexConsumer implements VertexConsumer {
        private static final int CLIP_PLANE_COUNT = 7;
        private static final int MAX_CLIPPED_VERTICES = 12;
        private static final float MIN_CLIP_W = 1.0e-6f;

        private Matrix4fc viewProjection;
        private int width;
        private int height;
        private boolean zeroToOneDepth;
        private AlphaMask fallbackAlphaMask;
        private FloatArrayBuilder target;
        private final Vector4f vector = new Vector4f();
        private final Vector3f itemVector = new Vector3f();
        private final float[] quadClip = new float[4 * 4];
        private final float[] subQuadClip = new float[4 * 4];
        private final float[] clipBufferA = new float[MAX_CLIPPED_VERTICES * 4];
        private final float[] clipBufferB = new float[MAX_CLIPPED_VERTICES * 4];
        private final float[] clippedScreenX = new float[MAX_CLIPPED_VERTICES];
        private final float[] clippedScreenY = new float[MAX_CLIPPED_VERTICES];
        private final float[] clippedScreenZ = new float[MAX_CLIPPED_VERTICES];
        private final float[] quadU = new float[4];
        private final float[] quadV = new float[4];
        private final boolean[] valid = new boolean[4];
        private AlphaMask activeAlphaMask;
        private int quadIndex = 0;
        private boolean quadInside;
        private final boolean[] opaqueCells = new boolean[16 * 16];
        private final int[] openStart = new int[16];
        private final int[] openEnd = new int[16];
        private final int[] openRow = new int[16];
        private final int[] nextOpenStart = new int[16];
        private final int[] nextOpenEnd = new int[16];
        private final int[] nextOpenRow = new int[16];
        private int[] rectScratch = new int[64 * 4];

        private SilhouetteVertexConsumer() {
        }

        private SilhouetteVertexConsumer reset(Matrix4fc viewProjection, int width, int height,
                                               AlphaMask alphaMask, FloatArrayBuilder target) {
            this.viewProjection = viewProjection;
            this.width = width;
            this.height = height;
            this.zeroToOneDepth = ESP.zeroToOneDepth;
            this.fallbackAlphaMask = alphaMask;
            this.activeAlphaMask = alphaMask;
            this.target = target;
            this.quadIndex = 0;
            return this;
        }

        @Override
        public @NonNull VertexConsumer addVertex(float x, float y, float z) {
            addVertex(x, y, z, 0f, 0f);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            addVertex(x, y, z, u, v);
        }

        private void addVertex(float x, float y, float z, float u, float v) {
            int index = quadIndex++;
            quadU[index] = u;
            quadV[index] = v;
            projectVertex(index, x, y, z);

            if (quadIndex == 4) {
                emitQuad();
                quadIndex = 0;
            }
        }

        private void addBakedQuad(Matrix4fc poseMatrix, BakedQuad quad, AlphaMask alphaMask) {
            activeAlphaMask = alphaMask;
            for (int i = 0; i < 4; i++) {
                Vector3fc position = quad.position(i);
                long packedUv = quad.packedUV(i);
                poseMatrix.transformPosition(position.x(), position.y(), position.z(), itemVector);
                addVertex(itemVector.x, itemVector.y, itemVector.z, UVPair.unpackU(packedUv), UVPair.unpackV(packedUv));
            }
            activeAlphaMask = fallbackAlphaMask;
        }

        private void addMeshTriangle(Matrix4fc poseMatrix, float[] positions, float[] uvs, int a, int b, int c) {
            addMeshVertex(poseMatrix, positions, uvs, a);
            addMeshVertex(poseMatrix, positions, uvs, b);
            addMeshVertex(poseMatrix, positions, uvs, c);
            addMeshVertex(poseMatrix, positions, uvs, c);
        }

        private void addMeshVertex(Matrix4fc poseMatrix, float[] positions, float[] uvs, int index) {
            poseMatrix.transformPosition(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2], itemVector);
            addVertex(itemVector.x, itemVector.y, itemVector.z, uvs[index * 2], uvs[index * 2 + 1]);
        }

        private void projectVertex(int index, float x, float y, float z) {
            Vector4f projected = vector.set(x, y, z, 1f).mul(viewProjection);
            int offset = index * 4;
            quadClip[offset] = projected.x;
            quadClip[offset + 1] = projected.y;
            quadClip[offset + 2] = projected.z;
            quadClip[offset + 3] = projected.w;
            valid[index] = Float.isFinite(projected.x)
                    && Float.isFinite(projected.y)
                    && Float.isFinite(projected.z)
                    && Float.isFinite(projected.w);
        }

        private void emitQuad() {
            if (!valid[0] || !valid[1] || !valid[2] || !valid[3])
                return;
            if (outsideFrustum(quadClip))
                return;
            quadInside = insideFrustum(quadClip);

            AlphaMask alphaMask = activeAlphaMask;
            if (alphaMask != null) {
                emitAlphaAwareQuad();
                return;
            }

            addTriangle(0, 1, 2);
            addTriangle(0, 2, 3);
        }

        private void emitAlphaAwareQuad() {
            AlphaMask alphaMask = activeAlphaMask;
            if (alphaMask == null)
                return;

            float minU = Math.min(Math.min(quadU[0], quadU[1]), Math.min(quadU[2], quadU[3]));
            float maxU = Math.max(Math.max(quadU[0], quadU[1]), Math.max(quadU[2], quadU[3]));
            float minV = Math.min(Math.min(quadV[0], quadV[1]), Math.min(quadV[2], quadV[3]));
            float maxV = Math.max(Math.max(quadV[0], quadV[1]), Math.max(quadV[2], quadV[3]));
            if (alphaMask.isRegionTransparent(minU, minV, maxU, maxV))
                return;
            if (alphaMask.isRegionOpaque(minU, minV, maxU, maxV)) {
                addTriangle(0, 1, 2);
                addTriangle(0, 2, 3);
                return;
            }

            int[] pattern = cellPattern(alphaMask);
            int stepsU = pattern[0];
            int stepsV = pattern[1];
            boolean parallelogram = isParallelogram();
            for (int offset = 2; offset + 3 < pattern.length; offset += 4) {
                int x0 = pattern[offset];
                int y0 = pattern[offset + 1];
                int x1 = pattern[offset + 2];
                int y1 = pattern[offset + 3];
                if (parallelogram) {
                    emitSubQuad(x0 / (float) stepsU, y0 / (float) stepsV, x1 / (float) stepsU, y1 / (float) stepsV);
                    continue;
                }

                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++)
                        emitSubQuad(x / (float) stepsU, y / (float) stepsV, (x + 1) / (float) stepsU, (y + 1) / (float) stepsV);
                }
            }
        }

        private int[] cellPattern(AlphaMask alphaMask) {
            CellPatternKey key = new CellPatternKey(alphaMask,
                    quadU[0], quadU[1], quadU[2], quadU[3],
                    quadV[0], quadV[1], quadV[2], quadV[3]);
            int[] cached = CELL_PATTERNS.get(key);
            if (cached != null)
                return cached;

            float minU = Math.min(Math.min(quadU[0], quadU[1]), Math.min(quadU[2], quadU[3]));
            float maxU = Math.max(Math.max(quadU[0], quadU[1]), Math.max(quadU[2], quadU[3]));
            float minV = Math.min(Math.min(quadV[0], quadV[1]), Math.min(quadV[2], quadV[3]));
            float maxV = Math.max(Math.max(quadV[0], quadV[1]), Math.max(quadV[2], quadV[3]));
            int stepsU = clampSteps((int) Math.ceil(Math.abs(alphaMask.localU(maxU) - alphaMask.localU(minU)) * alphaMask.width()));
            int stepsV = clampSteps((int) Math.ceil(Math.abs(alphaMask.localV(maxV) - alphaMask.localV(minV)) * alphaMask.height()));

            for (int y = 0; y < stepsV; y++) {
                float t0 = y / (float) stepsV;
                float t1 = (y + 1) / (float) stepsV;
                for (int x = 0; x < stepsU; x++) {
                    float s0 = x / (float) stepsU;
                    float s1 = (x + 1) / (float) stepsU;
                    float sampleU = interpolateQuad(quadU, (s0 + s1) * 0.5f, (t0 + t1) * 0.5f);
                    float sampleV = interpolateQuad(quadV, (s0 + s1) * 0.5f, (t0 + t1) * 0.5f);
                    opaqueCells[y * 16 + x] = alphaMask.isOpaque(sampleU, sampleV);
                }
            }

            int rectCount = mergeOpaqueCells(stepsU, stepsV);
            int[] pattern = new int[2 + rectCount * 4];
            pattern[0] = stepsU;
            pattern[1] = stepsV;
            System.arraycopy(rectScratch, 0, pattern, 2, rectCount * 4);
            if (CELL_PATTERNS.size() >= CELL_PATTERN_CACHE_LIMIT)
                CELL_PATTERNS.clear();
            CELL_PATTERNS.put(key, pattern);
            return pattern;
        }

        private int mergeOpaqueCells(int stepsU, int stepsV) {
            int rectCount = 0;
            int openCount = 0;
            for (int y = 0; y <= stepsV; y++) {
                int nextCount = 0;
                int x = 0;
                while (y < stepsV && x < stepsU) {
                    if (!opaqueCells[y * 16 + x]) {
                        x++;
                        continue;
                    }

                    int start = x;
                    while (x < stepsU && opaqueCells[y * 16 + x])
                        x++;

                    int row = y;
                    for (int i = 0; i < openCount; i++) {
                        if (openStart[i] == start && openEnd[i] == x) {
                            row = openRow[i];
                            openRow[i] = -1;
                            break;
                        }
                    }
                    nextOpenStart[nextCount] = start;
                    nextOpenEnd[nextCount] = x;
                    nextOpenRow[nextCount] = row;
                    nextCount++;
                }

                for (int i = 0; i < openCount; i++) {
                    if (openRow[i] < 0)
                        continue;
                    rectCount = addRect(rectCount, openStart[i], openRow[i], openEnd[i], y);
                }

                System.arraycopy(nextOpenStart, 0, openStart, 0, nextCount);
                System.arraycopy(nextOpenEnd, 0, openEnd, 0, nextCount);
                System.arraycopy(nextOpenRow, 0, openRow, 0, nextCount);
                openCount = nextCount;
            }
            return rectCount;
        }

        private int addRect(int rectCount, int x0, int y0, int x1, int y1) {
            if ((rectCount + 1) * 4 > rectScratch.length)
                rectScratch = Arrays.copyOf(rectScratch, rectScratch.length * 2);

            int offset = rectCount * 4;
            rectScratch[offset] = x0;
            rectScratch[offset + 1] = y0;
            rectScratch[offset + 2] = x1;
            rectScratch[offset + 3] = y1;
            return rectCount + 1;
        }

        private boolean isParallelogram() {
            for (int component = 0; component < 4; component++) {
                float a = quadClip[component];
                float b = quadClip[4 + component];
                float c = quadClip[8 + component];
                float d = quadClip[12 + component];
                float tolerance = 1.0e-4f * (Math.abs(a) + Math.abs(b) + Math.abs(c) + Math.abs(d)) + 1.0e-6f;
                if (Math.abs(a + c - b - d) > tolerance)
                    return false;
            }
            return true;
        }

        private int clampSteps(int steps) {
            return Math.clamp(steps, 1, 16);
        }

        private void emitSubQuad(float s0, float t0, float s1, float t1) {
            interpolateClipVertex(subQuadClip, 0, s0, t0);
            interpolateClipVertex(subQuadClip, 1, s1, t0);
            interpolateClipVertex(subQuadClip, 2, s1, t1);
            interpolateClipVertex(subQuadClip, 3, s0, t1);

            addTriangle(subQuadClip, 0, 1, 2);
            addTriangle(subQuadClip, 0, 2, 3);
        }

        private float interpolateQuad(float[] values, float s, float t) {
            float top = values[0] + (values[1] - values[0]) * s;
            float bottom = values[3] + (values[2] - values[3]) * s;
            return top + (bottom - top) * t;
        }

        private void addTriangle(int a, int b, int c) {
            addTriangle(quadClip, a, b, c);
        }

        private void interpolateClipVertex(float[] destination, int destinationIndex, float s, float t) {
            int destinationOffset = destinationIndex * 4;
            for (int component = 0; component < 4; component++)
                destination[destinationOffset + component] = interpolateQuadComponent(component, s, t);
        }

        private float interpolateQuadComponent(int component, float s, float t) {
            float top = quadClip[component] + (quadClip[4 + component] - quadClip[component]) * s;
            float bottom = quadClip[12 + component] + (quadClip[8 + component] - quadClip[12 + component]) * s;
            return top + (bottom - top) * t;
        }

        private void addTriangle(float[] source, int a, int b, int c) {
            if (quadInside) {
                addProjectedTriangle(source, a, b, c);
                return;
            }

            copyVertex(source, a, clipBufferA, 0);
            copyVertex(source, b, clipBufferA, 1);
            copyVertex(source, c, clipBufferA, 2);

            // Clip in homogeneous space before dividing by W. Close entities can
            // cross the camera plane, where projecting each vertex first would
            // otherwise create unbounded coordinates or discard the whole face.
            float[] input = clipBufferA;
            float[] output = clipBufferB;
            int vertexCount = 3;
            for (int plane = 0; plane < CLIP_PLANE_COUNT; plane++) {
                vertexCount = clipPolygonAgainstPlane(input, vertexCount, output, plane);
                if (vertexCount < 3)
                    return;

                float[] swap = input;
                input = output;
                output = swap;
            }

            for (int i = 0; i < vertexCount; i++) {
                int offset = i * 4;
                float w = input[offset + 3];
                if (!Float.isFinite(w) || w <= 0f)
                    return;

                float ndcX = input[offset] / w;
                float ndcY = input[offset + 1] / w;
                float ndcZ = input[offset + 2] / w;
                if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY) || !Float.isFinite(ndcZ))
                    return;

                ndcX = Math.clamp(ndcX, -1f, 1f);
                ndcY = Math.clamp(ndcY, -1f, 1f);
                ndcZ = zeroToOneDepth
                        ? Math.clamp(ndcZ, 0f, 1f)
                        : Math.clamp(ndcZ, -1f, 1f);
                clippedScreenX[i] = (ndcX * 0.5f + 0.5f) * width;
                clippedScreenY[i] = (1f - (ndcY * 0.5f + 0.5f)) * height;
                clippedScreenZ[i] = ndcZ;
            }

            for (int i = 1; i + 1 < vertexCount; i++) {
                addScreenTriangle(
                        target,
                        clippedScreenX[0], clippedScreenY[0], clippedScreenZ[0],
                        clippedScreenX[i], clippedScreenY[i], clippedScreenZ[i],
                        clippedScreenX[i + 1], clippedScreenY[i + 1], clippedScreenZ[i + 1]
                );
            }
        }

        private void addProjectedTriangle(float[] source, int a, int b, int c) {
            if (!projectToScreen(source, a, 0) || !projectToScreen(source, b, 1) || !projectToScreen(source, c, 2))
                return;

            addScreenTriangle(
                    target,
                    clippedScreenX[0], clippedScreenY[0], clippedScreenZ[0],
                    clippedScreenX[1], clippedScreenY[1], clippedScreenZ[1],
                    clippedScreenX[2], clippedScreenY[2], clippedScreenZ[2]
            );
        }

        private boolean projectToScreen(float[] source, int index, int slot) {
            int offset = index * 4;
            float w = source[offset + 3];
            if (!Float.isFinite(w) || w <= 0f)
                return false;

            float ndcX = source[offset] / w;
            float ndcY = source[offset + 1] / w;
            float ndcZ = source[offset + 2] / w;
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY) || !Float.isFinite(ndcZ))
                return false;

            ndcX = Math.clamp(ndcX, -1f, 1f);
            ndcY = Math.clamp(ndcY, -1f, 1f);
            ndcZ = zeroToOneDepth
                    ? Math.clamp(ndcZ, 0f, 1f)
                    : Math.clamp(ndcZ, -1f, 1f);
            clippedScreenX[slot] = (ndcX * 0.5f + 0.5f) * width;
            clippedScreenY[slot] = (1f - (ndcY * 0.5f + 0.5f)) * height;
            clippedScreenZ[slot] = ndcZ;
            return true;
        }

        private boolean insideFrustum(float[] vertices) {
            for (int plane = 0; plane < CLIP_PLANE_COUNT; plane++) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    if (clipDistance(vertices, vertex, plane) < 0f)
                        return false;
                }
            }
            return true;
        }

        private int clipPolygonAgainstPlane(float[] input, int inputCount, float[] output, int plane) {
            int outputCount = 0;
            int previousIndex = inputCount - 1;
            float previousDistance = clipDistance(input, previousIndex, plane);
            boolean previousInside = previousDistance >= 0f;

            for (int currentIndex = 0; currentIndex < inputCount; currentIndex++) {
                float currentDistance = clipDistance(input, currentIndex, plane);
                boolean currentInside = currentDistance >= 0f;

                if (currentInside != previousInside) {
                    float denominator = previousDistance - currentDistance;
                    if (denominator != 0f && outputCount < MAX_CLIPPED_VERTICES) {
                        float delta = Math.clamp(previousDistance / denominator, 0f, 1f);
                        interpolateVertex(input, previousIndex, currentIndex, delta, output, outputCount++);
                    }
                }

                if (currentInside && outputCount < MAX_CLIPPED_VERTICES)
                    copyVertex(input, currentIndex, output, outputCount++);

                previousIndex = currentIndex;
                previousDistance = currentDistance;
                previousInside = currentInside;
            }

            return outputCount;
        }

        private float clipDistance(float[] vertices, int index, int plane) {
            int offset = index * 4;
            float x = vertices[offset];
            float y = vertices[offset + 1];
            float z = vertices[offset + 2];
            float w = vertices[offset + 3];
            return switch (plane) {
                case 0 -> x + w;
                case 1 -> w - x;
                case 2 -> y + w;
                case 3 -> w - y;
                case 4 -> zeroToOneDepth ? z : z + w;
                case 5 -> w - z;
                default -> w - MIN_CLIP_W;
            };
        }

        private boolean outsideFrustum(float[] vertices) {
            for (int plane = 0; plane < CLIP_PLANE_COUNT; plane++) {
                boolean allOutside = true;
                for (int vertex = 0; vertex < 4; vertex++) {
                    if (clipDistance(vertices, vertex, plane) >= 0f) {
                        allOutside = false;
                        break;
                    }
                }
                if (allOutside)
                    return true;
            }
            return false;
        }

        private void copyVertex(float[] source, int sourceIndex, float[] destination, int destinationIndex) {
            int sourceOffset = sourceIndex * 4;
            int destinationOffset = destinationIndex * 4;
            System.arraycopy(source, sourceOffset, destination, destinationOffset, 4);
        }

        private void interpolateVertex(float[] source, int startIndex, int endIndex, float delta,
                                       float[] destination, int destinationIndex) {
            int startOffset = startIndex * 4;
            int endOffset = endIndex * 4;
            int destinationOffset = destinationIndex * 4;
            for (int component = 0; component < 4; component++) {
                float start = source[startOffset + component];
                destination[destinationOffset + component] = start
                        + (source[endOffset + component] - start) * delta;
            }
        }

        @Override
        public @NonNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    private interface AlphaMask {
        boolean isOpaque(float u, float v);

        default float localU(float u) {
            return u;
        }

        default float localV(float v) {
            return v;
        }

        int width();

        int height();

        boolean isRegionOpaque(float minU, float minV, float maxU, float maxV);

        boolean isRegionTransparent(float minU, float minV, float maxU, float maxV);
    }

    private static final class NativeImageAlphaMask implements AlphaMask {
        private final int width;
        private final int height;
        private final byte[] opaque;
        private final int[] opaquePrefix;

        private NativeImageAlphaMask(NativeImage image) {
            width = image.getWidth();
            height = image.getHeight();
            opaque = new byte[width * height];
            opaquePrefix = new int[(width + 1) * (height + 1)];
            for (int y = 0; y < height; y++) {
                int rowOpaque = 0;
                for (int x = 0; x < width; x++) {
                    int opaqueValue = (((image.getPixel(x, y) >>> 24) & 0xFF) > 16) ? 1 : 0;
                    opaque[y * width + x] = (byte) opaqueValue;
                    rowOpaque += opaqueValue;
                    opaquePrefix[(y + 1) * (width + 1) + x + 1] = opaquePrefix[y * (width + 1) + x + 1] + rowOpaque;
                }
            }
        }

        @Override
        public boolean isOpaque(float u, float v) {
            int x = Math.clamp((int) Math.floor(u * width), 0, width - 1);
            int y = Math.clamp((int) Math.floor(v * height), 0, height - 1);
            return opaque[y * width + x] != 0;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public boolean isRegionOpaque(float minU, float minV, float maxU, float maxV) {
            RegionCoverage coverage = regionCoverage(this, minU, minV, maxU, maxV);
            int area = coverage.area();
            return area > 0 && opaqueCount(coverage) == area;
        }

        @Override
        public boolean isRegionTransparent(float minU, float minV, float maxU, float maxV) {
            RegionCoverage coverage = regionCoverage(this, minU, minV, maxU, maxV);
            return coverage.area() > 0 && opaqueCount(coverage) == 0;
        }

        private int opaqueCount(RegionCoverage coverage) {
            return regionOpaqueCount(opaquePrefix, width, coverage);
        }
    }

    private static final class SpriteAlphaMask implements AlphaMask {
        private final float u0;
        private final float v0;
        private final float uScale;
        private final float vScale;
        private final int width;
        private final int height;
        private final byte[] opaque;
        private final int[] opaquePrefix;

        private SpriteAlphaMask(TextureAtlasSprite sprite) {
            SpriteContents contents = sprite.contents();
            u0 = sprite.getU0();
            v0 = sprite.getV0();
            uScale = 1f / Math.max(1.0e-6f, sprite.getU1() - sprite.getU0());
            vScale = 1f / Math.max(1.0e-6f, sprite.getV1() - sprite.getV0());
            width = Math.max(1, contents.width());
            height = Math.max(1, contents.height());
            opaque = new byte[width * height];
            opaquePrefix = new int[(width + 1) * (height + 1)];
            for (int y = 0; y < height; y++) {
                int rowOpaque = 0;
                for (int x = 0; x < width; x++) {
                    int opaqueValue = contents.isTransparent(0, x, y) ? 0 : 1;
                    opaque[y * width + x] = (byte) opaqueValue;
                    rowOpaque += opaqueValue;
                    opaquePrefix[(y + 1) * (width + 1) + x + 1] = opaquePrefix[y * (width + 1) + x + 1] + rowOpaque;
                }
            }
        }

        @Override
        public boolean isOpaque(float u, float v) {
            int x = Math.clamp((int) Math.floor(localU(u) * width), 0, width - 1);
            int y = Math.clamp((int) Math.floor(localV(v) * height), 0, height - 1);
            return opaque[y * width + x] != 0;
        }

        @Override
        public float localU(float u) {
            return (u - u0) * uScale;
        }

        @Override
        public float localV(float v) {
            return (v - v0) * vScale;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public boolean isRegionOpaque(float minU, float minV, float maxU, float maxV) {
            RegionCoverage coverage = regionCoverage(this, minU, minV, maxU, maxV);
            int area = coverage.area();
            return area > 0 && opaqueCount(coverage) == area;
        }

        @Override
        public boolean isRegionTransparent(float minU, float minV, float maxU, float maxV) {
            RegionCoverage coverage = regionCoverage(this, minU, minV, maxU, maxV);
            return coverage.area() > 0 && opaqueCount(coverage) == 0;
        }

        private int opaqueCount(RegionCoverage coverage) {
            return regionOpaqueCount(opaquePrefix, width, coverage);
        }
    }

    private record CellPatternKey(AlphaMask mask, float u0, float u1, float u2, float u3,
                                  float v0, float v1, float v2, float v3) {
        @Override
        public boolean equals(Object other) {
            return other instanceof CellPatternKey key
                    && mask == key.mask
                    && Float.compare(u0, key.u0) == 0 && Float.compare(u1, key.u1) == 0
                    && Float.compare(u2, key.u2) == 0 && Float.compare(u3, key.u3) == 0
                    && Float.compare(v0, key.v0) == 0 && Float.compare(v1, key.v1) == 0
                    && Float.compare(v2, key.v2) == 0 && Float.compare(v3, key.v3) == 0;
        }

        @Override
        public int hashCode() {
            int hash = System.identityHashCode(mask);
            hash = 31 * hash + Float.floatToIntBits(u0);
            hash = 31 * hash + Float.floatToIntBits(u1);
            hash = 31 * hash + Float.floatToIntBits(u2);
            hash = 31 * hash + Float.floatToIntBits(u3);
            hash = 31 * hash + Float.floatToIntBits(v0);
            hash = 31 * hash + Float.floatToIntBits(v1);
            hash = 31 * hash + Float.floatToIntBits(v2);
            return 31 * hash + Float.floatToIntBits(v3);
        }
    }

    private enum ArmorRenderKind {
        NONE,
        ARMOR,
        OVERLAY
    }

    private record RegionCoverage(int x0, int y0, int x1, int y1) {
        private int area() {
            return Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
        }
    }

    private static RegionCoverage regionCoverage(AlphaMask alphaMask, float minU, float minV, float maxU, float maxV) {
        float localMinU = Math.min(alphaMask.localU(minU), alphaMask.localU(maxU));
        float localMaxU = Math.max(alphaMask.localU(minU), alphaMask.localU(maxU));
        float localMinV = Math.min(alphaMask.localV(minV), alphaMask.localV(maxV));
        float localMaxV = Math.max(alphaMask.localV(minV), alphaMask.localV(maxV));
        int x0 = regionStart(localMinU, alphaMask.width());
        int x1 = regionEnd(localMaxU, alphaMask.width(), x0);
        int y0 = regionStart(localMinV, alphaMask.height());
        int y1 = regionEnd(localMaxV, alphaMask.height(), y0);
        return new RegionCoverage(x0, y0, x1, y1);
    }

    private static int regionStart(float local, int size) {
        return Math.clamp((int) Math.floor(local * size), 0, Math.max(0, size - 1));
    }

    private static int regionEnd(float local, int size, int start) {
        return Math.clamp(Math.max(start + 1, (int) Math.ceil(local * size)), start + 1, size);
    }

    private static int regionOpaqueCount(int[] prefix, int width, RegionCoverage coverage) {
        int stride = width + 1;
        return prefix[coverage.y1 * stride + coverage.x1]
                - prefix[coverage.y0 * stride + coverage.x1]
                - prefix[coverage.y1 * stride + coverage.x0]
                + prefix[coverage.y0 * stride + coverage.x0];
    }

    private static final class FloatArrayBuilder {
        private float[] values;
        private int size;
        private FloatBuffer uploadBuffer;

        private FloatArrayBuilder(int initialCapacity) {
            values = new float[Math.max(2, initialCapacity)];
        }

        private void add(float x, float y, float z) {
            ensureCapacity(size + 3);
            values[size++] = x;
            values[size++] = y;
            values[size++] = z;
        }

        private int vertexCount() {
            return size / 3;
        }

        private FloatBuffer toBuffer() {
            if (uploadBuffer == null || uploadBuffer.capacity() < size)
                uploadBuffer = BufferUtils.createFloatBuffer(nextCapacity(size));

            uploadBuffer.clear();
            uploadBuffer.put(values, 0, size);
            uploadBuffer.flip();
            return uploadBuffer;
        }

        private void clear() {
            size = 0;
        }

        private void ensureCapacity(int required) {
            if (required <= values.length)
                return;

            values = Arrays.copyOf(values, nextCapacity(required));
        }

        private int nextCapacity(int required) {
            int nextCapacity = values.length;
            while (nextCapacity < required) {
                nextCapacity *= 2;
            }
            return nextCapacity;
        }
    }
}
