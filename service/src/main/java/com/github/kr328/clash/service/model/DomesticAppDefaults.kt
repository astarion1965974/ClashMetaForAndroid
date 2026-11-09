package com.github.kr328.clash.service.model

/**
 * Curated *exact Android package ids* for common Mainland China applications.
 * This is a one-time default for an unconfigured VPN, NOT a language / developer
 * prefix heuristic. Unknown or foreign applications remain on Clash by default.
 *
 * Keep package ids even when the app is not yet installed: VpnService.Builder
 * ignores unknown ids, and will apply the same default after future installs.
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
    )

    fun shouldInitialize(hasModeSetting: Boolean, hasPackageSetting: Boolean): Boolean =
        !hasModeSetting && !hasPackageSetting
}
