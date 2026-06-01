package com.meetingminutes.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetingminutes.app.data.ActionBoardItem
import com.meetingminutes.app.data.CloudSettings
import com.meetingminutes.app.data.DEFAULT_KIMI_BASE_URL
import com.meetingminutes.app.data.DEFAULT_KIMI_MODEL
import com.meetingminutes.app.data.MeetingCard
import com.meetingminutes.app.data.MeetingDetail
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as MeetingMinutesApplication
            val viewModel = remember { MainViewModel(app) }
            MeetingMinutesTheme {
                MeetingMinutesApp(viewModel)
            }
        }
    }
}

private enum class Screen(val title: String) {
    Record("录音"),
    Library("库"),
    Calendar("日历"),
    Insights("工作台"),
    Settings("设置")
}

private data class RecordingLaunch(val title: String, val backgroundSafe: Boolean, val agenda: String)

private data class MeetingTemplate(val name: String, val titlePrefix: String, val agenda: String)

private val meetingTemplates = listOf(
    MeetingTemplate("周会", "周会", "1. 上周完成\n2. 本周目标\n3. 风险阻塞\n4. 需要谁支持"),
    MeetingTemplate("评审", "方案评审", "1. 要评审的方案\n2. 决策标准\n3. 争议点\n4. 最终结论和负责人"),
    MeetingTemplate("复盘", "项目复盘", "1. 目标是否达成\n2. 做得好的地方\n3. 问题根因\n4. 下次怎么改"),
    MeetingTemplate("1对1", "1对1沟通", "1. 当前状态\n2. 卡点和压力\n3. 需要的资源\n4. 下一步行动")
)

private val AppBackground = Color(0xFFF5F7FA)
private val AppSurface = Color(0xFFFFFFFF)
private val AppSurfaceSoft = Color(0xFFEAF2F1)
private val AppTextMuted = Color(0xFF64748B)
private val AppLine = Color(0xFFE2E8F0)
private val AppDanger = Color(0xFFB42318)
private val AppWarning = Color(0xFF9A6700)

@Composable
private fun MeetingMinutesTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF0F766E),
        onPrimary = Color.White,
        secondary = Color(0xFF475569),
        tertiary = Color(0xFFC2410C),
        background = AppBackground,
        surface = AppSurface,
        surfaceVariant = AppSurfaceSoft,
        outline = AppLine,
        error = AppDanger
    )
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingMinutesApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    var screen by remember { mutableStateOf(Screen.Record) }
    val context = LocalContext.current
    var pendingStart by remember { mutableStateOf<RecordingLaunch?>(null) }
    val recordPermissions = MainViewModel.requiredRecordingPermissions()
    val recordingLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val allowed = recordPermissions.all { result[it] == true || MainViewModel.hasPermissions(context, arrayOf(it)) }
        if (allowed) pendingStart?.let { viewModel.startRecording(it.title, it.backgroundSafe, it.agenda) }
        pendingStart = null
    }
    val calendarPermissions = remember {
        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (calendarPermissions.all { result[it] == true || MainViewModel.hasPermissions(context, arrayOf(it)) }) {
            viewModel.syncCalendar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("会议库", fontWeight = FontWeight.Bold)
                        Text(
                            state.message.ifBlank { screen.title },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = AppSurface) {
                Screen.values().forEach { item ->
                    NavigationBarItem(
                        selected = item == screen,
                        onClick = { screen = item },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    Screen.Record -> Icons.Default.Mic
                                    Screen.Library -> Icons.AutoMirrored.Filled.LibraryBooks
                                    Screen.Calendar -> Icons.Default.CalendarMonth
                                    Screen.Insights -> Icons.Default.Insights
                                    Screen.Settings -> Icons.Default.Settings
                                },
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (screen) {
                Screen.Record -> RecordScreen(
                    state = state,
                    onStart = { title, realtime, agenda ->
                        if (MainViewModel.hasPermissions(context, recordPermissions)) {
                            viewModel.startRecording(title, realtime, agenda)
                        } else {
                            pendingStart = RecordingLaunch(title, realtime, agenda)
                            recordingLauncher.launch(recordPermissions)
                        }
                    },
                    onStop = viewModel::stopRecording
                )
                Screen.Library -> LibraryScreen(state, viewModel)
                Screen.Calendar -> CalendarScreen(
                    state = state,
                    viewModel = viewModel,
                    onSync = {
                        if (MainViewModel.hasPermissions(context, calendarPermissions)) {
                            viewModel.syncCalendar()
                        } else {
                            calendarLauncher.launch(calendarPermissions)
                        }
                    }
                )
                Screen.Insights -> WorkbenchScreen(state, viewModel)
                Screen.Settings -> SettingsScreen(
                    settings = state.settings,
                    onSave = viewModel::saveSettings,
                    onCheckUpdate = { viewModel.checkForUpdates() }
                )
            }
        }
    }
    state.updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            title = { Text("发现新版本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(update.versionName, fontWeight = FontWeight.Bold)
                    Text(update.releaseNotes.take(420).ifBlank { "可以下载新的 APK 更新。" })
                    Text(
                        "会打开下载页面。安装时如果手机提示未知来源，按系统提示允许本次安装即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextMuted
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissUpdate()
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
                }) { Text("去更新") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUpdate) { Text("稍后") }
            }
        )
    }
}

