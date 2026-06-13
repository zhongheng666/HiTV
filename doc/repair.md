# HiTV 代码审查与修复方案

> 审查范围：`app/src/main/java/com/htlac/hitv/` 全部 Kotlin 源码
> 审查日期：2026-06-12

---

## 一、严重 (P0 — 会导致崩溃/数据丢失/功能异常)

### 1.1 `replaceAll` 非原子事务 — 频道数据丢失灾难

**文件:** `core/data/local/ChannelDao.kt:24`

**问题:** 注释声称"使用 @Transaction 保证原子动作"，但 `deleteAll()` 和 `insertChannels()` 是两个独立的 DAO 方法。Room 的 `@Transaction` 仅包裹在这两个调用外面时才会生效，但 **当前代码的 `@Transaction` 注解已经存在** — 此问题已修复。

**但是**，`ChannelRepository.syncChannelsFromUrl()` 在 collect 到数据后才调用 `replaceAll()`。如果 M3U 解析过程中 **抛出异常**（网络中断），数据不会被写入，这是正确的。但如果 **解析成功但频道数为 0**（M3U 文件为空或格式不对），也不会写入，这也是正确的。

**结论:** 此问题已不存在（注释中的"深度修复"确实有效）。标记为 **已修复**，保留关注。

---

### 1.2 EPG 删除时机 — 真空期断层

**文件:** `core/data/repository/EpgRepository.kt:55-57`

```kotlin
// 当前代码：在第一批数据到达时才删除旧数据
if (!isOldDataCleared && batchPrograms.isNotEmpty()) {
    epgDao.deleteAll()  // ← 旧数据被删除
    isOldDataCleared = true
}
epgDao.insertPrograms(batchPrograms)
```

**问题:** 如果解析器在下载过程中 **中途失败**（如网络中断、XML 损坏），已删除的旧数据无法恢复，导致 EPG 出现 **真空期**。虽然代码有 `isOldDataCleared` 标记和 try-catch，但 `deleteAll()` 已经在 `collect` 流内部被执行，**catch 块无法回滚已删除的数据**。

**修复方案:** 先在内存中收集全部新数据，解析成功后再删除旧数据并批量插入：

```kotlin
suspend fun syncEpgFromUrl(epgUrl: String) {
    _epgSyncEvent.emit("📡 EPG 开始下载解析...")
    
    // 1. 收集全部新数据（不在 collect 中操作数据库）
    val allChannels = channelDao.getAllChannels().firstOrNull() ?: emptyList()
    val tvgIdToHash = allChannels.filter { it.tvgId.isNotEmpty() }
        .associateBy({ it.tvgId }, { it.urlHash })
    val nameToHash = allChannels.associateBy({ it.name }, { it.urlHash })
    
    val newPrograms = mutableListOf<EpgProgram>()
    try {
        epgParser.parse(epgUrl, tvgIdToHash, nameToHash)
            .catch { e ->
                Log.e(TAG, "EPG 解析失败: ${e.message}", e)
                _epgSyncEvent.emit("❌ EPG 解析失败")
                throw e
            }
            .collect { batch -> newPrograms.addAll(batch) }
        
        // 2. 全部解析成功后，才替换旧数据（原子操作）
        if (newPrograms.isNotEmpty()) {
            epgDao.deleteAll()
            epgDao.insertPrograms(newPrograms)
            val dbCount = epgDao.getProgramCount()
            Log.i(TAG, "EPG 更新成功！实存 $dbCount 条")
            _epgSyncEvent.emit("✅ EPG 更新成功！(实存 $dbCount 条)")
        }
    } catch (e: Exception) {
        Log.e(TAG, "EPG 严重异常", e)
        _epgSyncEvent.emit("❌ EPG 严重异常")
        // 旧数据完好无损
    }
}
```

---

### 1.3 NTP 时间偏移计算错误 — 节目单匹配错乱

**文件:** `core/network/NtpManager.kt:26-27`

