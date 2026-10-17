package br.gohan.videofeed

/**
 * 运行时可改的服务器地址。
 * Swift 侧通过 IOSKoinHelperKt.setServerBase(url:) 修改，
 * 改了之后触发一次 Feed 刷新就能换源，不用重新编译 ipa。
 */
object ApiConfig {
    var baseUrl: String = "http://172.29.8.196:6688"
}
