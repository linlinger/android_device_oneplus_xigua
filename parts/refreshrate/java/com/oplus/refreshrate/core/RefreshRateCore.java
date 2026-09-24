/*
 * Copyright (C) 2026 Oplus Project
 *
 * Oplus RefreshRate Service — 净室实现
 *
 * 调度核心: 基于"投票 (Vote)"的刷新率决策。
 * 架构参考 ColorOS OPlusRefreshRateCore 的行为 (净室重写, 非 OPPO 代码):
 *   - 多个场景/组件各自"投票"请求一个刷新率
 *   - 每个投票有优先级 (0-150, 越高越优先)
 *   - 从最高优先级向下取, 高优先级 vote 可覆盖低优先级的 min/max 约束
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 刷新率投票核心 — 决定最终刷新率。
 *
 * 纯算法, 无 Android 依赖, 可独立测试。
 */
public final class RefreshRateCore {

    /**
     * 优先级常量 (对齐 ColorOS 的行为语义)。
     * 数值越大越优先。0-150 区间。
     */
    public static final class Priority {
        public static final int MIN = 0;
        public static final int APP_REQUEST = 10;        // 应用请求
        public static final int FLEXIBLE_WINDOW = 20;    // 自由窗口
        public static final int GAME_REQUEST = 25;       // 游戏请求
        public static final int ANIMATION_BOOST = 40;    // 动画加速
        public static final int HIGH_BRIGHTNESS = 50;    // 高亮度
        public static final int DC_MODE = 60;            // DC 调光
        public static final int MEMC = 70;               // 补帧
        public static final int HIGH_TEMPERATURE = 80;   // 高温
        public static final int LAUNCHER_REQUEST = 85;   // 桌面请求
        public static final int OSYNC = 100;             // 系统同步
        public static final int LOWRATE_DISPLAY = 110;   // 低刷新率显示
        public static final int PAD_MIRAGE = 120;        // 平板投屏
        public static final int AOD = 121;               // 息屏显示
        public static final int FOD_SHOW = 130;          // 屏下指纹
        public static final int RATING_MODE = 140;       // 评分模式
        public static final int NEAR_FLASH = 150;        // 闪光灯 (最高)
        public static final int MAX = 150;
    }

    /** 优先级从高到低的遍历顺序 */
    private static final List<Integer> PRIORITIES = Arrays.asList(
            Priority.NEAR_FLASH, Priority.RATING_MODE, Priority.FOD_SHOW,
            Priority.AOD, Priority.PAD_MIRAGE, Priority.LOWRATE_DISPLAY,
            Priority.OSYNC, Priority.LAUNCHER_REQUEST, Priority.HIGH_TEMPERATURE,
            Priority.MEMC, Priority.DC_MODE, Priority.HIGH_BRIGHTNESS,
            Priority.ANIMATION_BOOST, Priority.GAME_REQUEST,
            Priority.FLEXIBLE_WINDOW, Priority.APP_REQUEST, Priority.MIN);

    /** 单个投票: 一个场景/组件对刷新率的请求 */
    public static final class Vote {
        public final float refreshRate;      // 请求的刷新率 (Hz)
        public final float minRefreshRate;   // 最低可接受刷新率
        public final String requestSource;   // 请求来源 (调试用)

        public Vote(float refreshRate, float minRefreshRate, String requestSource) {
            this.refreshRate = refreshRate;
            this.minRefreshRate = minRefreshRate;
            this.requestSource = requestSource;
        }

        public Vote(float refreshRate, String requestSource) {
            this(refreshRate, refreshRate, requestSource);
        }

        @Override
        public String toString() {
            return "Vote{rate=" + refreshRate + ", min=" + minRefreshRate
                    + ", src='" + requestSource + "'}";
        }
    }

    /** 所有活跃投票, key = 优先级 */
    private final Map<Integer, Vote> mVotes = new HashMap<>();
    /** 是否冻结 (冻结时不允许刷新率变化) */
    private boolean mFreezing;

    /**
     * 决策: 从所有投票中选出最终刷新率。
     * 算法 (净室实现, 行为对齐 ColorOS):
     * 1. 从最高优先级向下遍历
     * 2. 取最高优先级 vote 的刷新率为候选
     * 3. 若更高优先级 vote 与当前候选差值 > 1Hz, 且低优先级 vote
     *    的 min 约束允许 → 覆盖
     * 4. 检查 min/max 约束, 冲突则提前终止
     */
    public float getPerfectRefreshRate(boolean topOnly) {
        float rate = -1.0f;
        float minRate = -1.0f;

        for (int i = PRIORITIES.size() - 1; i >= 0; i--) {
            Vote vote = mVotes.get(PRIORITIES.get(i));
            if (vote == null) {
                continue;
            }
            if (rate >= 0.0f) {
                // 已有候选, 检查约束
                if (rate - vote.refreshRate > 1.0f
                        && (minRate < vote.refreshRate
                            || Math.abs(minRate - vote.refreshRate) < 1.0f)) {
                    rate = vote.refreshRate;
                }
                if (vote.minRefreshRate > minRate) {
                    minRate = vote.minRefreshRate;
                }
                if (minRate >= rate || Math.abs(rate - minRate) < 1.0f
                        || vote.refreshRate <= minRate || vote.minRefreshRate >= rate) {
                    break;
                }
            } else {
                rate = vote.refreshRate;
                minRate = vote.minRefreshRate;
                if (topOnly || rate - minRate < 1.0f) {
                    break;
                }
            }
        }
        return rate;
    }

    /** 投票 (设置/更新一个优先级的投票) */
    public void setVote(int priority, Vote vote) {
        if (vote == null) {
            mVotes.remove(priority);
        } else {
            mVotes.put(priority, vote);
        }
    }

    /** 清除一个优先级的投票 */
    public void clearVote(int priority) {
        mVotes.remove(priority);
    }

    /** 清除所有投票 */
    public void clearAllVotes() {
        mVotes.clear();
    }

    /** 冻结/解冻 (冻结时刷新率锁定) */
    public void setFreezing(boolean freezing) {
        mFreezing = freezing;
    }

    public boolean isFreezing() {
        return mFreezing;
    }

    /** 当前最高优先级的投票优先级 */
    public int getCurMaxVotePriority() {
        for (int i = PRIORITIES.size() - 1; i >= 0; i--) {
            if (mVotes.get(PRIORITIES.get(i)) != null) {
                return PRIORITIES.get(i);
            }
        }
        return Priority.MIN;
    }

    /** 调试 dump */
    public String dumpVotes() {
        StringBuilder sb = new StringBuilder();
        for (int i = PRIORITIES.size() - 1; i >= 0; i--) {
            Vote vote = mVotes.get(PRIORITIES.get(i));
            if (vote != null) {
                sb.append("  [").append(PRIORITIES.get(i)).append("] ")
                        .append(vote).append('\n');
            }
        }
        return sb.toString();
    }
}