@Composable
private fun RecordScreen(
    state: AppUiState,
    onStart: (String, Boolean, String) -> Unit,
    onStop: () -> Unit
) {
    var title by remember { mutableStateOf("会议 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())}") }
    var agenda by remember { mutableStateOf("") }
    var backgroundSafe by remember { mutableStateOf(true) }
    var confirmStop by remember { mutableStateOf(false) }
    val isRecording = state.recording.isRecording

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DashboardStrip(
                meetings = state.meetings.size,
                pendingActions = state.actionBoard.count { !it.action.done },
                aiMode = aiModeLabel(state.settings)
            )
        }
        item {
            Panel(
                title = "新会议",
                subtitle = if (isRecording) "录音进行中" else "选择模板或直接命名",
                trailing = { StatusPill(if (backgroundSafe) "后台保护" else "前台录音") }
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("会议标题") },
                    singleLine = true,
                    enabled = !isRecording
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    meetingTemplates.take(2).forEach { template ->
                        AssistChip(
                            onClick = {
                                title = "${template.titlePrefix} ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())}"
                                agenda = template.agenda
                            },
                            label = { Text(template.name) },
                            enabled = !isRecording
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    meetingTemplates.drop(2).forEach { template ->
                        AssistChip(
                            onClick = {
                                title = "${template.titlePrefix} ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())}"
                                agenda = template.agenda
                            },
                            label = { Text(template.name) },
                            enabled = !isRecording
                        )
                    }
                }
                OutlinedTextField(
                    value = agenda,
                    onValueChange = { agenda = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("会前目标/议程") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isRecording
                )
                SwitchRow(
                    title = "后台持续录音",
                    subtitle = "锁屏、切应用后继续录",
                    checked = backgroundSafe,
                    enabled = !isRecording,
                    onCheckedChange = { backgroundSafe = it }
                )
            }
        }
        item {
            RecorderPanel(
                state = state,
                onClick = { if (isRecording) confirmStop = true else onStart(title, backgroundSafe, agenda) }
            )
        }
        item {
            Panel(title = "现场转写", subtitle = if (isRecording) "结束后生成文档" else "本地离线识别") {
                Text(
                    state.recording.liveTranscript.ifBlank { "录音结束后会在 APP 内转文字、识别角色、生成会议文档。" },
                    color = if (state.recording.liveTranscript.isBlank()) AppTextMuted else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("停止录音？") },
            text = { Text("停止后会开始转写，并在 APP 内生成会议文档。") },
            confirmButton = {
                Button(onClick = {
                    confirmStop = false
                    onStop()
                }) { Text("停止并整理") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("继续录音") }
            }
        )
    }
}

@Composable
private fun DashboardStrip(meetings: Int, pendingActions: Int, aiMode: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("会议", meetings.toString(), Modifier.weight(1f))
        StatTile("待办", pendingActions.toString(), Modifier.weight(1f))
        StatTile("AI", aiMode, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppLine)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppTextMuted)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun RecorderPanel(state: AppUiState, onClick: () -> Unit) {
    val isRecording = state.recording.isRecording
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isRecording) Color(0xFFFFF1F0) else AppSurface,
        border = BorderStroke(1.dp, if (isRecording) Color(0xFFFECACA) else AppLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    FilledIconButton(
                        modifier = Modifier.size(94.dp),
                        onClick = onClick
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "停止录音" else "开始录音",
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatDuration(state.recording.durationMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(if (isRecording) "正在录音" else "准备开始", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(state.recording.message.ifBlank { "点击麦克风开始" }, style = MaterialTheme.typography.bodySmall, color = AppTextMuted)
                }
            }
            LevelBar(state.recording.level)
        }
    }
}