```kotlin
val now = SystemClock.elapsedRealtime()  // ← 开机以来的毫秒数（含睡眠）
timeOffset = networkTime - now           // ← 用绝对时间戳减去相对时间
```

**问题:** `SntpClient.requestTime()` 返回的是 **Unix 时间戳**（绝对时间，如 `1718180400000`），而 `SystemClock.elapsedRealtime()` 是 **系统开机以来的毫秒数**（相对时间，如 `3600000`）。两者相减得到的 `timeOffset` 毫无意义，导致 `getCurrentTime()` 返回完全错误的时间，所有 EPG 节目查询都会失败。

**修复方案:** 在 NTP 响应时同时记录绝对时间和相对时间：

```kotlin
private var ntpTimeMillis: Long? = null
private var elapsedRealtimeAtSync: Long? = null

suspend fun syncTime() = withContext(Dispatchers.IO) {
    SntpClient.requestTime(ntpServer, 3000).onSuccess { networkTime ->
        ntpTimeMillis = networkTime
        elapsedRealtimeAtSync = SystemClock.elapsedRealtime()
        _isSyncedFlow.value = true
    }.onFailure {
        _isSyncedFlow.value = false
    }
}

fun getCurrentTime(): Long {
    return ntpTimeMillis ?: System.currentTimeMillis()
}
```

---

### 1.4 SntpClient 时间戳计算潜在问题

**文件:** `core/network/SntpClient.kt:40`

```kotlin
val responseTime = requestTime + (responseTicks - requestTicks)
```

**问题:** `requestTime` 使用 `System.currentTimeMillis()`，而 `requestTicks`/`responseTicks` 使用 `SystemClock.elapsedRealtime()`。这两者之间可能存在 **时间跳跃**（NTP 校正、用户手动修改时间等），直接用它们做减法不准确。

**修复方案:** 在发送请求和接收响应的时刻分别读取 `SystemClock.elapsedRealtime()` 和 `System.currentTimeMillis()`，用 NTP 协议的标准公式计算：

```kotlin
val t1 = SystemClock.elapsedRealtime()
val t1Millis = System.currentTimeMillis()
// ... send request ...
socket.receive(response)
val t4 = SystemClock.elapsedRealtime()
val t4Millis = System.currentTimeMillis()

// 往返延迟
val delay = (t4 - t1) - (t3Millis - t1Millis) - (t4Millis - t3Millis)  // 简化：2 * RTT
// 实际上用原始的 NTP 公式（基于服务器返回的 originateTime/receiveTime/transmitTime）
val clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2
return responseTime + clockOffset
```

**当前代码实际上使用了标准 NTP 偏移公式**（第 46 行），但 `responseTime` 的估算（第 40 行）不够精确。在实际网络中误差通常在可接受范围内，但如果 NTP 服务器的 `originateTime`/`receiveTime`/`transmitTime` 与客户端 `responseTime` 的差值较大，会导致偏移量计算偏差。建议 **保持当前实现**（这是 Android SntpClient 的简化版本），但在 NTP 同步失败时有合理降级。

---

### 1.5 Hilt 单例双重实例风险

**文件:** `core/player/MpvPlayer.kt:19-22`, `core/player/Media3Player.kt:42-45`

```kotlin
@Singleton          // ← Hilt 保证单例
class MpvPlayer @Inject constructor(...) : PlayerController, MPVLib.EventObserver {
```

**问题:** 两个播放器都被标记为 `@Singleton`，意味着 Hilt 会在应用生命周期内各创建 **唯一实例**。`PlayerViewModel` 同时注入两者，这本身没问题。但 `MpvPlayer` 的 `MPVLib.create(context)` 和 `MPVLib.init()` 在 `init {}` 块中执行，如果 **Hilt 提前初始化**（如在 Application.onCreate 期间），context 可能尚未完全就绪。

