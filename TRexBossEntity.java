package com.example.trexboss.entity;

import com.example.trexboss.entity.goal.TRexMeleeGoal;
import com.example.trexboss.entity.goal.TRexSummonFreezeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TRexBossEntity extends Monster implements GeoEntity {
    // ===== ステータス =====
    public static final double MAX_HEALTH = 1000.0;
    public static final double ATTACK_DAMAGE = 60.0;   // ハート30個ぶん(防具で軽減される)

    // ===== 召喚フェーズ(体力がこの値以下になったら順番に発動) =====
    //   0: スケルトン5体 / 1: スケルトンホースマン3体 / 2: ウィザースケルトン10体 / 3: ウィザー1体
    private static final double[] PHASE_HP = {800.0, 600.0, 400.0, 200.0};
    private static final int SUMMON_DURATION = 48;      // ポーズの長さ(animation.model.new4 = 2.375秒 ≒ 48tick)
    private static final int SUMMON_SPAWN_TICK = 28;    // ポーズ開始から何tick後にモブを出すか
    /** 召喚モブに付けるタグ。ティラノは攻撃せず、ティラノもモブからダメージを受けない。 */
    public static final String MINION_TAG = "trexboss_minion";

    // ===== アニメーション名(Blockbenchのアニメ名と一致させる) =====
    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK   = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN    = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation DEATH  = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation SUMMON = RawAnimation.begin().thenPlayAndHold("animation.model.new4");

    /** 攻撃アニメ(1.75秒)をウォーデンの間隔(18tick=0.9秒)に収める再生倍率 */
    private static final double ATTACK_ANIM_SPEED = 1.75 / 0.9;
    private static final int DEATH_TICKS = 42;          // deathアニメ 2.05秒 ≒ 41tick

    private static final EntityDataAccessor<Boolean> SUMMONING =
            SynchedEntityData.defineId(TRexBossEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private int nextPhase = 0;       // 次に発動するフェーズ
    private int summonPhase = -1;    // いま召喚中のフェーズ
    private int summonTicks = 0;     // 召喚ポーズの残りtick

    public TRexBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.3)       // ウォーデンと同じ
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 3.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SUMMONING, false);
    }

    public boolean isSummoning() {
        return this.entityData.get(SUMMONING);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new TRexSummonFreezeGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TRexMeleeGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // アンデットと他のティラノ以外(サバイバルのプレイヤー・村人・動物・モブ全部)に敵対
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, LivingEntity.class, 10, true, false, TRexBossEntity::isEnemy));
    }

    /** 敵対する相手か。アンデットと他のティラノ(と防具立て)以外はすべて敵。 */
    public static boolean isEnemy(LivingEntity e) {
        if (e instanceof TRexBossEntity || e instanceof ArmorStand) return false;
        if (e.getType().is(EntityTypeTags.UNDEAD)) return false;     // スケルトン・ゾンビ・ウィザーなど
        if (e.getTags().contains(MINION_TAG)) return false;          // 召喚モブ(念のため)
        return true;   // クリエイティブ/スペクテイターのプレイヤーはバニラの仕様でそもそも狙えない
    }

    /** 召喚したモブ(ウィザーの爆発・スケルトンの矢など)からはダメージを受けない。 */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        Entity causer = source.getEntity();
        Entity direct = source.getDirectEntity();
        if ((causer != null && causer.getTags().contains(MINION_TAG))
                || (direct != null && direct.getTags().contains(MINION_TAG))) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    // ===== ボスバー =====
    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        tickSummon();
    }

    // ===== 体力が減ったらポーズ → モブ召喚 =====
    private void tickSummon() {
        if (this.isDeadOrDying()) return;

        if (this.summonTicks > 0) {
            this.summonTicks--;
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            if (this.summonTicks == SUMMON_DURATION - SUMMON_SPAWN_TICK) {
                spawnPhaseMobs(this.summonPhase);
            }
            if (this.summonTicks == 0) {
                this.entityData.set(SUMMONING, false);
            }
            return;
        }

        if (this.nextPhase < PHASE_HP.length && this.getHealth() <= PHASE_HP[this.nextPhase]) {
            this.summonPhase = this.nextPhase++;
            this.summonTicks = SUMMON_DURATION;
            this.entityData.set(SUMMONING, true);      // クライアントで animation.model.new4 を再生
            this.setTarget(this.getTarget());
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 4.0f, 0.6f);
        }
    }

    private void spawnPhaseMobs(int phase) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        switch (phase) {
            case 0 -> {
                for (int i = 0; i < 5; i++) {
                    Skeleton s = createMinion(sl, EntityType.SKELETON, 0);
                    if (s != null) { protectFromSun(s); sl.addFreshEntity(s); puff(sl, s); }
                }
            }
            case 1 -> {
                for (int i = 0; i < 3; i++) {
                    SkeletonHorse horse = createMinion(sl, EntityType.SKELETON_HORSE, 0);
                    Skeleton rider = createMinion(sl, EntityType.SKELETON, 0);
                    if (horse == null || rider == null) continue;
                    horse.setTamed(true);                 // 乗っているスケルトンを振り落とさない
                    protectFromSun(rider);
                    sl.addFreshEntity(horse);
                    rider.moveTo(horse.getX(), horse.getY(), horse.getZ(), horse.getYRot(), 0.0f);
                    sl.addFreshEntity(rider);
                    rider.startRiding(horse, true);
                    puff(sl, horse);
                }
            }
            case 2 -> {
                for (int i = 0; i < 10; i++) {
                    Mob w = createMinion(sl, EntityType.WITHER_SKELETON, 0);
                    if (w != null) { sl.addFreshEntity(w); puff(sl, w); }
                }
            }
            case 3 -> {
                WitherBoss wither = createMinion(sl, EntityType.WITHER, 4);
                if (wither != null) {
                    // ウィザーがティラノやスケルトンたちを狙わないように、ターゲット選択を差し替える
                    wither.targetSelector.removeAllGoals(g -> g instanceof NearestAttackableTargetGoal<?>);
                    wither.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                            wither, LivingEntity.class, 0, false, false, TRexBossEntity::isEnemy));
                    sl.addFreshEntity(wither);
                    puff(sl, wither);
                }
            }
            default -> { }
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON,
                net.minecraft.sounds.SoundSource.HOSTILE, 3.0f, 0.7f);
    }

    /** モブを生成してタグ付け・初期装備まで済ませる(ワールドへの追加は呼び出し側)。 */
    private <T extends Mob> T createMinion(ServerLevel sl, EntityType<T> type, double extraY) {
        T mob = type.create(sl);
        if (mob == null) return null;
        Vec3 p = findSpawnPos(sl, mob);
        mob.moveTo(p.x, p.y + extraY, p.z, this.random.nextFloat() * 360.0f, 0.0f);
        mob.addTag(MINION_TAG);
        mob.setPersistenceRequired();
        mob.finalizeSpawn(sl, sl.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        return mob;
    }

    /** ティラノの周囲9〜15ブロックで、地面があって埋まらない場所を探す。 */
    private Vec3 findSpawnPos(ServerLevel sl, Mob mob) {
        for (int i = 0; i < 16; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = 9.0 + this.random.nextDouble() * 6.0;
            double x = this.getX() + Math.cos(angle) * dist;
            double z = this.getZ() + Math.sin(angle) * dist;
            int surface = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            double y = Math.abs(surface - this.getY()) > 8.0 ? this.getY() : surface;
            mob.setPos(x, y, z);
            if (sl.noCollision(mob)) return new Vec3(x, y, z);
        }
        return new Vec3(this.getX(), this.getY() + 1.0, this.getZ());
    }

    /** スケルトンが日光で燃え続けないように(炎の見た目は出ることがある)。 */
    private static void protectFromSun(Mob mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
    }

    private static void puff(ServerLevel sl, Entity e) {
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1.0, e.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SummonPhase", this.nextPhase);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.nextPhase = tag.getInt("SummonPhase");
    }

    /** 頭やしっぽが当たり判定の外に出るので、画面外判定(カリング)の範囲を広げる。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(10.0, 2.0, 10.0);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ===== 死亡アニメ後に消える =====
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                ExperienceOrb.award(serverLevel, this.position(), this.xpReward);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    // ===== サウンド(エンドラ+ウォーデンの音を低いピッチで流用) =====
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f;   // 声を低く(巨大な恐竜っぽく)
    }

    @Override
    protected float getSoundVolume() {
        return 2.0f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.WARDEN_STEP, 1.2f, 0.6f);
    }

    // ===== GeckoLib =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .setAnimationSpeed(ATTACK_ANIM_SPEED));
    }

    private PlayState mainPredicate(AnimationState<TRexBossEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (this.isSummoning()) {
            return state.setAndContinue(SUMMON);      // animation.model.new4 のポーズ
        }
        if (state.isMoving()) {
            // 敵を認識している間は run、認識していない間は walk
            return state.setAndContinue(this.isAggressive() ? RUN : WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
