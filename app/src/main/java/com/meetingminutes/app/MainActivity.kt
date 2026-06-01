package com.meetingminutes.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.meetingminutes.app.data.CloudSettings
import com.meetingminutes.app.data.ActionBoardItem
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

@Composable
private fun MeetingMinutesTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF1F6B5B),
        secondary = Color(0xFF9B6A2F),
        tertiary = Color(0xFFE0B84D),
        background = Color(0xFFF7F3EA),
        surface = Color(0xFFFFFCF5),
        surfaceVariant = Color(0xFFE7EFEA)
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
                        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.values().forEach { item ->
                    NavigationBarItem(
                        selected = item == screen,
                        onClick = { screen = item },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    Screen.Record -> Icons.Default.Mic
                                    Screen.Library -> Icons.Default.LibraryBooks
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
                        color = MaterialTheme.colorScheme.secondary
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("会议标题") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        CardBlock(title = "会议模板") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meetingTemplates.take(2).forEach { template ->
                    AssistChip(
                        onClick = {
                            title = "${template.titlePrefix} ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())}"
                            agenda = template.agenda
                        },
                        label = { Text(template.name) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meetingTemplates.drop(2).forEach { template ->
                    AssistChip(
                        onClick = {
                            title = "${template.titlePrefix} ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())}"
                            agenda = template.agenda
                        },
                        label = { Text(template.name) }
                    )
                }
            }
            OutlinedTextField(
                value = agenda,
                onValueChange = { agenda = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("会前目标/议程") },
                minLines = 3,
                maxLines = 5
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("后台持续录音", fontWeight = FontWeight.Medium)
                Text("锁屏、切应用后继续录，停止前会二次确认", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = backgroundSafe, onCheckedChange = { backgroundSafe = it }, enabled = !state.recording.isRecording)
        }
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(172.dp)
                .clip(CircleShape)
                .background(if (state.recording.isRecording) Color(0xFFE26D5C) else MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            FilledIconButton(
                modifier = Modifier.size(124.dp),
                onClick = { if (state.recording.isRecording) confirmStop = true else onStart(title, backgroundSafe, agenda) }
            ) {
                Icon(
                    imageVector = if (state.recording.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (state.recording.isRecording) "停止录音" else "开始录音",
                    modifier = Modifier.size(52.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(formatDuration(state.recording.durationMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(state.recording.message, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
        LevelBar(state.recording.level)
        Spacer(Modifier.height(18.dp))
        CardBlock(title = "现场转写") {
            Text(state.recording.liveTranscript.ifBlank { "免费版会在停止录音后用手机本地模型转写并生成纪要。录音过程中可以锁屏或切到其他应用。" })
        }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("停止录音？") },
            text = { Text("停止后会在手机本地转写，并自动生成会议纪要。") },
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
private fun LibraryScreen(state: AppUiState, viewModel: MainViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索会议") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.meetings, key = { it.id }) { meeting ->
                    MeetingRow(meeting, selected = state.selectedMeeting?.meeting?.id == meeting.id) {
                        viewModel.selectMeeting(meeting.id)
                    }
                }
            }
        }
    }
    state.selectedMeeting?.let { detail ->
        DetailDialog(detail = detail, viewModel = viewModel, onDismiss = { viewModel.selectMeeting(0) })
    }
}

@Composable
private fun CalendarScreen(state: AppUiState, viewModel: MainViewModel, onSync: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        WeekStrip(selectedDay = state.selectedDay, onSelect = viewModel::selectDay)
        Spacer(Modifier.height(14.dp))
        Text("当天会议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.selectedDayMeetings, key = { it.id }) { meeting ->
                MeetingRow(meeting, selected = false) { viewModel.selectMeeting(meeting.id) }
            }
        }
    }
    state.selectedMeeting?.let { detail ->
        DetailDialog(detail = detail, viewModel = viewModel, onDismiss = { viewModel.selectMeeting(0) }, onSync = onSync)
    }
}

@Composable
private fun WorkbenchScreen(state: AppUiState, viewModel: MainViewModel) {
    val pendingActions = state.actionBoard.filterNot { it.action.done }
    val completedActions = state.actionBoard.filter { it.action.done }.take(4)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardBlock(title = "待办工作台") {
                if (pendingActions.isEmpty()) {
                    Text("暂无未完成待办")
                } else {
                    pendingActions.take(12).forEach { item ->
                        ActionBoardRow(item = item, onCheckedChange = { checked ->
                            viewModel.toggleActionDone(item.action.id, checked)
                        })
                    }
                }
                if (completedActions.isNotEmpty()) {
                    Text("已完成", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    completedActions.forEach { item ->
                        ActionBoardRow(item = item, onCheckedChange = { checked ->
                            viewModel.toggleActionDone(item.action.id, checked)
                        })
                    }
                }
            }
        }
        item {
            CardBlock(title = "会议洞察") {
                if (state.insights.isEmpty()) {
                    Text("暂无洞察")
                } else {
                    state.insights.take(3).forEach { insight ->
                        Text(insight.title, fontWeight = FontWeight.Bold)
                        Text(insight.content, style = MaterialTheme.typography.bodySmall)
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
                .padding(top = 10.dp)
        ) {
            Text(item.action.content, fontWeight = if (item.action.done) FontWeight.Normal else FontWeight.Medium)
            Text(
                "${item.action.owner} · ${item.meetingTitle} · ${formatDate(item.meetingStartedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardBlock(title = "免费本地模式") {
            Text("转写：${settings.transcriptionEngine}")
            Text("纪要：${settings.summaryEngine}")
            Text(
                "不需要 OpenAI、不需要充值。录音结束后会在手机本地转写，纪要由本地规则整理。第一次识别会解压离线模型，可能稍慢。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("识别成功后保留音频", fontWeight = FontWeight.Medium)
                    Text("关闭时会自动删除原始录音，节省空间", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = keepAudio, onCheckedChange = { keepAudio = it })
            }
        }
        CardBlock(title = "可选大模型整理") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("转写后调用外部模型", fontWeight = FontWeight.Medium)
                    Text("默认关闭。开启后只把文字发给你填写的模型接口，用来整理成更漂亮的会议文档。", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = aiEnabled, onCheckedChange = { aiEnabled = it })
            }
            if (aiEnabled) {
                OutlinedTextField(
                    value = aiBaseUrl,
                    onValueChange = { aiBaseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("接口 Base URL") },
                    placeholder = { Text("例如：https://你的模型平台/v1") },
                    singleLine = true
                )
                SecretField(
                    value = aiApiKey,
                    onValueChange = { aiApiKey = it },
                    label = "API Key（有的平台可不填）"
                )
                OutlinedTextField(
                    value = aiModel,
                    onValueChange = { aiModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    placeholder = { Text("填写免费模型的模型 ID") },
                    singleLine = true
                )
                Text(
                    "不填完整时会继续使用本地规则纪要。外部接口调用失败也不会影响会议保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        CardBlock(title = "应用更新") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启动时检查 GitHub 新版本", fontWeight = FontWeight.Medium)
                    Text("有新 APK 时会弹窗提示，不会自动强制安装。", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = updateEnabled, onCheckedChange = { updateEnabled = it })
            }
            OutlinedTextField(
                value = updateApiUrl,
                onValueChange = { updateApiUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub 更新地址") },
                placeholder = { Text("GitHub Releases latest API 地址") },
                minLines = 2,
                maxLines = 3
            )
            Text(
                "默认指向 masha19890829-netizen/meeting-minutes-android 的 GitHub Releases。以后发布新版时，Release 说明里写 versionCode: 更大的数字，并上传 APK。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSave(currentSettings())
                    onCheckUpdate()
                }
            ) {
                Text("保存并检查更新")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(currentSettings())
            }
        ) {
            Text("保存设置")
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
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSync: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var confirmDelete by remember(detail.meeting.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text(detail.meeting.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(detail.meeting.status) })
                    AssistChip(onClick = {}, label = { Text(formatDate(detail.meeting.startedAt)) })
                }
                detail.summary?.let {
                    Text("摘要", fontWeight = FontWeight.Bold)
                    Text(it.summary)
                    Text("关键决策", fontWeight = FontWeight.Bold)
                    Text(it.decisions.ifBlank { "暂无" })
                }
                Text("待办", fontWeight = FontWeight.Bold)
                if (detail.actions.isEmpty()) {
                    Text("暂无")
                } else {
                    detail.actions.forEach { action ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = action.done,
                                onCheckedChange = { checked -> viewModel.toggleActionDone(action.id, checked) }
                            )
                            Text("${action.owner}：${action.content}", modifier = Modifier.weight(1f))
                        }
                    }
                }
                Text("原始转写", fontWeight = FontWeight.Bold)
                detail.transcript.forEach { Text("${it.speaker}：${it.text}") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { viewModel.copyMarkdown(context, detail) }) {
                        Icon(Icons.Default.ContentCopy, "复制纪要")
                    }
                    IconButton(onClick = { viewModel.copyActions(context, detail) }) {
                        Icon(Icons.Default.Description, "复制待办")
                    }
                    IconButton(onClick = { viewModel.exportMarkdown(context, detail) }) {
                        Icon(Icons.Default.Article, "分享 Markdown")
                    }
                    IconButton(onClick = { viewModel.exportPdf(context, detail) }) {
                        Icon(Icons.Default.PictureAsPdf, "分享 PDF")
                    }
                    IconButton(onClick = { viewModel.regenerateSummary(detail.meeting.id) }) {
                        Icon(Icons.Default.Refresh, "重新总结")
                    }
                    if (detail.calendarEventId == null) {
                        IconButton(onClick = { onSync?.invoke() ?: viewModel.syncCalendar() }) {
                            Icon(Icons.Default.CloudSync, "同步日历")
                        }
                    } else {
                        IconButton(onClick = viewModel::unsyncCalendar) {
                            Icon(Icons.Default.EventBusy, "取消日历同步")
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "删除会议文档")
                    }
                }
            }
        }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除会议文档？") },
            text = { Text("会删除这条会议、转写、纪要、待办和导出的缓存文件。这个操作不能撤销。") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    viewModel.deleteMeeting(detail.meeting.id)
                    onDismiss()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MeetingRow(meeting: MeetingCard, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE7EFEA) else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(meeting.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${formatDate(meeting.startedAt)} · ${meeting.status}", style = MaterialTheme.typography.bodySmall)
            if (meeting.tags.isNotBlank()) Text(meeting.tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            val selected = sameDay(day, selectedDay)
            Card(
                onClick = { onSelect(day) },
                colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(SimpleDateFormat("E", Locale.CHINA).format(Date(day)), color = if (selected) Color.White else Color.Unspecified)
                    Text(SimpleDateFormat("d", Locale.CHINA).format(Date(day)), fontWeight = FontWeight.Bold, color = if (selected) Color.White else Color.Unspecified)
                }
            }
        }
    }
}

@Composable
private fun CardBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LevelBar(level: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE1DDD2))
    ) {
        Box(
            Modifier
                .fillMaxWidth(level.coerceIn(0.03f, 1f))
                .height(10.dp)
                .background(MaterialTheme.colorScheme.tertiary)
        )
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
