package sigf.xo.mod;

import dev.rehan.passthrough.MobWar;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import java.util.Random;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import sigf.xo.Xo;

/**
 * The scenes of Minecraft Crossover. Each scene has a prep (cleanup, GTA teleport, time, weather) and a go (the show).
 * Scenes are started by the demo, or by writing "prep 1" / "go 1" / "run 1" into gen/scene.txt (testing and recording).
 */
final class Scenes {
	private static final Path TRIGGER = Path.of("C:/mod/work/b46_1205/gen/scene.txt");
	private static final List<BlockPos> built = new ArrayList<>();

	/** Scene frame: o = the player's feet, f = forward and r = right (Minecraft x/z), base = ground level (feet y). */
	private static double ox, oz, fx, fz, rx, rz;
	private static int base;

	/** Where each scene happens: GTA x, y, z, heading, hour, weather. */
	private static final double[][] LOC = {
		{},
		{306.0, -800.0, 30.0, 180.0, 12, 0},    // 1 house
		{306.0, -800.0, 30.0, 180.0, 12, 0},    // 2 roadblock
		{306.0, -800.0, 30.0, 180.0, 21, 0},    // 3 mob war (same street, at night)
		{306.0, -800.0, 30.0, 180.0, 12, 0},    // 4 daylight raid
	};
	private static final String[] WEATHER = {"EXTRASUNNY", "CLEAR"};

	/** Scene 10 happens on scene 1's street. */
	private static int li(final int n) {
		return n == 10 ? 1 : n;
	}

	// eased camera (a mouse hand: smooth turns, never a snap), sent every 50 ms while camHold
	private static boolean camHold, camMoving;
	private static double camH, camP, camFromH, camFromP, camToH, camToP;
	private static long camTick, camT0, camDur;

	private static void cam(final double h, final double p, final double sec) {
		camFromH = camH;
		camFromP = camP;
		camToH = h;
		camToP = p;
		camT0 = camTick;
		camDur = Math.max(1, Math.round(sec * 20));
		camMoving = true;
	}

	private static void camTick() {
		camTick++;
		if (!camHold) {
			return;
		}

		if (camMoving) {
			double u = Math.min(1.0, (double) (camTick - camT0) / camDur);
			double e = u * u * (3 - 2 * u);
			camH = camFromH + (camToH - camFromH) * e;
			camP = camFromP + (camToP - camFromP) * e;
			camMoving = u < 1.0;
		}

		Xo.gta("look", "heading", camH, "pitch", camP);
	}

	private static final int[] ORDER = {4, 2, 1, 3};
	private static final double[] LENGTH = {16, 19.5, 19.5, 20};
	private static int risen;
	private static long fightUntil;

	/** The demo: each scene when the previous one is over. */
	private static void chain(final int i) {
		if (i >= ORDER.length) {
			return;
		}

		if (i > 0) {
			prep(ORDER[i], false);
		}

		Xo.after(i > 0 ? 1.2 : 0, () -> {
			run(ORDER[i]);
			Xo.after(LENGTH[i], () -> chain(i + 1));
		});
	}

	/** Steve fights back like a player: sword swings at the nearest hostile mob in reach, with hit sound and knockback. */
	private static void fight() {
		if (System.currentTimeMillis() > fightUntil) {
			return;
		}

		ServerPlayer p = Xo.player();
		if (p == null) {
			return;
		}

		var mobs = Xo.near(p.position(), 8, e -> e instanceof net.minecraft.world.entity.monster.Monster && !MobWar.isProxy(e));
		if (mobs.isEmpty()) {
			return;
		}

		var target = mobs.get(0);
		p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		target.hurtServer(Xo.level(), Xo.level().damageSources().playerAttack(p), 7f);
		Xo.sound(SoundEvents.PLAYER_ATTACK_STRONG, target.position(), 1f, 1f);
		Xo.particles(ParticleTypes.CRIT, target.position().add(0, 1, 0), 10, 0.3);
	}

