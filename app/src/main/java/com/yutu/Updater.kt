package com.yutu

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Update *notice* only (personal build, 2026-11-14).
 *
 * Once a day the newest GitHub release is looked up and, when it is newer than this build, Home
 * shows an Update pill and a notification says a new version is out. Tapping either opens the
 * release page in the browser so the notes can be read. **Nothing is ever downloaded or
 * installed by the app**: there is no APK download, no PackageInstaller, and the manifest no
 * longer asks for REQUEST_INSTALL_PACKAGES / UPDATE_PACKAGES_WITHOUT_USER_ACTION. Updating means
 * building this fork again and installing it by hand.
 *
 * Logs "update check: …".
 */
object AppUpdate {
    private const val TAG = "YUTU"
    /** Newest first; enough to cover the notes of every release a phone may have skipped.
     *  The repository is GITHUB_RELEASES_REPO in app/build.gradle.kts (upstream Yuno by default). */
    private val RELEASES_URL
        get() = "https://api.github.com/repos/${BuildConfig.GITHUB_RELEASES_REPO}/releases?per_page=30"
    private const val CHECK_EVERY_MS = 24 * 60 * 60 * 1000L
    /** A check that failed (offline, rate limit) tries again after this, not a day later. */
    private const val RETRY_AFTER_MS = 60 * 60 * 1000L
    private const val KEY_NEXT_CHECK = "next_check"
    private const val KEY_RELEASE = "release"
    private const val KEY_NOTIFIED = "notified"
    /**
     * The version that made the last check. A different one (just installed by hand) checks at
     * once, instead of waiting for the daily timer.
     */
    private const val KEY_CHECKED_BY = "checked_by"
    private const val CHANNEL = "updates"

    data class Release(
        val version: String,
        /** The release's page on GitHub: where the browser goes to read the notes. */
        val pageUrl: String,
        /** Newest first: this release's notes and those of the releases between it and this build. */
        val notes: List<ReleaseNotes> = emptyList(),
    )

    /** One release's notes as published (markdown); [sections] is what the sheet shows. */
    data class ReleaseNotes(val version: String, val body: String) {
        val sections by lazy { parseNotes(body) }
    }

    /** "New" / "Fixed" / … (null before any heading) and its bullet points. */
    data class NoteSection(val title: String?, val items: List<String>)

