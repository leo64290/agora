package com.bitchat.android.agora

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pseudos du jeu, tirés de l'identité du téléphone : jamais de vrai nom. */
object AgoraNames {
    private val animals = listOf(
        "Renard", "Mésange", "Loutre", "Hibou", "Castor", "Lynx", "Abeille", "Hérisson",
        "Blaireau", "Moineau", "Chouette", "Écureuil", "Pie", "Fourmi", "Lapin", "Cerf",
        "Grenouille", "Tortue", "Martinet", "Héron", "Belette", "Corbeau", "Dauphin", "Loup",
        "Chamois", "Marmotte", "Libellule", "Coccinelle", "Faucon", "Phoque", "Bouquetin", "Grillon"
    )

    fun pseudo(playerId: String): String {
        var h = 0L
        for (c in playerId) h = (h * 31 + c.code) and 0x7FFFFFFF
        val animal = animals[(h % animals.size).toInt()]
        val num = ((h / animals.size) % 99 + 1).toInt()
        return "$animal-" + num.toString().padStart(2, '0')
    }

    fun pseudos(ids: List<String>): String = when (ids.size) {
        0 -> ""
        1 -> pseudo(ids[0])
        else -> ids.dropLast(1).joinToString(", ") { pseudo(it) } + " et " + pseudo(ids.last())
    }
}

/** Petit stockage clé-valeur (SharedPreferences sur Android, une table en mémoire dans les tests). */
interface AgoraStore {
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun clear()
}

class MemoryStore : AgoraStore {
    private val map = HashMap<String, Any>()
    override fun getLong(key: String, default: Long) = (map[key] as? Long) ?: default
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getString(key: String) = map[key] as? String
    override fun putString(key: String, value: String) { map[key] = value }
    override fun clear() = map.clear()
}

data class BadgeView(val id: String, val name: String, val desc: String, val got: Boolean, val progress: String)

data class LevelView(val title: String, val nextTitle: String?, val nextAt: Int?, val progress: Float)

data class DayStats(
    val ideas: Int,
    val votes: Int,
    val rounds: Int,
    val maxPhones: Int,
    val relayed: Long,
    val decisions: List<String>,
    val challenges: Int
)

/**
 * Ce qui reste sur MON téléphone d'une manif à l'autre : badges, niveau, stats du jour.
 * Rien de tout ça n'est envoyé dans le réseau.
 */