**实际风险:** 低。因为 `HitvApplication.onCreate()` 只做了 `super.onCreate()`，没有触发任何依赖注入，所以 Hilt 单例会在首次请求时才创建，此时 Application 已完全就绪。

**结论:** 无实际风险，标记为 **观察项**。

---

## 二、高危 (P1 — 内存泄漏/性能问题/用户体验)

### 2.1 `LaunchedEffect(Unit)` 无限循环 — 时钟内存泄漏

**文件:** `core/player/PlayerScreen.kt:261-267`

```kotlin
LaunchedEffect(Unit) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    while (true) {            // ← 永不退出
        currentTimeString = formatter.format(Date())
        delay(1000)
    }
}
```

**问题:** `LaunchedEffect(Unit)` 在 `showClock == true` 时启动，但 **没有对应的退出条件**。即使 `showClock` 变为 `false` 导致 Composable 脱离树，`while(true)` 协程仍会持续运行（直到 Composable 被完全 Disposable），且 `currentTimeString` 的每次 `mutableStateOf` 赋值都会触发重组。如果时钟面板被频繁切换，会创建大量无法回收的协程。

**修复方案:**

```kotlin
if (viewModel.showClock && !viewModel.isSyncing) {
    val currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(currentTimeString) { }  // 显式依赖
    
    // 或者更好的方式：
    val clockTime by rememberUpdatedState(currentTimeString)
}

// 更好的实现：
@Composable
fun ClockDisplay(isNtpSynced: Boolean) {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(1000)
        }
    }
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Text(text = formatter.format(Date(now.longValue)), ...)
}
```

或使用 `snapshotFlow` + `collect`:

```kotlin
val clockText by remember {
    mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()))
}
LaunchedEffect(Unit) {
    snapshotFlow { viewModel.showClock && !viewModel.isSyncing }
        .filter { it }.collect { running ->
            if (running) {
                while (running) {
                    currentTimeString = clockText.format(Date())
                    delay(1000)
                }
            }
        }
}
```

---

### 2.2 `channelDao.getAllChannels()` 返回 `Flow` 但被当作一次性查询

**文件:** `core/data/repository/EpgRepository.kt:43`

```kotlin
val allChannels = channelDao.getAllChannels().firstOrNull() ?: emptyList()
```

**问题:** `getAllChannels()` 是一个 Room Flow 查询，返回的是持续监听的流。在 `syncEpgFromUrl` 中用 `firstOrNull()` 只取第一个值 **是正确做法**，但每次 EPG 同步都会 **重新查询整个 channels 表**构建字典。对于几千个频道的场景，这是 O(n) 操作，不是性能瓶颈但不够优雅。

**结论:** 功能正确，无明显性能问题。

---

### 2.3 `CoroutineScope` 未正确清理 — EPG 守护进程泄漏

**文件:** `core/network/EpgSyncDaemon.kt:29`