@Composable
private fun LibraryScreen(state: AppUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("搜索会议") },
            leadingIcon = { Icon(Icons.Default.Search, "搜索会议") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("全部", state.meetings.size.toString(), Modifier.weight(1f))
            StatTile("已完成", state.meetings.count { it.status == "completed" }.toString(), Modifier.weight(1f))
            StatTile("可重试", state.meetings.count { it.status == "needs_retry" }.toString(), Modifier.weight(1f))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.meetings, key = { it.id }) { meeting ->
                MeetingRow(meeting, selected = state.selectedMeeting?.meeting?.id == meeting.id) {
                    viewModel.selectMeeting(meeting.id)
                }
            }
        }
    }
    state.selectedMeeting?.let { detail ->
        DetailDialog(
            detail = detail,
            busy = state.busy,
            aiMode = aiModeLabel(state.settings),
            viewModel = viewModel,
            onDismiss = { viewModel.selectMeeting(0) }
        )
    }
}

@Composable
private fun CalendarScreen(state: AppUiState, viewModel: MainViewModel, onSync: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WeekStrip(selectedDay = state.selectedDay, onSelect = viewModel::selectDay)
        SectionHeader("当天会议", "${state.selectedDayMeetings.size} 条")
        if (state.selectedDayMeetings.isEmpty()) {
            EmptyState("这天还没有会议")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.selectedDayMeetings, key = { it.id }) { meeting ->
                    MeetingRow(meeting, selected = false) { viewModel.selectMeeting(meeting.id) }
                }
            }
        }
    }
    state.selectedMeeting?.let { detail ->
        DetailDialog(
            detail = detail,
            busy = state.busy,
            aiMode = aiModeLabel(state.settings),
            viewModel = viewModel,
            onDismiss = { viewModel.selectMeeting(0) },
            onSync = onSync
        )
    }
}

