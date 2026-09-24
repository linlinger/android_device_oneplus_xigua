# 刷新率调度配置标准 (OplusRefreshRate)

本服务**直接兼容 ColorOS 原厂配置文件**, 无需转换。
同时支持自定义配置路径, 方便适配其他设备。

## 配置文件位置 (按优先级)

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 1 | `/my_product/etc/refresh_rate_config.xml` | ColorOS 原厂 (my_product 分区) |
| 2 | `/data/system/refresh_rate_config.xml` | 运行时更新版 |
| 3 | `/system/etc/refresh_rate_config.xml` | 自定义 (类原生) |

VRR 配置 (`oplus_vrr_config.json`) 同样三个路径。

## 配置文件 1: refresh_rate_config.xml (应用级映射)

### 格式
```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<refresh_rate_config version="20240529" ratemagic="8550T60_90_120">
  <item package="com.tencent.qqmusic" rateId="2-1-2-1" />
  <item package="com.netease.cloudmusic"
        activity=".activity.MLogVideoActivity"
        rateId="2-2-2-1"
        appreqfirst="true"
        applowRefreshRate="30"
        lowRateWinType="1"
        disableViewOverride="true"
        lowfreq="true" />
</refresh_rate_config>
```

### rateId 语义 (核心!)
```
rateId = "auto-90-60-120"  四个值, 对应四种系统模式:
  0 = unspecified (默认/不指定)
  1 = 90Hz
  2 = 60Hz
  3 = 120Hz

例:
  "2-2-2-2" = 所有模式 60Hz (默认, 大多数应用)
  "3-2-2-3" = auto:120, 90Hz:60, 60Hz:60, 120Hz:120
  "2-1-2-1" = auto:60, 90Hz:90, 60Hz:60, 120Hz:90 (音乐类)
  "0-0-0-0" = 全部 unspecified (跟随系统)
```

### 属性
| 属性 | 类型 | 说明 |
|------|------|------|
| package | string | 包名 (必填) |
| activity | string | 活动名 (可选, 精确匹配) |
| rateId | string | 四档刷新率 (见上) |
| appreqfirst | bool | 应用请求优先 |
| applowRefreshRate | int | 应用低刷新率 (如 30) |
| lowRateWinType | int | 低刷新率窗口类型 |
| disableViewOverride | bool | 禁用视图覆盖 |
| lowfreq | bool | 视频低刷新率名单 |

### 分类 (原厂注释)
Music / Video / News / Reading / 漫画 / Office / Talk / Browser /
System / Phone / Shopping / 便携生活 / Game / 交通导航 / 教育学习 /
金融理财 / 拍照美化 / 系统应用 / CTS

## 配置文件 2: oplus_vrr_config.json (全局策略)

### 格式
```json
[
  {"filter_name": "oplus_adfr_config"},
  {"version": 20250403},
  {"feature_sa": "true"},
  {"feature_osync": 3},
  {
    "sa_backlight": true,
    "sa_backlight_strategy": [
      {"fps": 120, "list": ["70:120", "300:60", "1500:120"]}
    ]
  },
  {
    "game_list": [
      {"pkg_name": ["com.tencent.tmgp.sgame"],
       "backlight": true,
       "backlight_strategy": [{"fps": 120, "list": ["70:120", "300:60", "1500:120"]}],
       "kfc": true, "kfc_target": [60, 120], "kfc_jitter": [60, 120]}
    ]
  },
  {"blacklist": [包名...]},
  {"sw_whitelist_3rd": [包名...]},
  {"timeout": 3500},
  {"hw_enable": true},
  {"sw_enable": true},
  {"adfr_enable": false},
  {"deferred_mode_change": true}
]
```

### 关键字段
| 字段 | 类型 | 说明 |
|------|------|------|
| feature_sa | string "true"/"false" | SA (省电) 特性 |
| feature_osync | int | OSYNC 等级 |
| sa_backlight | bool | 背光策略开关 |
| sa_backlight_strategy | array | 背光→刷新率映射 (fps + list) |
| sa_backlight_strategy.list | string[] | `"亮度nit:刷新率"` 列表, 从高到低匹配 |
| game_list | array | 游戏专属策略 |
| blacklist | string[] | 应用黑名单 (不参与智能调度) |
| sw_whitelist_3rd | string[] | 第三方白名单 |
| timeout | int | 静止降频超时 (ms), 默认 3500 |
| hw_enable | bool | 硬件 VRR 开关 |
| sw_enable | bool | 软件 VRR 开关 |
| adfr_enable | bool | ADFR 开关 |
| deferred_mode_change | bool | 延迟模式切换 |

### sa_backlight_strategy 语义
```json
{"fps": 120, "list": ["70:120", "300:60", "1500:120"]}
```
含义 (从高到低匹配):
- 亮度 >= 1500nit → 120Hz
- 亮度 >= 300nit → 60Hz
- 亮度 >= 70nit → 120Hz
- < 70nit → 默认

**目的**: 高亮度下降频省电 + 防过热, 低亮度保持流畅。

## 游戏策略示例 (原厂 19 个游戏)
```
com.tencent.tmgp.sgame      (王者荣耀)  backlight + kfc [60,120]
com.tencent.tmgp.speedmobile (QQ飞车)   backlight + kfc
com.tencent.tmgp.pubgmhd    (和平精英)  backlight + kfc
com.tencent.tmgp.cf         (穿越火线)  backlight + kfc
com.tencent.lolm            (英雄联盟)  backlight + kfc
com.netease.harrypotter     (哈利波特)
com.netease.sky             (光遇)
... (共 19 个)
```

## 自定义标准 (本服务扩展)

除原厂格式外, 支持自定义增强 (不冲突):
- `/system/etc/oplus_vrr_custom.json`: 追加规则 (白名单覆盖黑名单等)
- 未来: 通过设置 App 生成用户自定义应用配置

## 测试方法
1. 把原厂配置放到 `/my_product/etc/` (或 `/system/etc/`)
2. 启动服务, 看 logcat `ORR/Config` 日志:
   ```
   loaded 1930 configs from /my_product/etc/refresh_rate_config.xml
   loaded vrr config v20250403, games=19, blacklist=115
   ```
3. 切换应用, 观察 `ORR/Scene` 日志的投票变化
