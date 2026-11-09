package com.github.kr328.clash.service.model

/**
 * Curated *exact Android package ids* for common Mainland China applications.
 * Additional entries verified from mnixry/direct-android-ruleset,
 * rules/@Merged/APP.mutated.yaml (AGPL-3.0), snapshot 2026-11-09.
 * Source: https://github.com/mnixry/direct-android-ruleset
 * Exact package IDs only; avoid the upstream regex and foreign app rules.
 * This is a one-time default for an unconfigured VPN, NOT a language / developer
 * prefix heuristic. Unknown or foreign applications remain on Clash by default.
 *
 * Keep IDs for not-yet-installed apps in persisted preferences; TunService only
 * passes installed IDs to VpnService.Builder. Future installs inherit these
 * choices on the next VPN reconnect.
 * The user's saved per-app routes always override this initial snapshot.
 */
object DomesticAppDefaults {
    val bypassPackages: Set<String> = setOf(
        // Communication / productivity
        "com.tencent.mm",                  // WeChat
        "com.tencent.mobileqq",            // QQ
        "com.alibaba.android.rimet",        // DingTalk
        "com.ss.android.lark",              // Feishu
        "com.tencent.wemeet.app",          // Tencent Meeting
        "com.tencent.weread",              // WeRead
        // Payments / commerce
        "com.eg.android.AlipayGphone",     // Alipay
        "com.taobao.taobao",               // Taobao
        "com.tmall.wireless",              // Tmall
        "com.jingdong.app.mall",           // JD
        "com.xunmeng.pinduoduo",           // Pinduoduo
        "com.taobao.idlefish",             // Xianyu
        // Video / social / entertainment
        "com.ss.android.ugc.aweme",        // Douyin
        "com.ss.android.ugc.aweme.lite",   // Douyin Lite
        "com.smile.gifmaker",              // Kuaishou
        "com.kuaishou.nebula",             // Kuaishou Lite
        "com.xingin.xhs",                  // Xiaohongshu
        "tv.danmaku.bili",                 // Bilibili
        "com.sina.weibo",                  // Weibo
        "com.zhihu.android",               // Zhihu
        "com.ss.android.article.news",     // Toutiao
        "com.qiyi.video",                  // iQIYI
        "com.tencent.qqlive",              // Tencent Video
        "com.youku.phone",                 // Youku
        "com.netease.cloudmusic",          // NetEase Music
        "com.tencent.qqmusic",             // QQ Music
        "com.ximalaya.ting.android",       // Ximalaya
        // Maps / transport / local services
        "com.sankuai.meituan",             // Meituan
        "com.sankuai.meituan.takeoutnew",  // Meituan Delivery
        "com.dianping.v1",                 // Dianping
        "me.ele",                          // Eleme
        "com.autonavi.minimap",            // Amap
        "com.baidu.BaiduMap",              // Baidu Maps
        "com.sdu.didi.psnger",             // Didi
        "ctrip.android.view",              // Ctrip
        "com.MobileTicket",                // 12306
        // Search / browsers and tools
        "com.baidu.searchbox",             // Baidu
        "com.tencent.mtt",                 // QQ Browser
        "com.UCMobile",                    // UC Browser
        "com.quark.browser",               // Quark
        "cn.wps.moffice_eng",              // WPS
        // Banking / finance (verified Android identifiers)
        "cmb.pb",                            // CMB
        "com.icbc",                          // ICBC
        "com.chinamworld.main",              // CCB
        "com.chinamworld.bocmbci",           // Bank of China
        "com.android.bankabc",               // ABC
        "com.bankcomm.Bankcomm",             // Bank of Communications
        "com.pingan.paces.ccms",             // Ping An Bank
        "com.cebbank.mobile.cemb",           // CEB
        "cn.com.spdb.mobilebank.per",        // SPD Bank
        "com.yitong.mbank.psbc",             // Postal Savings Bank
        "com.unionpay",                      // UnionPay
        "com.jd.jrapp",                      // JD Finance
        "com.antfortune.wealth",             // Ant Fortune
        "com.cmbchina.ccd.pluto.cmbActivity", // CMB credit card
        // Travel, shopping and local service
        "com.Qunar",                         // Qunar
        "com.tongcheng.android",             // Tongcheng
        "com.taobao.trip",                   // Fliggy
        "com.shizhuang.duapp",               // Dewu
        "com.achievo.vipshop",               // Vipshop
        "com.wudaokou.hippo",                // Hema
        "com.jingdong.pdj",                  // JD Daojia
        "com.tmri.app.main",                 // Traffic Police 12123
        "com.tencent.map",                   // Tencent Maps
        "com.sankuai.meituan.dispatch.crowdsource", // Meituan Crowdsource
        // Common domestic utilities
        "com.coolapk.market",                // Coolapk
        "com.baidu.netdisk",                 // Baidu Netdisk
        "com.alicloud.databox",              // Aliyun Drive
        "com.tencent.androidqqmail",         // QQ Mail
        "com.netease.mail",                  // NetEase Mail
        "com.xiaomi.smarthome",              // Mi Home
        "com.tencent.android.qqdownloader", // MyApp
        "com.hicorenational.antifraud",     // National Anti-fraud Center
        "cn.gov.tax.its",                    // IIT
        "cn.hsa.app",                        // National Healthcare Service
        // Reading / entertainment
        "com.douban.frodo",                  // Douban
        "com.douban.radio",                  // Douban FM
        "com.bilibili.comic",                // Bilibili Comics
        "tv.danmaku.bilibilihd",            // Bilibili HD
        "com.tencent.news",                  // Tencent News
        "com.qq.reader",                     // QQ Reader
        "app.podcast.cosmos",                // Xiaoyuzhou
        "com.lofter.android",                // LOFTER
        "com.netease.gl",                    // NetEase Dashen
        "com.ss.android.ugc.live",           // Douyin Huoshan
        "com.kuaishou.nebula",              // Kuaishou Lite
        "com.sina.weibolite",                // Weibo Lite
        // 银行、支付和金融
        "com.cib.cibmb", // 兴业银行
        "cn.com.cmbc.newmbank", // 民生银行
        "com.cgbchina.xpt", // 广发银行
        "com.nbbank", // 宁波银行
        "cn.com.shbank.mper", // 上海银行
        "com.mybank.android.phone", // 网商银行
        "com.webank.wemoney", // 微众银行
        "cn.gov.pbc.dcep", // 数字人民币
        "com.bankcomm.maidanba", // 买单吧
        "com.duxiaoman.wallet", // 度小满
        "com.pingan.lifeinsurance", // 平安金管家
        // 消费、商超和外卖
        "com.suning.mobile.ebuy", // 苏宁易购
        "com.netease.yanxuan", // 网易严选
        "com.wuba.zhuanzhuan", // 转转
        "aihuishou.aihuishouapp", // 爱回收
        "com.yaya.zone", // 叮咚买菜
        "com.pupumall.customer", // 朴朴超市
        "cn.yonghui.hyd", // 永辉线上超市
        "cn.samsclub.app", // 山姆会员商店
        "com.mcdonalds.gma.cn", // 麦当劳
        "com.yek.android.kfc.activitys", // 肯德基
        "com.jd.jdhealth", // 京东健康
        "com.cainiao.wireless", // 菜鸟
        "com.sf.activity", // 顺丰速运
        "cn.ishansong", // 闪送
        // 社交、阅读与影音
        "com.baidu.tieba", // 百度贴吧
        "com.zhihu.daily.android", // 知乎日报
        "com.hunantv.imgo.activity", // 芒果TV
        "com.luna.music", // 汽水音乐
        "com.kugou.android", // 酷狗音乐
        "cn.kuwo.player", // 酷我音乐
        "cmccwm.mobilemusic", // 咪咕音乐
        "com.cmcc.cmvideo", // 咪咕视频
        "cn.cntv", // 央视影音
        "com.cctv.yangshipin.app.androidp", // 央视频
        "air.tv.douyu.android", // 斗鱼
        "com.duowan.kiwi", // 虎牙直播
        "com.tencent.qqsports", // 腾讯体育
        "com.dragon.read", // 番茄免费小说
        "com.kmxs.reader", // 七猫小说
        "com.chaozh.iReaderFree", // 掌阅
        "com.qidian.QDReader", // 起点读书
        "com.qq.ac.android", // 腾讯动漫
        "bubei.tingshu", // 懒人听书
        // 出行、旅行与生活服务
        "com.umetrip.android.msky.app", // 航旅纵横
        "com.flightmanager.view", // 航班管家
        "com.jingyao.easybike", // 哈啰出行
        "cn.caocaokeji.user", // 曹操出行
        "com.didapinche.booking", // 嘀嗒出行
        "com.huaxiaozhu.rider", // 花小猪
        "com.lalamove.huolala.client", // 货拉拉
        "com.ygkj.chelaile.standard", // 车来了
        "com.chebada", // 巴士管家
        "com.mygolbs.mybus", // 掌上公交
        "com.rytong.ceair", // 东方航空
        "com.csair.mbp", // 南方航空
        "com.china3s.android", // 春秋航空
        "com.anjuke.android.app", // 安居客
        "com.lianjia.beike", // 贝壳找房
        "com.homelink.android", // 链家
        "com.soufun.app", // 房天下
        "com.wuba", // 58同城
        "com.ss.android.auto", // 懂车帝
        "com.cubic.autohome", // 汽车之家
        "com.yiche.autoeasy", // 易车
        "com.moji.mjweather", // 墨迹天气
        // 办公、学习与拍照工具
        "com.xunlei.downloadprovider", // 迅雷
        "com.youdao.note", // 有道云笔记
        "com.baidu.baidutranslate", // 百度翻译
        "com.youdao.dict", // 有道词典
        "com.sohu.inputmethod.sogou", // 搜狗输入法
        "com.baidu.input", // 百度输入法
        "com.iflytek.inputmethod", // 讯飞输入法
        "com.baidu.wenku", // 百度文库
        "com.baidu.homework", // 作业帮
        "com.fenbi.android.solar", // 小猿搜题
        "com.yuantiku.tutor", // 猿辅导
        "com.xueersi.parentsmeeting", // 学而思网校
        "com.fenbi.android.servant", // 粉笔
        "com.mt.mtxx.mtxx", // 美图秀秀
        "com.xt.retouch", // 醒图
        "com.lemon.lv", // 剪映
        "com.gotokeep.keep", // Keep
        "com.boohee.one", // 薄荷健康
        "com.lingan.seeyou", // 美柚
        "com.wandoujia.phoenix2", // 豌豆荚
        "com.baidu.searchbox.lite", // 百度极速版
        "sogou.mobile.explorer", // 搜狗浏览器
        "com.cat.readall", // 悟空浏览器
        "com.xiaomi.shop", // 小米商城
        "com.oppo.store", // OPPO商城
        "com.vmall.client", // 华为商城
        // 运营商、政务和公共服务
        "com.greenpoint.android.mc10086.activity", // 中国移动
        "com.sinovatech.unicom.ui", // 中国联通
        "com.ct.client", // 中国电信
        "com.sgcc.wsgw.cn", // 网上国网
        "cn.xuexi.android", // 学习强国
        "com.hanweb.android.zhejiang.activity", // 浙里办
        "com.digitalgd.dgyss", // 粤省事
        "com.zcbl.bjjj_driving", // 北京交警
    )

    fun shouldInitialize(hasModeSetting: Boolean, hasPackageSetting: Boolean): Boolean =
        !hasModeSetting && !hasPackageSetting
}