@Composable
private fun WorkbenchScreen(state: AppUiState, viewModel: MainViewModel) {
    val pendingActions = state.actionBoard.filterNot { it.action.done }
    val completedActions = state.actionBoard.filter { it.action.done }.take(4)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("待办", pendingActions.size.toString(), Modifier.weight(1f))
                StatTile("已完成", state.actionBoard.count { it.action.done }.toString(), Modifier.weight(1f))
                StatTile("洞察", state.insights.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Panel(title = "行动项", subtitle = "跨会议待办") {
                if (pendingActions.isEmpty()) {
                    EmptyState("暂无未完成待办")
                } else {
                    pendingActions.take(12).forEach { item ->
                        ActionBoardRow(item = item, onCheckedChange = { checked ->
                            viewModel.toggleActionDone(item.action.id, checked)
                        })
                        HorizontalDivider(color = AppLine)
                    }
                }
                if (completedActions.isNotEmpty()) {
                    SectionHeader("最近完成", "")
                    completedActions.forEach { item ->
                        ActionBoardRow(item = item, onCheckedChange = { checked ->
                            viewModel.toggleActionDone(item.action.id, checked)
                        })
                    }
                }
            }
        }
        item {
            Panel(title = "会议洞察", subtitle = "自动归纳") {
                if (state.insights.isEmpty()) {
                    EmptyState("暂无洞察")
                } else {
                    state.insights.take(3).forEach { insight ->
                        InsightRow(title = insight.title, content = insight.content)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBoardRow(item: ActionBoardItem, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = item.action.done,
            onCheckedChange = onCheckedChange
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(item.action.content, fontWeight = if (item.action.done) FontWeight.Normal else FontWeight.Medium)
            Text(
                "${item.action.owner} · ${item.meetingTitle} · ${formatDate(item.meetingStartedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = AppTextMuted
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: CloudSettings,
    onSave: (CloudSettings) -> Unit,
    onCheckUpdate: () -> Unit
) {
    var keepAudio by remember(settings) { mutableStateOf(settings.keepAudioAfterSuccess) }
    var aiEnabled by remember(settings) { mutableStateOf(settings.aiPolishEnabled) }
    var aiBaseUrl by remember(settings) { mutableStateOf(settings.aiBaseUrl) }
    var aiApiKey by remember(settings) { mutableStateOf(settings.aiApiKey) }
    var aiModel by remember(settings) { mutableStateOf(settings.aiModel) }
    var updateEnabled by remember(settings) { mutableStateOf(settings.updateChecksEnabled) }
    var updateApiUrl by remember(settings) { mutableStateOf(settings.updateApiUrl) }
    fun currentSettings() = CloudSettings(
        transcriptionEngine = settings.transcriptionEngine,
        summaryEngine = settings.summaryEngine,
        keepAudioAfterSuccess = keepAudio,
        aiPolishEnabled = aiEnabled,
        aiBaseUrl = aiBaseUrl,
        aiApiKey = aiApiKey,
        aiModel = aiModel,
        updateChecksEnabled = updateEnabled,
        updateApiUrl = updateApiUrl
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Panel(title = "本地模式", subtitle = "免费使用") {
                SettingsLine("转写", settings.transcriptionEngine)
                SettingsLine("纪要", settings.summaryEngine)
                SwitchRow(
                    title = "识别成功后保留音频",
                    subtitle = "关闭时自动清理原始录音",
                    checked = keepAudio,
                    onCheckedChange = { keepAudio = it }
                )
            }
        }
        item {
            Panel(title = "Kimi 后台整理", subtitle = aiModeLabel(currentSettings())) {
                SwitchRow(
                    title = "启用 Kimi API",
                    subtitle = "详情页会在 APP 内直接生成文档",
                    checked = aiEnabled,
                    onCheckedChange = { aiEnabled = it }
                )
                if (aiEnabled) {
                    OutlinedTextField(
                        value = aiBaseUrl,
                        onValueChange = { aiBaseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("接口 Base URL") },
                        placeholder = { Text(DEFAULT_KIMI_BASE_URL) },
                        singleLine = true
                    )
                    SecretField(
                        value = aiApiKey,
                        onValueChange = { aiApiKey = it },
                        label = "Kimi API Key"
                    )
                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = { aiModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型名称") },
                        placeholder = { Text(DEFAULT_KIMI_MODEL) },
                        singleLine = true
                    )
                    Text(
                        "Key 不会写进 APK，只保存在手机本地加密设置里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextMuted
                    )
                }
            }
        }
        item {
            Panel(title = "应用更新", subtitle = "GitHub Releases") {
                SwitchRow(
                    title = "启动时检查新版本",
                    subtitle = "发现新 APK 时弹窗提示",
                    checked = updateEnabled,
                    onCheckedChange = { updateEnabled = it }
                )
                OutlinedTextField(
                    value = updateApiUrl,
                    onValueChange = { updateApiUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("更新地址") },
                    minLines = 2,
                    maxLines = 3
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSave(currentSettings())
                        onCheckUpdate()
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "检查更新")
                    Spacer(Modifier.width(8.dp))
                    Text("保存并检查更新")
                }
            }
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSave(currentSettings()) }
            ) {
                Text("保存设置")
            }
        }
    }
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation()
    )
}

@Composable
private fun DetailDialog(
    detail: MeetingDetail,
    busy: Boolean,
    aiMode: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSync: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var confirmDelete by remember(detail.meeting.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(detail.meeting.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(statusLabel(detail.meeting.status))
                    StatusPill(formatDate(detail.meeting.startedAt))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = { viewModel.generateAiDocument(detail.meeting.id) }
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI生成文档")
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "正在生成" else "AI生成文档")
                }
                Text("当前：$aiMode", style = MaterialTheme.typography.bodySmall, color = AppTextMuted)

                detail.summary?.let { summary ->
                    DetailSection("会议文档") {
                        Text(summary.summary)
                        Text("关键决策", fontWeight = FontWeight.SemiBold)
                        Text(summary.decisions.ifBlank { "暂无" }, color = if (summary.decisions.isBlank()) AppTextMuted else MaterialTheme.colorScheme.onSurface)
                        Text("风险点", fontWeight = FontWeight.SemiBold)
                        Text(summary.risks.ifBlank { "暂无" }, color = if (summary.risks.isBlank()) AppTextMuted else MaterialTheme.colorScheme.onSurface)
                        Text("后续问题", fontWeight = FontWeight.SemiBold)
                        Text(summary.openQuestions.ifBlank { "暂无" }, color = if (summary.openQuestions.isBlank()) AppTextMuted else MaterialTheme.colorScheme.onSurface)
                    }
                } ?: DetailSection("会议文档") {
                    Text("还没有文档，点击上方按钮生成。", color = AppTextMuted)
                }

                DetailSection("待办") {
                    if (detail.actions.isEmpty()) {
                        Text("暂无待办", color = AppTextMuted)
                    } else {
                        detail.actions.forEach { action ->
                            Row(verticalAlignment = Alignment.Top) {
                                Checkbox(
                                    checked = action.done,
                                    onCheckedChange = { checked -> viewModel.toggleActionDone(action.id, checked) }
                                )
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(top = 10.dp)
                                ) {
                                    Text(action.content)
                                    Text(action.owner, style = MaterialTheme.typography.bodySmall, color = AppTextMuted)
                                }
                            }
                        }
                    }
                }

                DetailSection("原始转写") {
                    if (detail.transcript.isEmpty()) {
                        Text("暂无转写", color = AppTextMuted)
                    } else {
                        detail.transcript.forEach { line ->
                            Text("${line.speaker}：${line.text}")
                        }
                    }
                }

                DetailSection("导出与同步") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionChipButton(
                            text = "复制纪要",
                            icon = Icons.Default.ContentCopy,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.copyMarkdown(context, detail) }
                        )
                        ActionChipButton(
                            text = "复制待办",
                            icon = Icons.Default.Checklist,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.copyActions(context, detail) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionChipButton(
                            text = "Markdown",
                            icon = Icons.AutoMirrored.Filled.Article,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.exportMarkdown(context, detail) }
                        )
                        ActionChipButton(
                            text = "PDF",
                            icon = Icons.Default.PictureAsPdf,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.exportPdf(context, detail) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionChipButton(
                            text = "重新生成",
                            icon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.generateAiDocument(detail.meeting.id) }
                        )
                        if (detail.calendarEventId == null) {
                            ActionChipButton(
                                text = "同步日历",
                                icon = Icons.Default.CloudDone,
                                modifier = Modifier.weight(1f),
                                onClick = { onSync?.invoke() ?: viewModel.syncCalendar() }
                            )
                        } else {
                            ActionChipButton(
                                text = "取消同步",
                                icon = Icons.Default.EventBusy,
                                modifier = Modifier.weight(1f),
                                onClick = viewModel::unsyncCalendar
                            )
                        }
                    }
                }

                HorizontalDivider(color = AppLine)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppDanger),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    onClick = { confirmDelete = true }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除会议")
                    Spacer(Modifier.width(8.dp))
                    Text("删除会议")
                }
            }
        }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除会议？") },
            text = { Text("会删除这条会议、转写、纪要、待办和导出的缓存文件。这个操作不能撤销。") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = AppDanger),
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteMeeting(detail.meeting.id)
                        onDismiss()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ActionChipButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, maxLines = 1)
    }
}