	private Scenes() {
	}

	static void init() {
		Xo.every(0.5, Scenes::poll);
		Xo.every(0.05, Scenes::camTick);
		// on the street before anything starts; always a teleport, it is what re-levels the ground and puts Steve there
		Xo.onLink(() -> prep(1, true));
		// the demo: three scenes back to back, each when the previous is over; no outside commands
		Xo.demo(0.1, () -> Xo.gta("relevel"));
		Xo.demo(0.5, () -> whenGround(1, 60, () -> chain(0)));
		// GTA's dead leave Minecraft loot: a puff of souls and an experience orb
		// ...and a person who dies in GTA rises as a Minecraft zombie right there, and joins the fight
		Xo.on("death", m -> {
			var a = m.getAsJsonArray("mc");
			Vec3 v = new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble() + 0.3, a.get(2).getAsDouble());
			Xo.particles(ParticleTypes.SOUL, v.add(0, 0.8, 0), 25, 0.5);
			Xo.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, v, 1f, 1f);
			if (risen++ < 10) {
				var z = Xo.spawn(EntityTypes.ZOMBIE, v);
				if (z != null) {
					z.setPersistenceRequired();
				}
			}
		});
		Xo.every(0.45, Scenes::fight);
		Xo.every(0.05, () -> {
			ServerPlayer p = Xo.player();
			if (p != null && p.getHealth() < 9f && !p.getAbilities().invulnerable) {
				p.setHealth(20f);
			}
		});
		// Steve always stands where GTA's player stands, even right after a restart of Minecraft
		Xo.every(0.5, () -> {
			ServerPlayer p = Xo.player();
			Vec3 g = Xo.gtaPlayer();
			if (p != null && g != null && p.position().distanceTo(g) > 3.0) {
				p.connection.teleport(g.x, g.y, g.z, p.getYRot(), p.getXRot());
			}
		});
		Xo.every(2, () -> {
			ServerPlayer p = Xo.player();
			if (p != null) {
				Xo.log("steve " + p.position() + " ground=" + p.onGround() + " gta=" + Xo.gtaPlayer() + " yoff=" + Xo.yOffset()
					+ " below=" + Xo.level().getBlockState(p.blockPosition().below()).isAir());
			}
		});
	}

	private static void poll() {
		try {
			if (!Files.exists(TRIGGER)) {
				return;
			}

			String cmd = Files.readString(TRIGGER).trim();
			Files.delete(TRIGGER);
			Xo.log("scene command: " + cmd);
			String[] w = cmd.split("\\s+");
			if (w[0].equals("tp")) {
				Xo.teleportGtaWorld(Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3]), Float.parseFloat(w[4]));
				return;
			}

			if (w[0].equals("cmd")) {
				Xo.command(cmd.substring(4));
				return;
			}

			if (w[0].equals("op")) {
				Object[] kv = new Object[w.length - 2];
				System.arraycopy(w, 2, kv, 0, kv.length);
				for (int i = 1; i < kv.length; i += 2) {
					try {
						kv[i] = Double.valueOf((String) kv[i]);
					} catch (NumberFormatException e) {
						// stays a string
					}
				}

				Xo.gta(w[1], kv);
				return;
			}

			int n = Integer.parseInt(w[1]);
			switch (w[0]) {
				case "prep" -> prep(n);
				case "go" -> go(n);
				case "run" -> {
					prep(n);
					Xo.after(7, () -> go(n));
				}
				default -> Xo.log("unknown scene command");
			}
		} catch (Exception e) {
			Xo.log("scene trigger: " + e);
		}
	}

	// ---------------------------------------------------------------- frame helpers

	static void cleanup() {
		ServerLevel level = Xo.level();
		for (BlockPos p : built) {
			level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
		}

		built.clear();
		Xo.clear();
		MobWar.clearMobs();
		Xo.command("kill @e[type=!player]");
		Xo.gta("copsclear");
		camHold = false;
		Xo.gta("police", "stars", 0);
	}

	static void prep(final int n) {
		prep(n, true);
	}

	static void prep(final int n, final boolean teleport) {
		cleanup();
		Xo.gta("mod.reset");
		double[] l = LOC[li(n)];
		if (teleport) {
			Xo.teleportGtaWorld(l[0], l[1], l[2], (float) l[3]);
		}

		Xo.gta("time", "h", (int) l[4], "m", 0);
		Xo.gta("weather", "w", WEATHER[(int) l[5]]);
		Xo.gta("view", "mode", 1);
	}

	private static void frame(final int n) {
		ServerPlayer p = Xo.player();
		double h = Math.toRadians(LOC[li(n)][3]);
		// GTA's own player position (Steve may still be falling toward the ground columns)
		Vec3 g = Xo.gtaPlayer();
		ox = g != null ? g.x : p.getX();
		oz = g != null ? g.z : p.getZ();
		base = g != null ? (int) Math.round(g.y) : (int) Math.floor(p.getY() + 0.01);
		fx = -Math.sin(h);
		fz = -Math.cos(h);
		rx = -fz;
		rz = fx;
	}

	/** Minecraft position f blocks ahead, r to the right, up above the ground. */
	private static Vec3 at(final double f, final double r, final double up) {
		return new Vec3(ox + fx * f + rx * r, base + up, oz + fz * f + rz * r);
	}

	private static float gtaHeading(final int n, final double turn) {
		return (float) (LOC[li(n)][3] + turn);
	}

	private static void put(final Vec3 v, final Block b) {
		BlockPos pos = BlockPos.containing(v.x, v.y + 0.01, v.z);
		Xo.level().setBlockAndUpdate(pos, b.defaultBlockState());
		built.add(pos);
	}

	private static void place(final double sec, final double f, final double r, final int up, final Block b) {
		Xo.after(sec, () -> {
			put(at(f, r, up), b);
			if (Math.random() < 0.3) {
				Xo.sound(SoundEvents.STONE_PLACE, at(f, r, up), 0.8f, 0.9f + (float) Math.random() * 0.3f);
			}
		});
	}

	/** Starts a scene once Steve stands on GTA's ground (right after a teleport he is still falling through the void). */
	private static void go(final int n) {
		whenGround(n, 40, () -> run(n));
	}

	private static long lastTp;

	private static void whenGround(final int n, final int tries, final Runnable then) {
		ServerPlayer p = Xo.player();
		Vec3 g = Xo.gtaPlayer();
		double[] l = LOC[li(n)];
		Vec3 want = Xo.fromGta(l[0], l[1], l[2]);
		boolean there = g != null && Math.hypot(g.x - want.x, g.z - want.z) < 30;
		if (g != null && !there && System.currentTimeMillis() - lastTp > 3500) {
			// GTA's player is somewhere else (story start): bring him to the street again
			lastTp = System.currentTimeMillis();
			Xo.teleportGtaWorld(l[0], l[1], l[2], (float) l[3]);
		}

		if (p != null && g != null && p.position().distanceTo(g) > 3.0) {
			p.connection.teleport(g.x, g.y, g.z, p.getYRot(), p.getXRot());
		}

		boolean ready = p != null && there && !Xo.level().getBlockState(p.blockPosition().below()).isAir() && p.position().distanceTo(g) < 3.0;
		if (!ready && tries > 0) {
			Xo.after(0.25, () -> whenGround(n, tries - 1, then));
			return;
		}

		then.run();
	}

	private static void run(final int n) {
		frame(n);
		switch (n) {
			case 1 -> house(n);
			case 2 -> roadblock(n);
			case 3 -> mobWar(n);
			case 4 -> raid(n);
			case 10 -> handBuilt(n);
			default -> Xo.log("no scene " + n);
		}
	}

	// ---------------------------------------------------------------- scene 1: a house on the street

	private static void house(final int n) {
		Xo.title("BLOCK BY BLOCK", "a Minecraft house in Los Santos", 3);
		Xo.gta("look", "heading", 0, "pitch", 9);
		final double f0 = 17;
		final double R0 = 4.5;
		double t = 1.0;
		final double dt = 0.025;
		final int W = 7, D = 4;
		// dirt foundation, planks and glass walls, stepped roof
		for (int d = 0; d <= D; d++) {
			for (int r = -W; r <= W; r++) {
				if (d == 0 || d == D || r == -W || r == W) {
					place(t += dt, f0 + d, r + R0, 0, Blocks.DIRT);
				}
			}
		}

		for (int up = 1; up <= 3; up++) {
			for (int d = 0; d <= D; d++) {
				for (int r = -W; r <= W; r++) {
					boolean edge = d == 0 || d == D || r == -W || r == W;
					if (!edge) {
						continue;
					}

					boolean corner = (d == 0 || d == D) && (r == -W || r == W);
					boolean door = d == 0 && r == 0 && up <= 2;
					boolean window = up == 2 && ((d == 0 && (Math.abs(r) == 2 || Math.abs(r) == 5)) || (r == -W && d == 2) || (r == W && d == 2));
					if (door) {
						continue;
					}

					place(t += dt, f0 + d, r + R0, up, corner ? Blocks.OAK_LOG : window ? Blocks.GLASS : Blocks.OAK_PLANKS);
				}
			}
		}

		// gable roof running along the street
		for (int layer = 0; layer < 3; layer++) {
			for (int d = -1 + layer; d <= D + 1 - layer; d++) {
				for (int r = -W - 1; r <= W + 1; r++) {
					place(t += dt, f0 + d, r + R0, 4 + layer, layer == 1 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
				}
			}
		}

		place(t += dt, f0 + 2, R0, 7, Blocks.COBBLESTONE);
		place(t += dt, f0 - 1, R0 - 2, 1, Blocks.TORCH);
		place(t += dt, f0 - 1, R0 + 2, 1, Blocks.TORCH);
		final double done = t;
		Xo.after(done + 0.2, () -> {
			Xo.sound(SoundEvents.ANVIL_USE, at(f0 + 2, R0, 2), 1f, 1f);
			Xo.particles(ParticleTypes.HAPPY_VILLAGER, at(f0 + 2, R0, 7), 50, 4.0);
		});
		for (int i = 0; i < 2; i++) {
			final int k = i;
			Xo.after(done - 1.0 + i * 0.5, () -> Xo.ped("walker" + k, k % 2 == 0 ? "a_f_y_hipster_01" : "a_m_y_hipster_01",
				at(7, k % 2 == 0 ? -9 : 9, 0.3), "flee"));
		}
		// the crash: planks fly where the cars hit, GTA blasts and flames
		final double[] hit = {0.5, 1.0, 1.4};
		final double[] hr = {R0 - 3, R0 + 3.5, R0 - 3};
		for (int i = 0; i < 3; i++) {
			final int k = i;
			Xo.after(done + hit[i], () -> {
				for (int up = 1; up <= 2; up++) {
					for (int dr = -1; dr <= 1; dr++) {
						Xo.level().destroyBlock(BlockPos.containing(at(f0, hr[k] + dr, up).x, at(f0, hr[k] + dr, up).y + 0.01, at(f0, hr[k] + dr, up).z), true);
					}
				}

				Vec3 c = at(f0 - 1, hr[k], 1);
				Xo.boom(c, k == 2 ? 4 : 0, k == 2 ? 0.8 : 0.4);
				Xo.ptfx("core", "ent_sht_steam", c, 2.0);
				Xo.sound(SoundEvents.GENERIC_EXPLODE.value(), c, 1f, 1f);
			});
		}

		Xo.after(done + 2.5, () -> Xo.text("~y~Minecraft~s~ 1 - 0 ~b~Los Santos traffic", 4));
		// traffic comes from behind the camera: the first car brakes hard, the next ones pile in
		final double[] rs = {R0 - 3, R0 - 3, R0 + 3.5, R0 + 3.5};
		final String[] models = {"sultan", "taxi", "buffalo", "police"};
		final double[] fs = {-30, -44, -32, -48};
		final int[] brake = {1500, 0, 1550, 0};
		final double[] when = {0.0, 0.5, 1.2, 1.7};
		for (int i = 0; i < 4; i++) {
			final int k = i;
			Xo.after(done - 2.0 + when[i], () -> Xo.gta("mod.car", "id", "traffic" + k, "model", models[k], "mc", at(fs[k], rs[k], 0.3),
				"h", gtaHeading(n, 0), "speed", 24, "brake_ms", brake[k]));
		}
	}

	// ---------------------------------------------------------------- scene 2: TNT on an LSPD roadblock

	private static void roadblock(final int n) {
		Xo.title("TNT vs THE LSPD", "five stars. Minecraft answers.", 2.5);
		Xo.gta("look", "heading", 0, "pitch", 7);
		Xo.gta("police", "stars", 5);
		final int cars = 4;
		final double R0 = 4.5, gap = 5.8;
		for (int i = 0; i < cars; i++) {
			double r = R0 + (i - 1.5) * gap;
			Xo.car("lspd" + i, i % 2 == 0 ? "police" : "police2", at(11, r, 0.2), gtaHeading(n, 90), 0);
			Xo.ped("cop" + i, "s_m_y_cop_01", at(14, r, 0.2), "fight");
		}

		// TNT falls from the sky onto each car in turn, each block a GTA explosion
		for (int i = 0; i < cars; i++) {
			final int k = i;
			for (int j = 0; j < 2; j++) {
				final int jj = j;
				Xo.after(3.5 + k * 2.4 + j * 1.1, () -> {
					Vec3 p = at(11 + (jj == 0 ? -1.0 : 1.0), R0 + (k - 1.5) * gap + (jj == 0 ? -1 : 1), 8 + jj * 2);
					PrimedTnt tnt = Xo.spawn(EntityTypes.TNT, p);
					if (tnt != null) {
						tnt.setFuse(28);
					}

					Xo.sound(SoundEvents.TNT_PRIMED, p, 1f, 1f);
				});
				if (j == 1) {
					Xo.after(3.5 + k * 2.4 + j * 1.1 + 1.5, () -> Xo.boom(at(11, R0 + (k - 1.5) * gap, 0.5), 4, 1.0));
				}
			}
		}

		Xo.after(3.0, () -> Xo.text("~r~TNT~s~ rains on the roadblock", 4));
		Xo.after(5.5, () -> Xo.gta("view", "mode", 4));
		Xo.after(9.5, () -> Xo.gta("view", "mode", 1));
		Xo.after(15.5, () -> Xo.text("~y~Minecraft~s~ 1 - 0 ~b~LSPD", 3));
	}

	// ---------------------------------------------------------------- scene 3: zombies and skeletons vs the LSPD

	private static void mobWar(final int n) {
		Xo.title("NIGHT OF THE MOBS", "zombies and skeletons vs the LSPD", 2.5);
		Xo.gta("look", "heading", 0, "pitch", -7);
		Xo.gta("cops", "cars", 5, "dist", 13, "line", 1);
		Xo.after(2.5, () -> {
			MobWar.spawn("zombie", 7, 11, 17, 55, 0, null);
			MobWar.spawn("skeleton", 6, 11, 17, 55, 0, null);
		});
		Xo.after(8, () -> {
			MobWar.spawn("zombie", 5, 11, 16, 55, 0, null);
			MobWar.spawn("skeleton", 4, 12, 17, 55, 0, null);
		});
		Xo.after(11, () -> MobWar.spawn("creeper", 4, 9, 13, 40, 0, null));
		Xo.after(13, () -> MobWar.spawn("zombie", 5, 10, 15, 55, 0, null));
		// a player's mouse: slow eased looks around the fight, a few steps forward then back
		camHold = true;
		camH = 0;
		camP = -7;
		camMoving = false;
		Xo.after(3.0, () -> cam(-1.5, -8, 3.5));
		Xo.after(8.0, () -> cam(2.0, -6.5, 4.0));
		Xo.after(13.0, () -> cam(0, -8, 3.5));
		Xo.after(3.5, () -> Xo.text("Arrows against pistols", 4));
		Xo.after(15, () -> Xo.text("~g~Minecraft~s~ vs ~b~LSPD", 3));
	}

	// ---------------------------------------------------------------- scene 4: a daylight raid, Steve fights

	private static void raid(final int n) {
		Xo.title("MINECRAFT INVADES", "Los Santos fights back", 2.5);
		Xo.gta("look", "heading", 0, "pitch", -4);
		camHold = true;
		camH = 0;
		camP = -4;
		camMoving = false;
		risen = 0;
		fightUntil = System.currentTimeMillis() + 15000;
		// Steve plays in survival for this fight: hits flash red, knock him back, hearts drop (a heal keeps him alive)
		Xo.command("gamemode survival @a");
		Xo.command("effect give @a minecraft:resistance 17 3 true");
		Xo.after(15.5, () -> Xo.command("gamemode creative @a"));
		Xo.gta("cops", "cars", 4, "dist", 12, "line", 1);
		// creatures right in front of the camera from the first second
		MobWar.spawn("zombie", 6, 7, 11, 45, 0, null);
		MobWar.spawn("skeleton", 3, 8, 12, 45, 0, null);
		Xo.after(0.5, () -> MobWar.spawn("zombie", 4, 6, 10, 55, 0, null));
		Xo.after(3.0, () -> MobWar.spawn("creeper", 3, 8, 12, 40, 0, null));
		Xo.after(6.5, () -> MobWar.spawn("zombie", 5, 9, 13, 50, 0, null));
		Xo.after(2.0, () -> cam(-1.5, -5, 3.5));
		Xo.after(8.0, () -> cam(1.5, -4, 4.0));
		walkTo(2.2, 3.0, 0, 1.0, n);
		// first person like real Minecraft: the sword hand in view while Steve fights, then back to third person
		Xo.after(6.0, () -> Xo.gta("view", "mode", 4));
		Xo.after(10.0, () -> Xo.gta("view", "mode", 1));
		Xo.after(3.0, () -> Xo.text("Steve swings. ~g~Minecraft~s~ mobs hunt the ~b~LSPD", 4));
		Xo.after(10.0, () -> Xo.text("Anyone who falls in Los Santos rises as a ~g~zombie", 4));
	}

	// ---------------------------------------------------------------- scene 10: Steve builds by hand

	/** One block by hand: the arm swings, the block appears with its own place sound. */
	private static void hand(final double sec, final double f, final double r, final int up, final Block b) {
		Xo.after(sec, () -> {
			ServerPlayer p = Xo.player();
			if (p != null) {
				p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			}

			Vec3 v = at(f, r, up);
			put(v, b);
			Xo.sound(b.defaultBlockState().getSoundType().getPlaceSound(), v, 1f, 0.9f + (float) Math.random() * 0.2f);
		});
	}

	private static void holding(final double sec, final String item) {
		Xo.after(sec, () -> Xo.command("item replace entity @a weapon.mainhand with minecraft:" + item));
	}

	private static void walkTo(final double sec, final double f, final double r, final double speed, final int n) {
		Xo.after(sec, () -> {
			double[] g = Xo.toGta(at(f, r, 0));
			Xo.gta("walk", "x", g[0], "y", g[1], "z", g[2], "speed", speed, "timeout", 6000, "h", gtaHeading(n, 0));
		});
	}

	private static void handBuilt(final int n) {
		Xo.title("BUILD IT YOURSELF", "no commands, just Steve", 2.5);
		camHold = true;
		camH = 0;
		camP = 8;
		camMoving = false;
		final Random rnd = new Random(7);
		final double f0 = 10, R0 = 3.0;
		holding(0.1, "dirt");
		walkTo(0.4, 5.5, 0, 1.6, n);
		cam(-3, 8, 2.5);
		Xo.after(2.9, () -> Xo.gta("stop"));
		double t = 3.0;
		// 1) dirt floor, front to back
		for (int d = 0; d <= 2; d++) {
			for (int r = -2; r <= 2; r++) {
				if (d == 1 && Math.abs(r) < 2) {
					continue;
				}

				hand(t += 0.11 + rnd.nextDouble() * 0.06, f0 + d, R0 + r, 0, Blocks.DIRT);
			}
		}

		t += 0.3;
		holding(t, "oak_planks");
		t += 0.35;
		cam(2, 8, 2.5);
		// 2) planks, two layers (door gap at the front, windows left for later)
		for (int up = 1; up <= 2; up++) {
			for (int d = 0; d <= 2; d++) {
				for (int r = -2; r <= 2; r++) {
					boolean edge = d == 0 || d == 2 || r == -2 || r == 2;
					boolean door = d == 0 && r == 0;
					boolean window = up == 2 && ((d == 0 && Math.abs(r) == 1) || (d == 1 && Math.abs(r) == 2));
					if (!edge || door || window) {
						continue;
					}

					hand(t += 0.11 + rnd.nextDouble() * 0.06, f0 + d, R0 + r, up, Blocks.OAK_PLANKS);
				}
			}
		}

		t += 0.3;
		holding(t, "glass");
		t += 0.35;
		hand(t += 0.2, f0, R0 - 1, 2, Blocks.GLASS);
		hand(t += 0.2, f0, R0 + 1, 2, Blocks.GLASS);
		hand(t += 0.2, f0 + 1, R0 - 2, 2, Blocks.GLASS);
		hand(t += 0.2, f0 + 1, R0 + 2, 2, Blocks.GLASS);
		t += 0.3;
		holding(t, "cobblestone");
		t += 0.3;
		cam(0, 11, 1.5);
		// 3) the roof is out of reach: jump and place at the top of the jump, three rows
		for (int d = 0; d <= 2; d++) {
			Xo.after(t, () -> Xo.gta("mod.jump"));
			double tj = t + 0.38;
			for (int r = -2; r <= 2; r++) {
				hand(tj += 0.13, f0 + d, R0 + r, 3, Blocks.COBBLESTONE);
			}

			t += 1.2;
		}

		final double done = t;
		cam(0, 7, 2.2);
		// 4) the street reacts: traffic from behind the camera piles into the new house
		final double[] rs = {R0 + 0.3, R0 - 1.3, R0 + 1.5};
		final String[] models = {"sultan", "taxi", "buffalo"};
		final double[] fs = {-26, -34, -29};
		final int[] brake = {1000, 0, 0};
		for (int i = 0; i < 3; i++) {
			final int k = i;
			Xo.after(done - 4.4 + i * 0.6, () -> Xo.gta("mod.car", "id", "traffic" + k, "model", models[k], "mc", at(fs[k], rs[k] > 0 ? rs[k] : 1.7, 0.3),
				"h", gtaHeading(n, 0), "speed", 22, "brake_ms", brake[k]));
		}

		for (int i = 0; i < 2; i++) {
			final double hr = R0 + (i == 0 ? -1 : 1.5);
			Xo.after(done - 1.0 + i * 0.7, () -> {
				Vec3 c = at(f0 - 1, hr, 1);
				Xo.boom(c, 0, 0.4);
				Xo.ptfx("core", "ent_sht_steam", c, 2.0);
				Xo.sound(SoundEvents.GENERIC_EXPLODE.value(), c, 1f, 1f);
				for (int up = 1; up <= 2; up++) {
					Xo.level().destroyBlock(BlockPos.containing(at(f0, hr, up).x, at(f0, hr, up).y + 0.01, at(f0, hr, up).z), true);
				}
			});
		}

		Xo.after(done + 0.3, () -> Xo.text("Built by hand. ~r~Traffic~s~ did not read the sign.", 4));
	}
}
