package com.example.trexboss.client;

import com.example.trexboss.TRexBossMod;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class TRexBossClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(TRexBossMod.TREX_BOSS, TRexBossRenderer::new);
    }
}