@Composable
private fun MeetingRow(meeting: MeetingCard, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) AppSurfaceSoft else AppSurface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else AppLine),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(meeting.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDate(meeting.startedAt), style = MaterialTheme.typography.bodySmall, color = AppTextMuted)
                }
                StatusPill(statusLabel(meeting.status))
            }
            if (meeting.tags.isNotBlank()) {
                Text(meeting.tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WeekStrip(selectedDay: Long, onSelect: (Long) -> Unit) {
    val days = remember(selectedDay) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDay }
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        List(7) {
            val day = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            day
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            val selected = sameDay(day, selectedDay)
            Surface(
                onClick = { onSelect(day) },
                color = if (selected) MaterialTheme.colorScheme.primary else AppSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else AppLine),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        SimpleDateFormat("E", Locale.CHINA).format(Date(day)),
                        color = if (selected) Color.White else AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        SimpleDateFormat("d", Locale.CHINA).format(Date(day)),
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun Panel(
    title: String,
    subtitle: String = "",
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTextMuted)
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, "")
        content()
    }
}

@Composable
private fun SectionHeader(title: String, meta: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (meta.isNotBlank()) {
            Spacer(Modifier.weight(1f))
            Text(meta, style = MaterialTheme.typography.bodySmall, color = AppTextMuted)
        }
    }
}

@Composable
private fun SettingsLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppTextMuted)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTextMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppSurfaceSoft,
        border = BorderStroke(1.dp, Color(0xFFD6E4E2))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, AppLine)
    ) {
        Text(
            text,
            modifier = Modifier.padding(18.dp),
            color = AppTextMuted
        )
    }
}

@Composable
private fun InsightRow(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold)
        }
        Text(content, style = MaterialTheme.typography.bodySmall, color = AppTextMuted)
    }
}

@Composable
private fun LevelBar(level: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE2E8F0))
    ) {
        Box(
            Modifier
                .fillMaxWidth(level.coerceIn(0.03f, 1f))
                .height(8.dp)
                .background(if (level > 0.7f) AppWarning else MaterialTheme.colorScheme.primary)
        )
    }
}

private fun aiModeLabel(settings: CloudSettings): String {
    return if (settings.canUseExternalAi) "Kimi" else "本地"
}

private fun statusLabel(status: String): String {
    return when (status) {
        "completed" -> "已归档"
        "recording" -> "录音中"
        "processing" -> "处理中"
        "needs_retry" -> "可重试"
        else -> status.ifBlank { "未知" }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatDate(ms: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
