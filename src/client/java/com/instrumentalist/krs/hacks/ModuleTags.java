package com.instrumentalist.krs.hacks;

import com.instrumentalist.krs.hacks.features.combat.*;
import com.instrumentalist.krs.hacks.features.dev.*;
import com.instrumentalist.krs.hacks.features.exploit.*;
import com.instrumentalist.krs.hacks.features.exploit.disabler.*;
import com.instrumentalist.krs.hacks.features.level.*;
import com.instrumentalist.krs.hacks.features.movement.*;
import com.instrumentalist.krs.hacks.features.nulling.*;
import com.instrumentalist.krs.hacks.features.player.*;
import com.instrumentalist.krs.hacks.features.render.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Static classification of every module: which PvP era it is fair to use in, and whether it is
 * obviously detectable (blatant) or client-side/legit (ghost). Drives the click GUI filter tabs.
 */
public final class ModuleTags {
    public enum Pvp { CLASSIC, MODERN }

    public enum Visibility { BLATANT, GHOST }

    public record Tags(Set<Pvp> pvp, Set<Visibility> visibility) {
        public boolean has(Pvp tag) {
            return pvp.contains(tag);
        }

        public boolean has(Visibility tag) {
            return visibility.contains(tag);
        }
    }

    private static final Set<Pvp> CLASSIC = Set.of(Pvp.CLASSIC);
    private static final Set<Pvp> MODERN = Set.of(Pvp.MODERN);
    private static final Set<Pvp> BOTH_PVP = Set.of(Pvp.CLASSIC, Pvp.MODERN);
    private static final Set<Visibility> BLATANT = Set.of(Visibility.BLATANT);
    private static final Set<Visibility> GHOST = Set.of(Visibility.GHOST);
    private static final Set<Visibility> BOTH_VIS = Set.of(Visibility.GHOST, Visibility.BLATANT);

    private static final Map<Class<?>, Tags> TAGS = new HashMap<>();
    private static final Tags DEFAULT = new Tags(BOTH_PVP, BLATANT);

    static {
        // Client side or invisible to the server, fair to use in either era.
        tag(BOTH_PVP, GHOST,
                Ambience.class, AntiBlind.class, Camera.class, Capes.class, Chat.class, ClickGui.class,
                CustomModel.class, ESP.class, FullBright.class, HUDHider.class, ImGui.class,
                Interface.class, KillEffect.class, LegacyRenderer.class, NoRender.class,
                Pointers.class, ServerRotations.class, TargetHud.class, ThunderDetector.class, Trails.class,
                ViewModel.class, Zoom.class,
                AntiCheatDetector.class, CaveFinder.class, NoBlockEffects.class, NoteBot.class,
                QuickMacro.class, Spammer.class,
                PluginsDetector.class,
                AntiAFK.class, AutoFish.class, AutoLeave.class, AutoRespawn.class, ChatCommands.class,
                Rewound.class,
                DurabilityAlert.class, Freecam.class, HackerDetector.class, MiddleClick.class,
                MurdererDetector.class, Stalker.class,
                BrandSpoofer.class, ExploitPatcher.class, PackSpoofer.class,
                MovementFix.class, NoPush.class, Parkour.class, SafeWalk.class, Sprint.class,
                AntiBot.class, Teams.class, WTap.class, TargetStrafe.class);

        // Deliberate cheating that works the same in either era.
        tag(BOTH_PVP, BLATANT,
                AutoPot.class, AutoTotem.class, CrossbowExploit.class, FastBow.class,
                FastEat.class, HitBoxes.class, KillAura.class, NoCombatDelay.class, Reach.class,
                Velocity.class,
                Blink.class, ChestTP.class, ConsoleSpammer.class, EntityDesync.class, LongThrow.class,
                NoBreakReset.class, PacketDuper.class, PathFinder.class, PingDisabler.class,
                PortalGodMode.class, PortalScreen.class, ServerCrasher.class, SurroundTP.class,
                TransactionConfirmBlinker.class, XCarry.class, DisablerModule.class,
                AutoBypass.class, Breaker.class, CivBreak.class, ItemDropChanger.class, Nuker.class,
                PacketMine.class, Timer.class, VisualSpoofer.class, Xray.class,
                AutoSneak.class, AutoWalk.class, FastFall.class, InventoryMove.class, Jesus.class, LegitScaffold.class,
                SilentAura.class,
                LongJump.class, NoSlow.class, PerfectHorseJump.class, Spider.class, Step.class,
                AutoTool.class, ChestAura.class, ChestStealer.class, FastBreak.class, FastLadder.class,
                InvManager.class, NoFall.class, Phase.class, Scaffold.class, SneakSpam.class,
                SpinBot.class, LookTP.class, Displace.class);

        // Packet based reach extensions: usable as a ghost feature, but obviously blatant to a spectator.
        tag(BOTH_PVP, BOTH_VIS, Backtrack.class, FakeLag.class);

        // Pre-1.9 mechanics.
        tag(CLASSIC, BLATANT, Criticals.class);
        tag(CLASSIC, GHOST, OldHitting.class);

        // 1.9+ mechanics and the dev tooling.
        tag(MODERN, BLATANT,
                MaceExploit.class, AlwaysRiptide.class, AntiLevitation.class, AntiVoid.class,
                NoJumpCooldown.class,
                AimAssist.class, AntiFireball.class, AutoGapple.class, AutoMine.class, AutoShield.class,
                AutoWeapon.class, AutoXP.class, BlueMapPlayerTracker.class,
                BowAimbot.class, BreakESP.class, ChatExceptionChecker.class, CityBreaker.class,
                CrystalAura.class, GhostHand.class, Harvester.class, LengBreaker.class, MMCFly.class,
                MovementDebugger.class, NoMoreAutism.class, PianoProGamer.class, PopCounter.class,
                SigmaBitcoinMiner.class, TargetUUIDDebugger.class, TriggerBot.class, TrySex.class,
                TunnelMiner.class, VeinMiner.class);
    }

    private ModuleTags() {
    }

    private static void tag(Set<Pvp> pvp, Set<Visibility> visibility, Class<?>... types) {
        Tags tags = new Tags(pvp, visibility);
        for (Class<?> type : types)
            TAGS.put(type, tags);
    }

    public static Tags of(Module module) {
        if (module == null)
            return DEFAULT;
        return TAGS.getOrDefault(module.getClass(), DEFAULT);
    }
}
