// Example crossover mod, GTA side ("Minecraft Crossover"): Minecraft drives most of it through the kit's xo.* bricks;
// this file adds the two things only GTA can do.
//   1. GTA -> Minecraft: every GTA person who dies near the player is reported ("death"); Minecraft raises a husk there.
//   2. A mod op Minecraft calls by name: Xo.gta("bounty.rain", "n", 6) drops loose money bags around the player.
// Runs inside rehan's script.cpp (anonymous namespace): natives::*, xon::*, xo_* bricks, WAIT, worldGetAllPeds.

	struct XoBrake { Ped driver; Vehicle veh; int at; bool done; };
	std::vector<XoBrake> g_brakes;
	std::unordered_set<Ped> g_reportedDead;
	int g_nextDeathScan = 0;

	void mod_connected(Ped player)
	{
		(void)player;
		g_reportedDead.clear();
		g_brakes.clear();
		xo_text("~g~Minecraft~s~ x ~y~Los Santos~s~: Minecraft Crossover", 5000);
	}

	void mod_tick(Ped player)
	{
		const int now = natives::GetGameTimer();
		// a calmer street: fewer moving cars and no parked ones, so nothing fills the camera
		invoke<Void>(0x245A6883D966D537, 0.35f);   // SET_VEHICLE_DENSITY_MULTIPLIER_THIS_FRAME
		invoke<Void>(0xB3B3359379FE77D3, 0.3f);    // SET_RANDOM_VEHICLE_DENSITY_MULTIPLIER_THIS_FRAME
		invoke<Void>(0xEAE6DCC7EEE3DB1D, 0.0f);    // SET_PARKED_VEHICLE_DENSITY_MULTIPLIER_THIS_FRAME
		for (auto &b : g_brakes)
		{
			if (b.done || now < b.at)
				continue;
			b.done = true;
			if (natives::DoesEntityExist(b.veh) && natives::DoesEntityExist(b.driver))
			{
				invoke<Void>(0xC429DCEEB339E129, b.driver, b.veh, 1, 2500);   // TASK_VEHICLE_TEMP_ACTION 1 = brake
				invoke<Void>(0x9C8C6504B5B63D2C, b.veh, 1200, 0);              // START_VEHICLE_HORN
			}
		}
		if (now < g_nextDeathScan)
			return;
		g_nextDeathScan = now + 500;
		const Vector3 me = natives::GetEntityCoords(player, TRUE);
		int peds[256];
		const int n = worldGetAllPeds(peds, 256);
		for (int i = 0; i < n; ++i)
		{
			const Ped p = peds[i];
			if (p == player || g_doublePeds.count(p) || g_reportedDead.count(p) || !natives::IsPedDeadOrDying(p))
				continue;
			const Vector3 at = natives::GetEntityCoords(p, FALSE);
			const float dx = at.x - me.x, dy = at.y - me.y;
			if (dx * dx + dy * dy > 60.0f * 60.0f)
				continue;
			g_reportedDead.insert(p);
			char mc[96];
			xo_to_mc_json(at, mc, sizeof(mc));
			xo_emit("death", "\"mc\":%s", mc);
		}
		if (g_reportedDead.size() > 512)
			g_reportedDead.clear(); // old bodies are long gone; a re-report is harmless
	}


	bool mod_op(Ped player, const std::string &op, const std::string &m)
	{
		if (op == "mod.fade")
		{
			if (json_num(m, "out", 0) != 0)
				invoke<Void>(0x891B5B39AC6302AF, int(json_num(m, "ms", 300)));   // DO_SCREEN_FADE_OUT
			else
				invoke<Void>(0xD4E8E24955024033, int(json_num(m, "ms", 500)));   // DO_SCREEN_FADE_IN
			return true;
		}
		if (op == "mod.reset")
		{
			// out of any vehicle, no leftover task: every scene starts with a clear view
			if (invoke<BOOL>(0x997ABD671D25CA0B, player, FALSE))
			{
				const Vehicle v = invoke<Vehicle>(0x9A9112A0FE9A4713, player, FALSE);
				invoke<Void>(0xD3DBCE61A490BE02, player, v, 16);               // TASK_LEAVE_VEHICLE (warp out)
			}
			invoke<Void>(0xAAA34F8A7CB32098, player);                          // CLEAR_PED_TASKS_IMMEDIATELY
			return true;
		}
		if (op == "mod.jump")
		{
			xon::SetEntityVelocity(player, 0.0f, 0.0f, 5.6f); // a hop on the spot
			return true;
		}
		// mod.car: a car with a driver, pushed at a speed, that brakes (and hoots) after brake_ms. Keys: id, model, mc, h, speed, brake_ms
		if (op == "mod.car")
		{
			const std::string id = json_str(m, "id");
			const Vehicle v = xo_spawn_car(id, json_str(m, "model"), xo_pos(player, m), float(json_num(m, "h", 0)), 0.0f);
			if (v == 0)
				return true;
			const Hash dm = xo_load_model("a_m_y_business_01");
			if (dm != 0)
			{
				const Ped d = invoke<Ped>(0x7DD959874C1FD534, v, 26, dm, -1, FALSE, FALSE); // CREATE_PED_INSIDE_VEHICLE
				natives::SetModelAsNoLongerNeeded(dm);
				xo_track(id + "_d", d, 1);
				invoke<Void>(0x2497C4717C8B881E, v, TRUE, TRUE);                           // SET_VEHICLE_ENGINE_ON
				const int ms = int(json_num(m, "brake_ms", 0));
				if (ms > 0 && d != 0)
					g_brakes.push_back({d, v, natives::GetGameTimer() + ms, false});
			}
			const float speed = float(json_num(m, "speed", 0));
			if (speed > 0.0f)
				xon::SetVehicleForwardSpeed(v, speed);
			return true;
		}
		return false;
	}