```kotlin
private val daemonScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

**问题:** `EpgSyncDaemon` 是 `@Singleton`，`daemonScope` 在其生命周期内 **永远不会被取消**。`start()` 方法中的 `while(isActive)` 循环在应用退出前永不停止。虽然 `SupervisorJob()` 本身不会被完成，但当 `Activity` 退出后，守护进程仍在后台运行并持有 `EpgRepository` 和 `SettingsManager` 的引用 — 这 **不会导致内存泄漏**（因为它们也是 Singleton），但如果未来在 Daemon 中持有其他非 Singleton 引用，就会泄漏。

**修复方案:** 添加 `@ContributesAndroidInjector` 或使用 `AndroidViewModel` 管理作用域：

```kotlin
@Singleton
class EpgSyncDaemon @Inject constructor(...) {
    private val daemonScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 提供一个清理方法
    fun cancel() {
        daemonScope.cancel()
    }
}
```

在 `HitvApplication` 的 `onTerminate()` 中调用（如果 API 可用），或在 `ProcessLifecycleOwner` 的 `ON_STOP` 时调用。

**实际风险:** 低，因为所有引用都是 Singleton。但在某些低端设备上，无限循环的协程会在每次重启应用时多一个（实际上是同一个，因为进程重启）。

---

### 2.4 `SettingsManager.iptvHistoryFlow` 每次读取都追加当前 URL

**文件:** `core/data/datastore/SettingsManager.kt:40-44`

```kotlin
val iptvHistoryFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
    val history = preferences[IPTV_HISTORY] ?: emptySet()
    val current = preferences[IPTV_URL] ?: ""
    if (current.isNotBlank()) history + current else history  // ← 每次 map 都追加！
}
```

**问题:** 这是一个 **纯展示侧** 的问题 — `iptvHistoryFlow` 在每次 DataStore 值变化时都会将当前 URL 重新追加到历史记录流中。由于 `+` 运算符在 Set 上返回新 Set，如果当前值已在集合中，不会重复（Set 去重）。但如果 DataStore 中有多个 key 变化（如同时保存了 IPTV 和 EPG），这个 flow 会被多次触发，理论上不会导致数据膨胀。

**但是**，`saveIptvUrl()` 中也手动追加了 URL（第 56-58 行），与 `iptvHistoryFlow` 的自动追加逻辑 **重复** — 两次追加同一 URL 到同一个 Set 不会产生重复（Set 天然去重），但造成了 **不必要的写操作**。

**结论:** 功能正确，存在轻微的性能浪费，可接受。

---

### 2.5 媒体 3 播放器 `internalPlay` 每次都不重置 decoder count

**文件:** `core/player/Media3Player.kt:97-116`

**问题:** `AnalyticsListener` 中的 `onVideoDecoderInitialized` 回调只在 **首次** 解码器初始化时触发。当 `startSilentReconnect()` 触发重新播放时，`currentDecoder` 不会更新，导致 debug 信息显示过时的解码器名称。

**修复方案:** 在 `updateDebugInfo()` 之前也重置 `currentDecoder = "重新连接中..."`，或者在 `onPlaybackStateChanged` 的 `BUFFERING` 状态中也更新 decoder 信息。

---

### 2.6 `MPVLib.setPropertyBoolean("pause", false)` 在 `play()` 中无条件执行

**文件:** `core/player/MpvPlayer.kt:120`

```kotlin
MPVLib.setPropertyBoolean("pause", false)   // ← 在 loadfile 之前设置
MPVLib.command(arrayOf("loadfile", url))
```

**问题:** 在 `loadfile` 之前就设置 `pause=false`，如果媒体加载较慢，MPV 可能在缓冲区还没就绪时就尝试播放。虽然 MPV 内部会处理（自动等待缓冲），但这与标准的 `stop → loadfile → prepare → play` 顺序不一致。

**修复方案:** 将 `pause=false` 移到 `loadfile` 之后：

```kotlin
MPVLib.command(arrayOf("stop"))
MPVLib.command(arrayOf("loadfile", url))
// MPV 自动开始缓冲和播放
```

或者监听 `loadstart`/`ready` 事件再 unpause。

---

## 三、中危 (P2 — 潜在问题/代码质量)

### 3.1 异常被静默吞掉

**文件:** `core/player/PlayerViewModel.kt:171`

```kotlin
fun switchEpgSource(newUrl: String) {
    ...
    try {
        epgRepository.syncEpgFromUrl(newUrl)
        currentPlayingChannel?.let { fetchEpgForChannel(it) }
    } catch (e: Exception) {}  // ← 完全静默
}
```

**问题:** 用户切换 EPG 源时如果失败，没有任何反馈。用户不知道是成功了还是失败了。

**修复方案:** 至少发送 Toast 或通过 `epgSyncEvent` 通知用户。

---

**文件:** `feature/settings/SettingsViewModel.kt:72-74`

```kotlin
viewModelScope.launch {
    try { epgRepository.syncEpgFromUrl(epg) } catch (e: Exception) { e.printStackTrace() }
}
```

**问题:** 嵌套 `viewModelScope.launch` 且 catch 后仅 `printStackTrace()`，用户完全看不到错误。EPG 同步失败时无反馈。

**修复方案:** 将 EPG 同步状态反馈到 UI：

```kotlin
if (epg.isNotBlank()) {
    viewModelScope.launch {
        try {
            epgRepository.syncEpgFromUrl(epg)
        } catch (e: Exception) {
            Log.e(TAG, "EPG 同步失败", e)
            // 可以选择记录但继续，不影响主流程
        }
    }
}
```

---

### 3.2 `NetworkUtil.getLocalIpAddress()` 可能返回非 Wi-Fi 地址

**文件:** `core/utils/NetworkUtil.kt`

**问题:** 遍历所有网络接口，返回第一个非回环、非 IPv6 的地址。在 Android 设备上，这可能返回 **以太网、USB tethering、或虚拟网卡** 的地址。如果电视连接的是 Wi-Fi 但系统枚举顺序中以太网在前，二维码会包含错误的 IP，手机端无法访问。

**修复方案:** 优先过滤 Wi-Fi 接口：

```kotlin
fun getLocalIpAddress(): String {
    try {
        // 优先 Wi-Fi
        val wifi = NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .find { it.name.equals("wlan0", ignoreCase = true) }
        wifi?.inetAddresses?.asSequence()?.firstOrNull {
            !it.isLoopbackAddress && it.hostAddress?.contains(':') == false
        }?.hostAddress?.let { return it }
    } catch (_: Exception) {}
    
    // 降级：返回任意非回环 IPv4
    return getAnyIpv4()
}
```

---

### 3.3 缺少 `ACCESS_WIFI_STATE` 权限

**文件:** `AndroidManifest.xml`

**问题:** `NetworkUtil` 使用 `NetworkInterface.getNetworkInterfaces()` 获取 IP，在 Android 10+ 上需要 `ACCESS_WIFI_STATE` 或 `ACCESS_NETWORK_STATE` 权限。当前清单只声明了 `ACCESS_NETWORK_STATE`，这在大多数设备上够用，但某些 Android TV 设备可能要求 `ACCESS_WIFI_STATE` 才能读取 Wi-Fi IP。

**修复方案:** 添加权限：

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

---

### 3.4 M3U 解析不支持 `.m3u8` 扩展名的 M3U8 格式

**文件:** `core/data/parser/M3uParser.kt`

**问题:** 解析器按行读取并处理 `#EXTINF:` 标记，这在 M3U 和 M3U8 中都有效。但 URL 行如果包含 `#` 注释标记（M3U8 允许行内注释），解析器会跳过该行（因为 `!currentLine.startsWith("#")`），导致 **M3U8 中的带参 URL 被跳过**。

