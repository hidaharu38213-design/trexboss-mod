package com.example.trexboss.client;

import com.example.trexboss.entity.TRexBossEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TRexBossRenderer extends GeoEntityRenderer<TRexBossEntity> {
    public TRexBossRenderer(EntityRendererProvider.Context context) {
        super(context, new TRexBossModel());
        this.shadowRadius = 4.0f;
    }
}
