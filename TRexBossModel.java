package com.example.trexboss.client;

import com.example.trexboss.TRexBossMod;
import com.example.trexboss.entity.TRexBossEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TRexBossModel extends GeoModel<TRexBossEntity> {
    @Override
    public ResourceLocation getModelResource(TRexBossEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(TRexBossMod.MOD_ID, "geo/entity/trex_boss.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TRexBossEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(TRexBossMod.MOD_ID, "textures/entity/trex_boss.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TRexBossEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(TRexBossMod.MOD_ID, "animations/entity/trex_boss.animation.json");
    }
}
