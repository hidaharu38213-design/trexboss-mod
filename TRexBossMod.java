package com.example.trexboss;

import com.example.trexboss.entity.TRexBossEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.defaultattribute.v1.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public class TRexBossMod implements ModInitializer {
    public static final String MOD_ID = "trexboss";

    public static final EntityType<TRexBossEntity> TREX_BOSS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "trex_boss"),
            EntityType.Builder.of(TRexBossEntity::new, MobCategory.MONSTER)
                    .sized(6.0f, 8.5f)          // 当たり判定(横, 高さ)。モデルの高さ(約8.4)に合わせた。横は歩ける範囲を考えて6
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("trex_boss"));

    public static final Item TREX_SPAWN_EGG = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "trex_boss_spawn_egg"),
            new SpawnEggItem(TREX_BOSS, 0x4B5A32, 0xC8B67A, new Item.Properties()));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(TREX_BOSS, TRexBossEntity.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(entries -> entries.accept(TREX_SPAWN_EGG));
    }
}