class AgoraProgress(
    private val store: AgoraStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    /** Remet les compteurs « du jour » à zéro quand la date change. */
    private fun rollDay() {
        val d = today().toString()
        if (store.getString("day") != d) {
            store.putString("day", d)
            for (k in listOf("d_ideas", "d_votes", "d_rounds", "d_phones", "d_relayed", "d_challenges")) store.putLong(k, 0)
            store.putString("d_decisions", "")
        }
    }

    private fun inc(key: String, by: Long = 1) = store.putLong(key, store.getLong(key, 0) + by)

    fun onIdeaProposed() = synchronized(this) { rollDay(); inc("ideas"); inc("d_ideas") }

    fun onVoteCast() = synchronized(this) {
        rollDay(); inc("d_votes")
        val dv = store.getLong("d_votes", 0)
        if (dv > store.getLong("best_votes_day", 0)) store.putLong("best_votes_day", dv)
        val days = (store.getString("manif_days") ?: "").split(',').filter { it.isNotEmpty() }.toMutableSet()
        if (days.add(today().toString())) store.putString("manif_days", days.sorted().takeLast(30).joinToString(","))
    }

    fun onBridging() = synchronized(this) { inc("bridging") }
    fun onDuelWon() = synchronized(this) { inc("duels") }
    fun onBetWon() = synchronized(this) { inc("bets_won") }
    fun onCrowned() = synchronized(this) { inc("crowned") }
    fun onRoundSealed() = synchronized(this) { rollDay(); inc("d_rounds") }
    fun onChallengeDone() = synchronized(this) { rollDay(); inc("challenges"); inc("d_challenges") }

    fun onPhones(count: Int) = synchronized(this) {
        rollDay()
        if (count > store.getLong("d_phones", 0)) store.putLong("d_phones", count.toLong())
    }

    fun addRelayed(n: Long) = synchronized(this) {
        if (n <= 0) return@synchronized
        rollDay(); inc("relayed", n); inc("d_relayed", n)
    }

    fun onAssemblyEnded(question: String, winner: String?) = synchronized(this) {
        rollDay()
        val line = AgoraCodec.clean(question, 80) + " : " + (winner ?: "égalité")
        val old = (store.getString("d_decisions") ?: "").split('\n').filter { it.isNotEmpty() }
        store.putString("d_decisions", (old + line).takeLast(10).joinToString("\n"))
    }

    fun relayedTotal(): Long = store.getLong("relayed", 0)

    /** Points Relais du jour : 1 point par 100 messages relayés, 50 au maximum. */
    fun relayPoints(): Int = synchronized(this) {
        rollDay()
        minOf(50L, store.getLong("d_relayed", 0) / AgoraRules.RELAY_MESSAGES_PER_POINT).toInt()
    }

    fun dayStats(): DayStats = synchronized(this) {
        rollDay()
        DayStats(
            ideas = store.getLong("d_ideas", 0).toInt(),
            votes = store.getLong("d_votes", 0).toInt(),
            rounds = store.getLong("d_rounds", 0).toInt(),
            maxPhones = store.getLong("d_phones", 0).toInt(),
            relayed = store.getLong("d_relayed", 0),
            decisions = (store.getString("d_decisions") ?: "").split('\n').filter { it.isNotEmpty() },
            challenges = store.getLong("d_challenges", 0).toInt()
        )
    }

    fun badges(): List<BadgeView> = synchronized(this) {
        val ideas = store.getLong("ideas", 0)
        val bridging = store.getLong("bridging", 0)
        val duels = store.getLong("duels", 0)
        val relayed = store.getLong("relayed", 0)
        val betsWon = store.getLong("bets_won", 0)
        val bestVotes = store.getLong("best_votes_day", 0)
        val crowned = store.getLong("crowned", 0)
        val days = (store.getString("manif_days") ?: "").split(',').count { it.isNotEmpty() }.toLong()
        fun b(id: String, name: String, desc: String, have: Long, need: Long) =
            BadgeView(id, name, desc, have >= need, if (have >= need) "Obtenu" else "${minOf(have, need)} sur $need")
        listOf(
            b("voix", "Première voix", "Proposer ta première idée.", ideas, 1),
            b("pont", "Pont", "Avoir une idée rassembleuse.", bridging, 1),
            b("coauteur", "Co-auteur", "Gagner un duel en améliorant l'idée de quelqu'un.", duels, 1),
            b("relais", "Relais d'acier", "Faire suivre 1 000 messages du réseau.", relayed, 1000),
            b("prophete", "Prophète", "Gagner 3 paris Flair.", betsWon, 3),
            b("oreille", "Oreille d'or", "Voter 50 fois dans une même journée.", bestVotes, 50),
            b("couronne", "Couronné·e", "Avoir une idée gravée dans le Livre du cortège.", crowned, 1),
            b("fidele", "Fidèle", "Jouer pendant 3 manifs différentes.", days, 3)
        )
    }

    fun level(total: Int): LevelView {
        val steps = listOf(0 to "Nouvelle voix", 20 to "Porte-voix", 60 to "Rassembleur·se", 150 to "Pont du cortège", 250 to "Agora vivante")
        val idx = steps.indexOfLast { total >= it.first }.coerceAtLeast(0)
        val next = steps.getOrNull(idx + 1)
        val from = steps[idx].first
        val progress = if (next == null) 1f else ((total - from).toFloat() / (next.first - from)).coerceIn(0f, 1f)
        return LevelView(steps[idx].second, next?.second, next?.first, progress)
    }

    fun clear() = synchronized(this) { store.clear() }
}
