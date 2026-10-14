package com.example.trexboss.entity.goal;

import com.example.trexboss.entity.TRexBossEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** 召喚ポーズ中は歩く・攻撃するなどの行動を止める。 */
public class TRexSummonFreezeGoal extends Goal {
    private final TRexBossEntity mob;

    public TRexSummonFreezeGoal(TRexBossEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return mob.isSummoning();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isSummoning();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }
}