**实际影响:** 大多数 M3U8 直播流的 URL 行不包含 `#`（注释通常在 `#EXT-X-TARGETDURATION` 等标签中），所以实际影响很小。但如果遇到格式不规范的源，可能会有频道丢失。

**结论:** 可接受，M3U 解析逻辑对于 IPTV 场景基本够用。

---

### 3.5 Room 迁移策略过于激进

**文件:** `core/data/di/DatabaseModule.kt:29`

```kotlin
.fallbackToDestructiveMigration()
```

**问题:** 任何数据库版本升级都会 **清空所有数据**。用户更换版本后，所有频道、EPG 历史、设置都会丢失。虽然当前 `version = 2` 且频繁开发中可能不需要保留数据库，但生产环境中这是严重问题。

**修复方案:** 在正式发版前实现 `Migration` 对象：

```kotlin
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 从 v1 升级到 v2 的迁移逻辑
        db.execSQL("ALTER TABLE channels ADD COLUMN tvgId TEXT NOT NULL DEFAULT ''")
        // ... 其他字段迁移
    }
}
```

---

### 3.6 EPG 解析器内存 — 大 XML 文件 OOM 风险

**文件:** `core/data/parser/EpgParser.kt`

**问题:** 7 天 EPG XML 文件可能达到 **50-100MB**。当前代码使用 `Xml.newPullParser()`（XmlPullParser）逐元素解析，**不会将整个 XML 加载到内存**，这是正确的流式解析。`batchSize = 1000` 意味着每次 emit 只持有 1000 个对象。

