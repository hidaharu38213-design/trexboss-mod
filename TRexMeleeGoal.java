package com.example.trexboss.entity.goal;

import com.example.trexboss.entity.TRexBossEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * ウォーデンと同じ間隔の近接攻撃。
 * 攻撃開始から次の攻撃開始まで18tick(0.9秒)。ヒットは開始8tick後(咬みつきの瞬間)。
 */
public class TRexMeleeGoal extends Goal {
    private static final int CYCLE_TICKS = 18;   // 攻撃間隔(ウォーデン=18tick)
    private static final int HIT_TICK = 8;       // 攻撃アニメ内でダメージが入るタイミング
    private static final double REACH = 11.0;     // 攻撃できる距離(ブロック)

    private final TRexBossEntity mob;
    private int attackTick = -1;                 // -1 = 攻撃していない
    private int repathTimer;

    public TRexMeleeGoal(TRexBossEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && !mob.isSummoning();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mob.setAggressive(true);   // 敵を認識 → runアニメに切り替わる
        mob.playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0f, 0.6f);
        attackTick = -1;
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        mob.getNavigation().stop();
        attackTick = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        double dist = mob.distanceTo(target);

        if (attackTick < 0) {
            if (dist > REACH) {
                if (--repathTimer <= 0) {
                    repathTimer = 10;
                    mob.getNavigation().moveTo(target, 1.2);   // ウォーデンの追跡倍率
                }
            } else {
                mob.getNavigation().stop();
                attackTick = 0;
                mob.triggerAnim("attack", "attack");
            }
            return;
        }

        mob.getNavigation().stop();
        attackTick++;
        if (attackTick == HIT_TICK && dist <= REACH + 3.0) {
            mob.doHurtTarget(target);
            mob.playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 2.0f, 0.8f);
        }
        if (attackTick >= CYCLE_TICKS) {
            attackTick = -1;
        }
    }
}
