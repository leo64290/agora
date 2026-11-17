package com.bitchat.android.agora

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Réglages du mode poche (ce qui fait vibrer). */
data class PocketSettings(
    val assemblies: Boolean = true,
    val myIdeas: Boolean = true,
    val round: Boolean = true,
    val newIdeas: Boolean = false
)

/**
 * Le mode poche : l'écran reste éteint, le téléphone vibre seulement pour ce qui compte,
 * et on vote aux assemblées éclair directement depuis la notification.
 *
 *  · une vibration courte  = nouvelles idées à voter
 *  · deux vibrations courtes = ton idée monte
 *  · une longue vibration = assemblée éclair
 */
object AgoraNotifier {
    private const val CHANNEL = "agora_poche"
    private const val PREFS = "agora_settings"
    const val ACTION_BALLOT = "com.bitchat.android.agora.BALLOT"
    const val EXTRA_KEY = "assembly_key"
    const val EXTRA_CHOICE = "choice"
    const val EXTRA_LABEL = "label"
    const val EXTRA_NOTIF = "notif_id"

    private val SHORT = longArrayOf(0, 120)
    private val DOUBLE = longArrayOf(0, 100, 140, 100)
    private val LONG = longArrayOf(0, 700)

    @Volatile private var app: Context? = null
    private val _settings = MutableStateFlow(PocketSettings())
    val settings: StateFlow<PocketSettings> = _settings

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _settings.value = PocketSettings(
            assemblies = p.getBoolean("assemblies", true),
            myIdeas = p.getBoolean("myIdeas", true),
            round = p.getBoolean("round", true),
            newIdeas = p.getBoolean("newIdeas", false)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "AGORA (mode poche)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Assemblées éclair, tes idées qui montent, fin de manche"
                enableVibration(false) // on vibre nous-mêmes, avec un motif par événement
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun update(settings: PocketSettings) {
        _settings.value = settings
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putBoolean("assemblies", settings.assemblies)
            ?.putBoolean("myIdeas", settings.myIdeas)
            ?.putBoolean("round", settings.round)
            ?.putBoolean("newIdeas", settings.newIdeas)
            ?.apply()
    }

    fun testVibration(kind: Int) {
        vibrate(when (kind) { 0 -> SHORT; 1 -> DOUBLE; else -> LONG })
    }

    private fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun openApp(ctx: Context): PendingIntent? {
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(ctx, 4100, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun post(id: Int, title: String, text: String?, pattern: LongArray?, build: (NotificationCompat.Builder) -> Unit = {}) {
        val ctx = app ?: return
        pattern?.let { vibrate(it) }
        if (!canNotify(ctx)) return
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            // Un groupe par notification : Android ne les met pas en paquet, les boutons restent visibles
            .setGroup("agora_$id")
        if (text != null) b.setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        build(b)
        try { NotificationManagerCompat.from(ctx).notify(id, b.build()) } catch (_: SecurityException) { }
    }

    private fun vibrate(pattern: LongArray) {
        val ctx = app ?: return
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) { }
    }

    private fun idFor(key: String) = 4200 + (key.hashCode() and 0x3FF)

    fun newAssembly(key: String, question: String, choices: List<String>) {
        if (!_settings.value.assemblies) return
        val ctx = app ?: return
        val notifId = idFor(key)
        post(notifId, "Assemblée éclair", question, LONG) { b ->
            choices.take(3).forEachIndexed { i, label ->
                val intent = Intent(ctx, AgoraActionReceiver::class.java).apply {
                    action = ACTION_BALLOT
                    putExtra(EXTRA_KEY, key)
                    putExtra(EXTRA_CHOICE, i)
                    putExtra(EXTRA_LABEL, label)
                    putExtra(EXTRA_NOTIF, notifId)
                }
                val pi = PendingIntent.getBroadcast(ctx, notifId * 4 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                b.addAction(0, label, pi)
            }
        }
    }

    fun ballotSent(notifId: Int, label: String) {
        post(notifId, "Vote envoyé : $label", "Tu n'as même pas ouvert l'appli. Résultat à la fin du minuteur.", null) { it.setTimeoutAfter(15_000) }
    }

    fun ballotRefused(notifId: Int, reason: String) {
        post(notifId, "Vote impossible", reason, null)
    }

    fun assemblyEnded(key: String, question: String, winner: String?) {
        if (!_settings.value.assemblies) return
        post(idFor(key), "Résultat : ${winner ?: "égalité"}", question, SHORT)
    }

    fun myIdea(title: String, text: String?) {
        if (!_settings.value.myIdeas) return
        post(4101, title, text, DOUBLE)
    }

    fun round(text: String) {
        if (!_settings.value.round) return
        post(4102, "AGORA · manche terminée", text, SHORT)
    }

    fun roundSoon() {
        if (!_settings.value.round) return
        post(4102, "Fin de la manche dans 2 minutes", "L'idée en tête va être gravée dans le Livre du cortège.", SHORT)
    }

    fun newIdeas(count: Int) {
        if (!_settings.value.newIdeas) return
        post(4103, "$count idées attendent ton vote", "Ouvre le vote en cartes : 5 secondes par idée.", SHORT)
    }
}

/** Le bouton de réponse dans la notification d'une assemblée éclair. */
class AgoraActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AgoraNotifier.ACTION_BALLOT) return
        val key = intent.getStringExtra(AgoraNotifier.EXTRA_KEY) ?: return
        val choice = intent.getIntExtra(AgoraNotifier.EXTRA_CHOICE, -1)
        val label = intent.getStringExtra(AgoraNotifier.EXTRA_LABEL) ?: ""
        val notifId = intent.getIntExtra(AgoraNotifier.EXTRA_NOTIF, 0)
        val refused = AgoraHub.ballot(key, choice)
        if (refused == null) AgoraNotifier.ballotSent(notifId, label) else AgoraNotifier.ballotRefused(notifId, refused)
    }
}