但是，`channelMap`（`MutableMap<String, String>`）在解析过程中保存 **所有 XML 中的 channel ID**，如果 XML 包含数千个频道，这会持续增长。

**实际风险:** 低。XML Pull Parser 是流式的，`channelMap` 最多保存几千条记录，内存占用在可接受范围内。

---

### 3.7 `SettingsScreen` 中输入框初始值填充逻辑缺陷

**文件:** `feature/settings/SettingsScreen.kt:87-90`

```kotlin
LaunchedEffect(savedIptvUrl, savedEpgUrl) {
    if (inputIptvText.isEmpty() && savedIptvUrl.isNotEmpty()) inputIptvText = savedIptvUrl
    if (inputEpgText.isEmpty() && savedEpgUrl.isNotEmpty()) inputEpgText = savedEpgUrl
}
```

**问题:** `LaunchedEffect` 的键是 `savedIptvUrl, savedEpgUrl`，这意味着 **每当 saved URL 变化时都会重新执行**。如果 `inputIptvText` 已被用户修改（不再为空），则不会覆盖 — 这是正确的。但 `inputIptvText` 初始为空字符串，首次进入时会被填充。如果用户在输入框中输入后，saved 值在 DataStore 中更新（如通过扫码推送），`LaunchedEffect` 不会覆盖用户的输入（因为 `isEmpty()` 检查），这也是正确的。

**结论:** 逻辑正确。

---

## 四、低危 / 代码质量 (P3 — 建议改进)

### 4.1 过于频繁的日志输出

**文件:** `core/player/MpvPlayer.kt`, `core/player/Media3Player.kt`

多处使用 `Log.e()` 作为调试日志（如 `eventProperty` 回调中的探针输出）。`Log.e` 在 Release 构建中通常仍然输出，但语义上是"错误"级别。

**建议:** 改用 `Log.d()` 或 `Log.v()`，真正错误才用 `Log.e()`。

---

### 4.2 `PlayerScreen.kt` 过长（722 行）

**文件:** `core/player/PlayerScreen.kt`

**问题:** 单个 Composable 文件超过 700 行，包含播放器 UI、侧边栏、频道列表、EPG 卡片、高级设置面板等多个独立组件。虽然当前用独立 `@Composable` 函数拆分了子组件，但主 `PlayerScreen` 仍然耦合了大量逻辑。

**建议:** 将 `AdvancedSettingsSidebar`、`ChannelListSidebar`、`EpgBottomCard` 拆分为独立文件。

---

### 4.3 `HashUtil.md5()` 线程安全

**文件:** `core/utils/HashUtil.kt`

```kotlin
MessageDigest.getInstance("MD5")  // ← 每次调用都创建新实例
```

**问题:** `MessageDigest.getInstance()` 是线程安全的（每次创建新实例），所以此处无问题。但如果未来改为复用实例，则会有线程安全问题。

**结论:** 当前实现安全。

---

### 4.4 `SettingsManager` 中的 History 重复追加

**文件:** `core/data/datastore/SettingsManager.kt:56-58`

`saveIptvUrl()` 手动追加 URL 到 history Set，同时 `iptvHistoryFlow` 的 map 也追加当前 URL。虽然 Set 去重，但两次写操作是不必要的。

**建议:** 只保留 `saveIptvUrl()` 中的手动追加逻辑，移除 `iptvHistoryFlow` map 中的自动追加：

```kotlin
val iptvHistoryFlow: Flow<Set<String>> = context.dataStore.data
    .map { it[IPTV_HISTORY] ?: emptySet() }
```

---

