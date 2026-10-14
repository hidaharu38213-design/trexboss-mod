ティラノサウルス・ボスMOD (Fabric 1.21.1 + GeckoLib 4)

【ステータス】 体力1000 / 攻撃力60 / 攻撃間隔18tick(ウォーデンと同じ)
  数値: src/main/java/com/example/trexboss/entity/TRexBossEntity.java の MAX_HEALTH / ATTACK_DAMAGE
  間隔: entity/goal/TRexMeleeGoal.java の CYCLE_TICKS (ダメージが入るタイミングは HIT_TICK)

【大きさ】 モデル自体を元の2倍に拡大済み(高さ約8.4・体長約19ブロック。元ファイルは tyrannosaurus-rex_x2.bbmodel)
  当たり判定は 横6×高さ8.5(TRexBossMod.java の sized)。攻撃の届く距離は TRexMeleeGoal.java の REACH

【敵対】 アンデットと他のティラノ以外すべてに敵対(entity/TRexBossEntity.java の isEnemy)

【召喚フェーズ】 体力が下がると animation.model.new4 のポーズ(約2.4秒)をして、その途中でモブを召喚
  800以下: スケルトン5体 / 600以下: スケルトンホースマン3体 / 400以下: ウィザースケルトン10体 / 200以下: ウィザー
  数値・体数は entity/TRexBossEntity.java の PHASE_HP と spawnPhaseMobs()
  召喚モブにはタグ trexboss_minion が付き、ティラノは攻撃せず、ティラノも彼らからダメージを受けません。
  Mob.targetSelector を使うため src/main/resources/trexboss.accesswidener が必要です(設定済み)。

【モデル】 tyrannosaurus-rex.bbmodel から変換済み(書き出し作業は不要)
  assets/trexboss/geo/entity/trex_boss.geo.json
  assets/trexboss/animations/entity/trex_boss.animation.json
  assets/trexboss/textures/entity/trex_boss.png            ← 通常スキン(使用中)
  assets/trexboss/textures/entity/trex_boss_skeleton.png   ← 骨スキン(未使用)
  骨スキンにしたい場合は client/TRexBossModel.java の "trex_boss.png" を "trex_boss_skeleton.png" に変えるだけ。

【ビルド】 gradle build  →  build/libs/trexboss-1.0.0.jar
  gradle wrapperは入っていないので、IntelliJでフォルダを開くか `gradle wrapper` を実行してください。
【出し方】 /summon trexboss:trex_boss またはクリエイティブのスポーンエッグ