    /** The newer release, or null when this build is the newest (then nothing is shown). */
    var available by mutableStateOf<Release?>(null)
        private set
    /** A check is on its way (Settings › About shows it). */
    var checking by mutableStateOf(false)
        private set
    /** The last check couldn't reach GitHub; Settings' tile offers a retry. */
    var failed by mutableStateOf(false)
        private set
    /** The button pops in only the first time it shows in this process. */
    internal var announced = false

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = MainScope()

    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)
        // Shown straight away from the last check; the daily check corrects it.
        available = savedRelease()?.takeIf { isNewer(it.version) }
    }

    /** Called whenever a window comes to the front; looks GitHub up at most once a day, and on a new version's first start. */
    fun checkIfDue() = check(force = false)

    /** Settings › About's tap: looks GitHub up now, whenever the last check was. */
    fun checkNow() = check(force = true)

    /** The pill's and the sheet's action: hands the release page to the browser. */
    fun openPage(context: Context) {
        val release = available ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun check(force: Boolean) {
        val newInstall = prefs.getString(KEY_CHECKED_BY, null) != BuildConfig.VERSION_NAME
        if (checking || (!force && !newInstall && System.currentTimeMillis() < prefs.getLong(KEY_NEXT_CHECK, 0L))) return
        checking = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
            checking = false
            val now = System.currentTimeMillis()
            result.onFailure {
                failed = true
                Log.w(TAG, "update check failed (${it.message}); trying again in an hour")
                prefs.edit().putLong(KEY_NEXT_CHECK, now + RETRY_AFTER_MS).putString(KEY_CHECKED_BY, BuildConfig.VERSION_NAME).apply()
                return@launch
            }
            failed = false
            val latest = result.getOrNull()
            prefs.edit().putLong(KEY_NEXT_CHECK, now + CHECK_EVERY_MS).putString(KEY_CHECKED_BY, BuildConfig.VERSION_NAME)
                .apply { if (latest != null) putString(KEY_RELEASE, latest.toJson()) else remove(KEY_RELEASE) }
                .apply()
            val newer = latest?.takeIf { isNewer(it.version) }
            Log.i(TAG, "update check: latest ${latest?.version ?: "none"}, this ${BuildConfig.VERSION_NAME}" +
                if (newer != null) " → newer version available (notice only, nothing is downloaded)" else "")
            if (newer != available) available = newer
            if (newer != null) notify(newer)
        }
    }

    /** One notification per version: "a new version is out", tap = the release page in the browser. */
    private fun notify(release: Release) {
        if (prefs.getString(KEY_NOTIFIED, null) == release.version) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, str(R.string.app_update_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val tap = PendingIntent.getActivity(
                app, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(str(R.string.app_update_available_title, release.version))
                .setContentText(str(R.string.app_update_available_text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            NotificationManagerCompat.from(app).notify(2, notification)
            prefs.edit().putString(KEY_NOTIFIED, release.version).apply()
        }.onFailure { Log.w(TAG, "update notice: couldn't post the notification (${it.message})") }
    }

    private fun fetchLatest(): Release? {
        val request = Request.Builder().url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Yutu/${BuildConfig.VERSION_NAME}")
            .build()
        Net.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return parseReleases(JSONArray(response.body?.string().orEmpty()), BuildConfig.VERSION_NAME)
        }
    }

    /**
     * The releases list → the newest published release with an APK asset, carrying the notes of every
     * release newer than [current] up to it (a phone that skipped versions sees all of their changes).
     */
    internal fun parseReleases(json: JSONArray, current: String): Release? {
        val published = (0 until json.length()).map { json.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
        val latest = published.mapNotNull { parseRelease(it) }.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
            ?: return null
        val notes = published
            .map { ReleaseNotes(it.optString("tag_name").removePrefix("v"), it.optString("body")) }
            .filter { compareVersions(it.version, current) > 0 && compareVersions(it.version, latest.version) <= 0 }
            .filter { it.sections.isNotEmpty() }
            .sortedWith { a, b -> compareVersions(b.version, a.version) }
        return latest.copy(notes = notes)
    }

    /** The latest-release JSON → its page, or null when it has no APK asset to point at. */
    internal fun parseRelease(json: JSONObject): Release? {
        val version = json.optString("tag_name").removePrefix("v").ifBlank { return null }
        val assets = json.optJSONArray("assets") ?: return null
        val hasApk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .any { it.optString("name").endsWith(".apk", ignoreCase = true) }
        if (!hasApk) return null
        val page = json.optString("html_url").ifBlank { "https://github.com/${BuildConfig.GITHUB_RELEASES_REPO}/releases" }
        return Release(
            version = version,
            pageUrl = page,
            notes = listOf(ReleaseNotes(version, json.optString("body"))).filter { it.sections.isNotEmpty() },
        )
    }

    /**
     * A release body → its sections: "### New" headings and "- " bullets (a bullet's indented next lines
     * join it). Everything after a "---" line is for the web page (install help), and so is any loose
     * paragraph; bold, code and links keep only their text.
     */
    internal fun parseNotes(body: String): List<NoteSection> {
        val sections = mutableListOf<NoteSection>()
        var title: String? = null
        val items = mutableListOf<String>()
        fun flush() {
            if (items.isNotEmpty()) sections += NoteSection(title, items.toList())
            items.clear()
        }
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (line == "---") break
            when {
                line.startsWith("#") -> { flush(); title = plain(line.trimStart('#').trim()).ifBlank { null } }
                line.startsWith("- ") || line.startsWith("* ") -> items += plain(line.substring(2).trim())
                line.isNotEmpty() && items.isNotEmpty() && raw.startsWith("  ") -> items[items.lastIndex] += " " + plain(line)
            }
        }
        flush()
        return sections
    }

    private fun plain(markdown: String) = markdown
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""`([^`]+)`"""), "$1")

    /** "0.5.55" vs "0.5.54" → positive; missing parts count as 0. */
    internal fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun isNewer(version: String) = compareVersions(version, BuildConfig.VERSION_NAME) > 0

    private fun savedRelease(): Release? = prefs.getString(KEY_RELEASE, null)?.let {
        runCatching {
            val o = JSONObject(it)
            val notes = o.optJSONArray("notes") ?: JSONArray()
            Release(
                o.getString("version"),
                o.getString("url"),
                (0 until notes.length()).map { notes.getJSONObject(it).let { n -> ReleaseNotes(n.getString("version"), n.getString("body")) } },
            )
        }.getOrNull()
    }

    private fun Release.toJson() =
        JSONObject().put("version", version).put("url", pageUrl)
            .put("notes", JSONArray(notes.map { JSONObject().put("version", it.version).put("body", it.body) }))
            .toString()
}

/**
 * The header's Update pill, beside the profile picture; absent when there's nothing newer. A tap
 * opens [WhatsNewSheet]. This build only ever *notices* an update — the sheet's button hands the
 * release page to the browser, and updating is a manual build + install.
 */
@Composable
fun UpdateAction() {
    val release = AppUpdate.available ?: return
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var sheet by remember { mutableStateOf(false) }
    val appear = remember { Animatable(if (AppUpdate.announced) 1f else 0f) }
    LaunchedEffect(Unit) {
        AppUpdate.announced = true
        appear.animateTo(1f, tween(420, easing = Motion.Pop))
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * v
                scaleY = 0.6f + 0.4f * v
            }
            .pressable { sheet = true }
            .height(44.dp)
            .widthIn(min = 104.dp)
            .clip(CircleShape)
            .background(c.primary),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (AppUpdate.failed) Icons.Rounded.Refresh else Icons.Rounded.SystemUpdateAlt,
                contentDescription = null,
                tint = c.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(if (AppUpdate.failed) R.string.app_update_pill_retry else R.string.app_update_pill),
                color = c.onPrimary,
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
    if (sheet) WhatsNewSheet(release, onDismiss = { sheet = false })
}

/**
 * What's new: the notes of every release since this build, newest first (each under its version
 * when there are several), and the "Open on GitHub" button under them, pinned while they scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatsNewSheet(release: AppUpdate.Release, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.app_update_whats_new),
            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            "Yutu ${release.version}",
            color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 14.dp),
        )
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (release.notes.isEmpty()) {
                Text(stringResource(R.string.app_update_no_notes), color = c.onSurfaceVariant, fontSize = 15.sp, lineHeight = 21.sp)
            }
            val several = release.notes.size > 1
            release.notes.forEach { notes ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (several) {
                        Text(notes.version, color = c.primary, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    notes.sections.forEach { NoteSectionBlock(it) }
                }
            }
            Text(
                stringResource(R.string.app_update_notice_only),
                color = c.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .pressable { AppUpdate.openPage(context) }
                .height(54.dp)
                .clip(CircleShape)
                .background(c.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.app_update_open_github),
                color = c.onPrimary,
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NoteSectionBlock(section: AppUpdate.NoteSection) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        section.title?.let { Text(it, color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        section.items.forEach { item ->
            Row {
                // The dot sits on the first line's middle (15 sp text on 21 sp lines).
                Box(Modifier.padding(top = 8.dp, end = 12.dp).size(5.dp).clip(CircleShape).background(c.primary))
                Text(item, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