### 4.5 缺少 `usesCleartextTraffic` 的风险

**文件:** `AndroidManifest.xml:17`

```xml
android:usesCleartextTraffic="true"
```

**问题:** 允许所有 HTTP 明文流量。虽然 IPTV/EPG 源通常使用 HTTP，但如果未来支持 HTTPS 源，不应无条件允许明文。

**建议:** 移除全局声明，在 `<application>` 中针对特定 `<intent-filter>` 或网络安全性配置（network_security_config.xml）允许 HTTP。

---

### 4.6 `M3uParser.extractAttribute` 正则性能

**文件:** `core/data/parser/M3uParser.kt:79`

```kotlin
val regex = Regex("""$attribute="([^"]*)"""")
```

**问题:** 每次解析一个 M3U 行都创建一个新的 `Regex` 对象。对于数千个频道的文件，这会产生大量临时对象。

**建议:** 在类级别预编译正则：

```kotlin
private val attrRegex = Regex("""(\w+)="([^"]*)"""")
// 或在 extractAttribute 中缓存
```

---

### 4.7 `EpgDao` 缺少索引 — 查询效率

**文件:** `core/data/local/EpgProgram.kt`

EPG 表上有 `channelHash`、`channelName`、`tvgId` 的索引，但查询 `WHERE channelHash = ? AND endTime > ? ORDER BY startTime ASC` 是一个 **复合条件查询**。单独索引 `channelHash` 只能加速第一个条件。

**建议:** 添加复合索引：

```kotlin
indices = [
    Index(value = ["channelHash", "endTime", "startTime"]),  // 覆盖查询
    Index(value = ["channelName"]),
    Index(value = ["tvgId"])
]
```

---

## 五、汇总优先级

| 优先级 | 编号 | 问题 | 影响 |
|--------|------|------|------|
| **P0** | 1.3 | NTP 时间偏移计算错误 | EPG 查询全部失效 |
| **P0** | 1.2 | EPG 真空期 | 用户看不到节目单 |
| **P1** | 2.1 | 时钟 `LaunchedEffect` 无限循环 | 内存泄漏 |
| **P1** | 2.5 | Media3 重连后解码器信息不更新 | Debug 信息不准确 |
| **P1** | 2.6 | MPV pause 时序 | 可能起播延迟 |
| **P2** | 3.1 | 异常被静默吞掉 | 用户无反馈 |
| **P2** | 3.3 | 可能返回非 Wi-Fi IP | 扫码无法访问 |
| **P2** | 3.5 | `fallbackToDestructiveMigration` | 版本升级丢数据 |
| **P3** | 4.4 | History 重复追加 | 轻微性能浪费 |
| **P3** | 4.6 | M3U 正则重复创建 | 轻微 GC 压力 |
| **P3** | 4.7 | EPG 复合索引缺失 | 慢速设备查询慢 |

---

## 六、立即修复清单

### 🔴 最高优先级（必须修）

1. **修复 NTP 时间计算** (`NtpManager.kt`)
   - 将 `elapsedRealtime` 改为 `currentTimeMillis` 记录 NTP 响应时刻
   - 实现为 `ntpTimeMillis` 直接返回值

### 🟠 高优先级（尽快修）

2. **修复 EPG 真空期** (`EpgRepository.kt`)
   - 先收集全部新数据，成功后再替换旧数据

3. **修复时钟协程泄漏** (`PlayerScreen.kt`)
   - 使用 `snapshotFlow` 或 `rememberUpdatedState` 管理 `while(true)` 循环

### 🟡 中优先级（下一版本）

4. **移除 EPG 同步中的异常静默吞掉** (`PlayerViewModel.kt:171`)
5. **改进 `NetworkUtil` 优先返回 Wi-Fi IP** (`NetworkUtil.kt`)
6. **添加 `ACCESS_WIFI_STATE` 权限** (`AndroidManifest.xml`)
7. **MPV pause 时序调整** (`MpvPlayer.kt`)
