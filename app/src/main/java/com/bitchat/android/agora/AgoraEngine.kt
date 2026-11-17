package com.bitchat.android.agora

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Le moteur du jeu AGORA. Chaque téléphone a le sien et calcule tout lui-même :
 * pas de chef, pas de serveur. Les messages arrivent dans n'importe quel ordre,
 * en double ou pas du tout : le moteur s'en sort quand même (le dernier vote
 * d'une personne gagne, et chacun renvoie ses propres données régulièrement).
 *
 * Kotlin pur : aucune dépendance Android.
 */
class AgoraEngine(
    val myId: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val randomId: () -> String = { randomHex(8) },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val lock = Any()

    private val ideas = LinkedHashMap<String, Idea>()
    private val votes = HashMap<String, Vote>()            // "votant|idée"
    private val assemblies = LinkedHashMap<String, Assembly>()
    private val ballots = HashMap<String, Ballot>()        // "votant|assemblée"
    private val bets = HashMap<String, Bet>()              // "joueur|manche"
    private val claps = HashMap<String, ClapReport>()      // "joueur|idée"

    private val replaced = HashMap<String, String>()       // idée de départ -> version gagnante
    private val replacedAccord = HashMap<String, Int>()    // version gagnante -> accord battu
    private val coAuthors = HashMap<String, List<String>>()// version gagnante -> auteurs précédents
    private val lostDuels = HashSet<String>()

    private val book = ArrayList<BookEntry>()
    private val betWins = HashMap<String, MutableList<Long>>()
    private val agoraPlayers = HashMap<String, Long>()
    private val clapsDirty = HashSet<String>()

    private var trackedRound = -1L
    private var roundEndWarned = -1L
    private val notifiedBridging = HashSet<String>()
    private val notifiedAssemblies = HashSet<String>()
    private val endedAssemblies = HashSet<String>()
    private val doneChallenges = HashSet<String>()
    private val bestRank = HashMap<String, Int>()
    private var cardsBaseline = 0
    private val events = ArrayList<AgoraEvent>()

    /** Nombre de téléphones BitChat joignables (donné par le réseau, pour le radar et les défis). */
    @Volatile var meshPeers: Int = 0

    // ------------------------------------------------------------------ temps

    fun roundOf(t: Long): Long = Math.floorDiv(t, AgoraRules.ROUND_MS)
    fun roundEnd(round: Long): Long = (round + 1) * AgoraRules.ROUND_MS

    private fun fixTime(t: Long, now: Long): Long? =
        if (t > now + AgoraRules.FUTURE_TOLERANCE_MS) null else min(t, now)

    // ------------------------------------------------------------- réception

    /** Un message AGORA reçu d'un autre téléphone. [senderId] = joueur signé par BitChat. */
    fun receive(senderId: String, payload: ByteArray): Boolean {
        if (senderId == myId || senderId.isEmpty()) return false
        val records = AgoraCodec.decode(payload)
        if (records.isEmpty()) return false
        synchronized(lock) {
            val now = clock()
            agoraPlayers[senderId] = now
            var changed = false
            for (r in records) if (apply(senderId, r, now)) changed = true
            return changed
        }
    }

    private fun apply(author: String, r: AgoraRecord, now: Long): Boolean = when (r) {
        is AgoraRecord.IdeaRec -> applyIdea(author, r, now)
        is AgoraRecord.VoteRec -> applyVote(author, r.ideaKey, r.value, r.at, now)
        is AgoraRecord.AssemblyRec -> applyAssembly(author, r, now)
        is AgoraRecord.BallotRec -> applyBallot(author, r.assemblyKey, r.choice, r.at, now)
        is AgoraRecord.BetRec -> applyBet(author, r.round, r.ideaKey, r.at, now)
        is AgoraRecord.ClapRec -> applyClaps(author, r.ideaKey, r.total, r.at, now)
    }

    private fun applyIdea(author: String, r: AgoraRecord.IdeaRec, now: Long): Boolean {
        val created = fixTime(r.createdAt, now) ?: return false
        val key = "$author:${r.id}"
        if (ideas.containsKey(key)) return false
        val parent = r.parentKey
        if (parent != null && authorOfKey(parent) == author) return false
        // +1 de marge : deux téléphones peuvent voir une vieille idée s'effacer à quelques secondes d'écart
        val active = ideas.values.count { it.authorId == author && isAlive(it, now) && it.key !in replaced }
        if (active >= AgoraRules.MAX_ACTIVE_IDEAS + 1) return false
        if (ideas.values.any { it.authorId == author && abs(it.createdAt - created) < AgoraRules.MIN_IDEA_INTERVAL_MS }) return false
        ideas[key] = Idea(key, author, r.kind, r.text, created, parent)
        return true
    }

    private fun applyVote(voter: String, ideaKey: String, value: Int, rawAt: Long, now: Long): Boolean {
        val at = fixTime(rawAt, now) ?: return false
        if (at < now - AgoraRules.WINDOW_MS) return false
        if (value !in -1..1) return false
        if (authorOfKey(ideaKey) == voter) return false
        val k = "$voter|$ideaKey"
        val old = votes[k]
        if (old != null && old.at >= at) return false
        votes[k] = Vote(voter, ideaKey, value, at)
        return true
    }

    private fun applyAssembly(author: String, r: AgoraRecord.AssemblyRec, now: Long): Boolean {
        val created = fixTime(r.createdAt, now) ?: return false
        val duration = r.endsAt - r.createdAt
        if (duration < AgoraRules.MIN_ASSEMBLY_MS || duration > AgoraRules.MAX_ASSEMBLY_MS) return false
        val key = "$author:${r.id}"
        if (assemblies.containsKey(key)) return false
        val endsAt = created + duration
        if (endsAt < now - AgoraRules.WINDOW_MS) return false
        if (assemblies.values.any { it.authorId == author && it.endsAt > created && it.createdAt < endsAt }) return false
        assemblies[key] = Assembly(key, author, r.question, r.choices, created, endsAt)
        if (endsAt > now && notifiedAssemblies.add(key)) {
            events.add(AgoraEvent.NewAssembly(key, r.question, r.choices))
        }
        return true
    }

    private fun applyBallot(voter: String, assemblyKey: String, choice: Int, rawAt: Long, now: Long): Boolean {
        val at = fixTime(rawAt, now) ?: return false
        if (choice !in 0 until AgoraRules.MAX_CHOICES) return false
        val k = "$voter|$assemblyKey"
        val old = ballots[k]
        if (old != null && old.at >= at) return false
        ballots[k] = Ballot(voter, assemblyKey, choice, at)
        return true
    }

    private fun applyBet(player: String, round: Long, ideaKey: String, rawAt: Long, now: Long): Boolean {
        val at = fixTime(rawAt, now) ?: return false
        if (roundOf(at) != round) return false
        if (at > roundEnd(round) - AgoraRules.BET_CUTOFF_MS) return false
        if (round < roundOf(now) - 1) return false
        val k = "$player|$round"
        if (bets.containsKey(k)) return false
        bets[k] = Bet(player, round, ideaKey, at)
        return true
    }

    private fun applyClaps(player: String, ideaKey: String, total: Int, rawAt: Long, now: Long): Boolean {
        val at = fixTime(rawAt, now) ?: return false
        val k = "$player|$ideaKey"
        val old = claps[k]
        val t = min(total, AgoraRules.MAX_CLAPS_PER_PLAYER)
        if (old != null && old.total >= t) return false
        claps[k] = ClapReport(player, ideaKey, t, at)
        return true
    }

    // -------------------------------------------------------- actions locales

    fun propose(kind: IdeaKind, text: String, parentKey: String? = null): AgoraResult = synchronized(lock) {
        val now = clock()
        val clean = AgoraCodec.clean(text, AgoraRules.MAX_TEXT)
        if (clean.length < 3) return AgoraResult.Refused("Ton idée est trop courte.")
        if (parentKey != null) {
            val parent = ideas[parentKey]
            if (parent == null || !isVisible(parent, now)) return AgoraResult.Refused("Cette idée n'est plus sur le mur.")
            if (parent.authorId == myId) return AgoraResult.Refused("Tu ne peux pas lancer un duel contre ta propre idée.")
            if (ideas.values.any { it.parentKey == parentKey && it.authorId == myId && isVisible(it, now) })
                return AgoraResult.Refused("Tu as déjà une version en duel contre cette idée.")
        }
        val mine = ideas.values.count { it.authorId == myId && isAlive(it, now) && it.key !in replaced }
        if (mine >= AgoraRules.MAX_ACTIVE_IDEAS) return AgoraResult.Refused("Tu as déjà 3 idées sur le mur. Attends qu'une s'efface.")
        val last = ideas.values.filter { it.authorId == myId }.maxOfOrNull { it.createdAt }
        if (last != null && now - last < AgoraRules.MIN_IDEA_INTERVAL_MS) {
            val s = (AgoraRules.MIN_IDEA_INTERVAL_MS - (now - last)) / 1000 + 1
            return AgoraResult.Refused("Attends encore $s secondes avant une nouvelle idée.")
        }
        val id = randomId()
        val key = "$myId:$id"
        ideas[key] = Idea(key, myId, kind, clean, now, parentKey)
        AgoraResult.Ok(AgoraCodec.encode(listOf(AgoraRecord.IdeaRec(id, kind, now, parentKey, clean))), key)
    }

    fun vote(ideaKey: String, value: Int): AgoraResult = synchronized(lock) {
        val now = clock()
        val idea = ideas[ideaKey] ?: return AgoraResult.Refused("Cette idée n'est plus sur le mur.")
        if (idea.authorId == myId) return AgoraResult.Refused("Tu ne peux pas voter pour ta propre idée.")
        if (value !in -1..1) return AgoraResult.Refused("Vote inconnu.")
        votes["$myId|$ideaKey"] = Vote(myId, ideaKey, value, now)
        AgoraResult.Ok(AgoraCodec.encode(listOf(AgoraRecord.VoteRec(ideaKey, value, now))))
    }

    fun createAssembly(question: String, choices: List<String>, durationMs: Long): AgoraResult = synchronized(lock) {
        val now = clock()
        val q = AgoraCodec.clean(question, AgoraRules.MAX_TEXT)
        val cs = choices.map { AgoraCodec.clean(it, AgoraRules.MAX_CHOICE_TEXT) }.filter { it.isNotEmpty() }
        if (q.length < 3) return AgoraResult.Refused("Écris une question.")
        if (cs.size < AgoraRules.MIN_CHOICES || cs.size > AgoraRules.MAX_CHOICES) return AgoraResult.Refused("Il faut entre 2 et 4 réponses.")
        if (durationMs < AgoraRules.MIN_ASSEMBLY_MS || durationMs > AgoraRules.MAX_ASSEMBLY_MS) return AgoraResult.Refused("Le minuteur doit durer entre 1 et 15 minutes.")
        if (assemblies.values.any { it.authorId == myId && it.endsAt > now }) return AgoraResult.Refused("Tu as déjà une assemblée en cours.")
        val id = randomId()
        val key = "$myId:$id"
        assemblies[key] = Assembly(key, myId, q, cs, now, now + durationMs)
        notifiedAssemblies.add(key)
        AgoraResult.Ok(AgoraCodec.encode(listOf(AgoraRecord.AssemblyRec(id, now, now + durationMs, q, cs))), key)
    }

    fun ballot(assemblyKey: String, choice: Int): AgoraResult = synchronized(lock) {
        val now = clock()
        val a = assemblies[assemblyKey] ?: return AgoraResult.Refused("Cette assemblée n'existe plus.")
        if (now >= a.endsAt) return AgoraResult.Refused("Le vote est terminé.")
        if (choice !in a.choices.indices) return AgoraResult.Refused("Réponse inconnue.")
        ballots["$myId|$assemblyKey"] = Ballot(myId, assemblyKey, choice, now)
        AgoraResult.Ok(AgoraCodec.encode(listOf(AgoraRecord.BallotRec(assemblyKey, choice, now))))
    }

    fun bet(ideaKey: String): AgoraResult = synchronized(lock) {
        val now = clock()
        val round = roundOf(now)
        if (now > roundEnd(round) - AgoraRules.BET_CUTOFF_MS) return AgoraResult.Refused("Trop tard : les paris ferment 5 minutes avant la fin de la manche.")
        if (bets.containsKey("$myId|$round")) return AgoraResult.Refused("Tu as déjà parié dans cette manche.")
        val idea = ideas[ideaKey]
        if (idea == null || !isVisible(idea, now)) return AgoraResult.Refused("Cette idée n'est plus sur le mur.")
        bets["$myId|$round"] = Bet(myId, round, ideaKey, now)
        AgoraResult.Ok(AgoraCodec.encode(listOf(AgoraRecord.BetRec(round, ideaKey, now))))
    }

    /** Un applaudissement. Rien n'est envoyé tout de suite : voir [flushClaps]. */
    fun clap(ideaKey: String) = synchronized(lock) {
        val now = clock()
        if (!ideas.containsKey(ideaKey)) return@synchronized
        val k = "$myId|$ideaKey"
        val total = min((claps[k]?.total ?: 0) + 1, AgoraRules.MAX_CLAPS_PER_PLAYER)
        claps[k] = ClapReport(myId, ideaKey, total, now)
        clapsDirty.add(ideaKey)
    }

    /** Les applaudissements regroupés, à envoyer toutes les 30 secondes (null si rien de neuf). */
    fun flushClaps(): ByteArray? = synchronized(lock) {
        if (clapsDirty.isEmpty()) return null
        val now = clock()
        val recs = clapsDirty.mapNotNull { key -> claps["$myId|$key"]?.let { AgoraRecord.ClapRec(key, it.total, now) } }
        clapsDirty.clear()
        if (recs.isEmpty()) null else AgoraCodec.encode(recs)
    }

    /** Tout ce qui est à moi et encore vivant, pour les nouveaux arrivants (toutes les 90 s). */
    fun snapshot(): List<ByteArray> = synchronized(lock) {
        val now = clock()
        val recs = ArrayList<AgoraRecord>()
        for (i in ideas.values) if (i.authorId == myId && isAlive(i, now)) {
            recs.add(AgoraRecord.IdeaRec(i.key.substringAfter(':'), i.kind, i.createdAt, i.parentKey, i.text))
        }
        for (a in assemblies.values) if (a.authorId == myId && a.endsAt >= now - AgoraRules.WINDOW_MS) {
            recs.add(AgoraRecord.AssemblyRec(a.key.substringAfter(':'), a.createdAt, a.endsAt, a.question, a.choices))
        }
        for (v in votes.values) if (v.voterId == myId && v.at >= now - AgoraRules.WINDOW_MS) {
            recs.add(AgoraRecord.VoteRec(v.ideaKey, v.value, v.at))
        }
        for (b in ballots.values) if (b.voterId == myId && assemblies.containsKey(b.assemblyKey)) {
            recs.add(AgoraRecord.BallotRec(b.assemblyKey, b.choice, b.at))
        }
        for (b in bets.values) if (b.playerId == myId && b.round >= roundOf(now) - 1) {
            recs.add(AgoraRecord.BetRec(b.round, b.ideaKey, b.at))
        }
        for (c in claps.values) if (c.playerId == myId && ideas.containsKey(c.ideaKey)) {
            recs.add(AgoraRecord.ClapRec(c.ideaKey, c.total, c.at))
        }
        AgoraCodec.encodeChunks(recs)
    }

    /** Mode panique : on oublie tout. */
    fun clear() = synchronized(lock) {
        ideas.clear(); votes.clear(); assemblies.clear(); ballots.clear(); bets.clear(); claps.clear()
        replaced.clear(); replacedAccord.clear(); coAuthors.clear(); lostDuels.clear(); book.clear()
        betWins.clear(); agoraPlayers.clear(); clapsDirty.clear(); notifiedBridging.clear()
        notifiedAssemblies.clear(); endedAssemblies.clear(); doneChallenges.clear(); bestRank.clear()
        events.clear(); trackedRound = -1L; roundEndWarned = -1L; cardsBaseline = 0
    }

    // ------------------------------------------------------- règles de vie

    private fun authorOfKey(key: String): String = key.substringBefore(':')

    private fun lastActivity(i: Idea, votesByIdea: Map<String, List<Vote>>? = null): Long {
        val list = votesByIdea?.get(i.key) ?: votes.values.filter { it.ideaKey == i.key }
        var last = i.createdAt
        for (v in list) if (v.at > last) last = v.at
        return last
    }

    private fun isAlive(i: Idea, now: Long, votesByIdea: Map<String, List<Vote>>? = null): Boolean =
        lastActivity(i, votesByIdea) >= now - AgoraRules.WINDOW_MS

    private fun isVisible(i: Idea, now: Long, votesByIdea: Map<String, List<Vote>>? = null): Boolean =
        i.key !in replaced && i.key !in lostDuels && isAlive(i, now, votesByIdea)

    private fun aliveVotes(now: Long): List<Vote> = votes.values.filter { it.at >= now - AgoraRules.WINDOW_MS }

    private class Stats(
        val pour: Int, val contre: Int, val passe: Int,
        val consensus: Double, val bridging: Boolean, val divisive: Boolean
    )

    private fun stats(idea: Idea, list: List<Vote>, groups: OpinionGroups): Stats {
        var pour = 0; var contre = 0; var passe = 0
        val agree = intArrayOf(0, 0); val n = intArrayOf(0, 0)
        for (v in list) {
            if (v.voterId == idea.authorId) continue
            when (v.value) { 1 -> pour++; -1 -> contre++; else -> passe++ }
            if (v.value != 0) {
                val g = groups.groupOf[v.voterId] ?: continue
                n[g]++
                if (v.value == 1) agree[g]++
            }
        }
        val consensus: Double
        var bridging: Boolean
        var divisive = false
        if (groups.groupCount == 2) {
            val p0 = (agree[0] + 1.0) / (n[0] + 2.0)
            val p1 = (agree[1] + 1.0) / (n[1] + 2.0)
            consensus = sqrt(p0 * p1)
            val enough = n[0] >= AgoraRules.BRIDGE_MIN_VOTES_PER_GROUP && n[1] >= AgoraRules.BRIDGE_MIN_VOTES_PER_GROUP
            val s0 = if (n[0] > 0) agree[0].toDouble() / n[0] else 0.0
            val s1 = if (n[1] > 0) agree[1].toDouble() / n[1] else 0.0
            bridging = enough && s0 >= AgoraRules.BRIDGE_SHARE && s1 >= AgoraRules.BRIDGE_SHARE
            divisive = enough && ((s0 >= AgoraRules.BRIDGE_SHARE && s1 < AgoraRules.DIVIDE_LOW_SHARE) ||
                (s1 >= AgoraRules.BRIDGE_SHARE && s0 < AgoraRules.DIVIDE_LOW_SHARE))
        } else {
            consensus = (pour + 1.0) / (pour + contre + 2.0)
            bridging = pour + contre >= 4 && pour.toDouble() / (pour + contre) >= 0.75
        }
        return Stats(pour, contre, passe, consensus, bridging, divisive)
    }

    private class State(
        val now: Long,
        val visible: List<Idea>,
        val votesByIdea: Map<String, List<Vote>>,
        val groups: OpinionGroups,
        val stats: Map<String, Stats>
    )

    private fun state(now: Long): State {
        val alive = aliveVotes(now)
        val byIdea = alive.groupBy { it.ideaKey }
        val visible = ideas.values.filter { isVisible(it, now, byIdea) }
        val groups = AgoraOpinion.compute(alive, visible.map { it.key }.toSet())
        val stats = HashMap<String, Stats>()
        for (i in ideas.values) stats[i.key] = stats(i, byIdea[i.key] ?: emptyList(), groups)
        return State(now, visible, byIdea, groups, stats)
    }

    private fun authorsOf(i: Idea): List<String> = listOf(i.authorId) + (coAuthors[i.key] ?: emptyList())

    // ------------------------------------------------------------------ tick

    /**
     * À appeler chaque seconde : nettoie, règle les duels, grave les manches,
     * et renvoie les événements (vibrations, badges…).
     */
    fun tick(): List<AgoraEvent> = synchronized(lock) {
        val now = clock()
        prune(now)
        var st = state(now)

        // Duels
        var duelChanged = false
        for (child in st.visible.filter { it.parentKey != null }) {
            val parentKey = child.parentKey!!
            val parent = ideas[parentKey] ?: continue
            if (parentKey in replaced || !isVisible(parent, now, st.votesByIdea)) continue
            val cs = st.stats[child.key] ?: continue
            val ps = st.stats[parentKey] ?: continue
            if (cs.pour + cs.contre >= AgoraRules.DUEL_MIN_VOTES && cs.consensus > ps.consensus) {
                replaced[parentKey] = child.key
                replacedAccord[child.key] = (ps.consensus * 100).roundToInt()
                coAuthors[child.key] = authorsOf(parent).filter { it != child.authorId }.distinct()
                duelChanged = true
                if (child.authorId == myId || myId in authorsOf(parent)) {
                    events.add(AgoraEvent.DuelWon(child.key, child.authorId == myId))
                }
            } else if (now - child.createdAt > AgoraRules.DUEL_TIMEOUT_MS) {
                lostDuels.add(child.key)
                duelChanged = true
            }
        }
        if (duelChanged) st = state(now)

        val wall = wallFrom(st)

        // Manche terminée : on grave l'idée gagnante et on règle les paris
        val round = roundOf(now)
        if (trackedRound < 0) {
            trackedRound = round
        } else if (round > trackedRound) {
            val top = wall.firstOrNull { it.pour + it.contre >= AgoraRules.MIN_VOTES_TO_WIN_ROUND }
            val entry = top?.let { BookEntry(trackedRound, it.key, it.text, it.authorIds, it.accord, now) }
            if (entry != null) book.add(entry)
            var myBetWon = false
            for (b in bets.values) if (b.round == trackedRound && top != null && b.ideaKey == top.key) {
                betWins.getOrPut(b.playerId) { ArrayList() }.add(now)
                if (b.playerId == myId) myBetWon = true
            }
            events.add(AgoraEvent.RoundSealed(entry, myBetWon, entry?.authorIds?.contains(myId) == true))
            trackedRound = round
        }
        val secondsLeft = (roundEnd(round) - now) / 1000
        if (secondsLeft <= 120 && roundEndWarned != round) {
            roundEndWarned = round
            events.add(AgoraEvent.RoundEndingSoon(2))
        }

        // Mes idées qui montent
        for (v in wall) if (v.isMine) {
            if (v.bridging && notifiedBridging.add(v.key)) events.add(AgoraEvent.MyIdeaBridging(v.key, v.text))
            val best = bestRank[v.key] ?: Int.MAX_VALUE
            if (v.rank < best) {
                bestRank[v.key] = v.rank
                if (v.rank <= 3 && best != Int.MAX_VALUE) events.add(AgoraEvent.MyIdeaClimbed(v.key, v.rank))
            }
        }

        // Assemblées terminées (seulement celles qui viennent de finir)
        for (a in assemblies.values) {
            if (now >= a.endsAt && a.endsAt >= now - 60_000L && endedAssemblies.add(a.key)) {
                val view = assemblyView(a, now)
                events.add(AgoraEvent.AssemblyEnded(a.key, a.question, view.winner?.let { a.choices[it] }))
            }
        }

        // Défis du cortège (une fois par manche)
        for (c in challenges(now, wall)) {
            if (c.done && doneChallenges.add("${c.id}@$round")) events.add(AgoraEvent.ChallengeDone(c.id, c.label))
        }

        // Nouvelles idées à voter
        val waiting = wall.count { !it.isMine && it.myVote == null }
        if (waiting < cardsBaseline) cardsBaseline = waiting
        if (waiting >= 5 && waiting - cardsBaseline >= 5) {
            events.add(AgoraEvent.NewIdeasWaiting(waiting))
            cardsBaseline = waiting
        }

        val out = ArrayList(events)
        events.clear()
        out
    }

    private fun prune(now: Long) {
        val limit = now - AgoraRules.WINDOW_MS
        votes.values.removeAll { it.at < limit }
        val byIdea = votes.values.groupBy { it.ideaKey }
        val dead = ideas.values.filter { !isAlive(it, now, byIdea) }.map { it.key }.toSet()
        if (dead.isNotEmpty()) {
            dead.forEach { ideas.remove(it) }
            claps.values.removeAll { it.ideaKey in dead }
            replaced.keys.removeAll { it in dead }
            replacedAccord.keys.removeAll { it in dead }
            coAuthors.keys.removeAll { it in dead }
            lostDuels.removeAll { it in dead }
        }
        val deadAssemblies = assemblies.values.filter { it.endsAt < limit }.map { it.key }.toSet()
        if (deadAssemblies.isNotEmpty()) {
            deadAssemblies.forEach { assemblies.remove(it) }
            ballots.values.removeAll { it.assemblyKey in deadAssemblies }
        }
        ballots.values.removeAll { it.at < limit && !assemblies.containsKey(it.assemblyKey) }
        val currentRound = roundOf(now)
        bets.values.removeAll { it.round < currentRound - 1 }
        betWins.values.forEach { list -> list.removeAll { it < limit } }
        agoraPlayers.values.removeAll { it < limit }
        val today = day(now)
        book.removeAll { day(it.sealedAt) != today }
    }

    private fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    // ------------------------------------------------------------------ vues

    /** Le mur, classé : les idées qui rassemblent en haut. */
    fun wall(): List<IdeaView> = synchronized(lock) { wallFrom(state(clock())) }

    private fun wallFrom(st: State): List<IdeaView> {
        val now = st.now
        val views = st.visible.map { i ->
            val s = st.stats[i.key]!!
            val last = lastActivity(i, st.votesByIdea)
            val leftMs = max(0L, last + AgoraRules.WINDOW_MS - now)
            val duelNote = when {
                replacedAccord.containsKey(i.key) -> "Version 2 : a remplacé l'ancienne version (${replacedAccord[i.key]} %)"
                i.parentKey != null && ideas[i.parentKey]?.let { isVisible(it, now, st.votesByIdea) } == true -> {
                    val pAccord = ((st.stats[i.parentKey]?.consensus ?: 0.5) * 100).roundToInt()
                    "Duel : doit dépasser $pAccord % avec au moins ${AgoraRules.DUEL_MIN_VOTES} votes"
                }
                st.visible.any { it.parentKey == i.key } -> "Une version 2 la défie en duel"
                else -> null
            }
            IdeaView(
                key = i.key, kind = i.kind, text = i.text, authorIds = authorsOf(i),
                pour = s.pour, contre = s.contre, passe = s.passe,
                accord = (s.consensus * 100).roundToInt(), bridging = s.bridging, divisive = s.divisive,
                minutesLeft = ceil(leftMs / 60_000.0).toInt(), life = (leftMs.toFloat() / AgoraRules.WINDOW_MS).coerceIn(0f, 1f),
                myVote = votes["$myId|${i.key}"]?.takeIf { it.at >= now - AgoraRules.WINDOW_MS }?.value,
                isMine = myId in authorsOf(i),
                claps = claps.values.filter { it.ideaKey == i.key }.sumOf { it.total },
                rank = 0, duelNote = duelNote, consensus = s.consensus, createdAt = i.createdAt
            )
        }
        return views
            .sortedWith(compareByDescending<IdeaView> { it.consensus }.thenByDescending { it.pour + it.contre }.thenByDescending { it.createdAt }.thenBy { it.key })
            .mapIndexed { idx, v -> v.copy(rank = idx + 1) }
    }

    /** Le vote en cartes : les idées des autres que je n'ai pas encore votées, les plus récentes d'abord. */
    fun cards(): List<IdeaView> = synchronized(lock) {
        wallFrom(state(clock())).filter { !it.isMine && it.myVote == null }.sortedByDescending { it.createdAt }
    }

    fun assemblies(): List<AssemblyView> = synchronized(lock) {
        val now = clock()
        val list = assemblies.values.map { assemblyView(it, now) }
        val running = list.filter { !it.ended }.sortedBy { it.secondsLeft }
        val ended = list.filter { it.ended }.sortedByDescending { assemblies[it.key]?.endsAt ?: 0L }
        running + ended
    }

    private fun assemblyView(a: Assembly, now: Long): AssemblyView {
        val valid = ballots.values.filter { it.assemblyKey == a.key && it.choice < a.choices.size && it.at <= a.endsAt }
        val counts = IntArray(a.choices.size)
        valid.forEach { counts[it.choice]++ }
        val ended = now >= a.endsAt
        val total = valid.size
        var winner: Int? = null
        if (ended && total > 0) {
            val maxC = counts.maxOrNull() ?: 0
            if (counts.count { it == maxC } == 1) winner = counts.indexOf(maxC)
        }
        return AssemblyView(
            key = a.key, authorId = a.authorId, question = a.question, choices = a.choices,
            secondsLeft = max(0L, (a.endsAt - now + 999) / 1000), ended = ended, voters = total,
            myChoice = ballots["$myId|${a.key}"]?.takeIf { it.at <= a.endsAt }?.choice,
            results = if (ended) counts.map { if (total == 0) 0 else (it * 100.0 / total).roundToInt() } else null,
            winner = winner, isMine = a.authorId == myId
        )
    }

    private fun challenges(now: Long, wall: List<IdeaView>): List<ChallengeView> {
        val recentVotes = votes.values.count { it.at >= now - AgoraRules.CHALLENGE_VOTES_WINDOW_MS }
        val phones = max(meshPeers + 1, agoraPlayers.size + 1)
        return listOf(
            ChallengeView("votes", "${AgoraRules.CHALLENGE_VOTES} votes en 10 minutes", min(recentVotes, AgoraRules.CHALLENGE_VOTES), AgoraRules.CHALLENGE_VOTES),
            ChallengeView("ponts", "${AgoraRules.CHALLENGE_BRIDGES} idées rassembleuses", min(wall.count { it.bridging }, AgoraRules.CHALLENGE_BRIDGES), AgoraRules.CHALLENGE_BRIDGES),
            ChallengeView("telephones", "${AgoraRules.CHALLENGE_PHONES} téléphones reliés", min(phones, AgoraRules.CHALLENGE_PHONES), AgoraRules.CHALLENGE_PHONES)
        )
    }

    fun round(): RoundView = synchronized(lock) {
        val now = clock()
        val st = state(now)
        val wall = wallFrom(st)
        val r = roundOf(now)
        val start = r * AgoraRules.ROUND_MS
        val myBet = bets["$myId|$r"]
        RoundView(
            round = r,
            secondsLeft = max(0L, (roundEnd(r) - now) / 1000),
            progress = ((now - start).toFloat() / AgoraRules.ROUND_MS).coerceIn(0f, 1f),
            betOpen = myBet == null && now <= roundEnd(r) - AgoraRules.BET_CUTOFF_MS,
            myBet = myBet?.let { b -> ideas[b.ideaKey]?.text ?: "une idée disparue" },
            betOptions = wall.take(3),
            book = book.sortedByDescending { it.round },
            challenges = challenges(now, wall)
        )
    }

    /** Le classement des joueurs (points vérifiables par tous, sans le Relais). */
    fun scores(): List<ScoreView> = synchronized(lock) {
        val now = clock()
        val st = state(now)
        val wall = wallFrom(st)
        val ras = HashMap<String, Double>()
        for (v in wall) {
            val pts = 10.0 * v.pour * v.consensus
            val share = pts / v.authorIds.size
            for (a in v.authorIds) ras[a] = (ras[a] ?: 0.0) + share
        }
        val visibleAuthors = HashMap<String, String>()
        for (i in st.visible) visibleAuthors[i.key] = i.authorId
        val participationVotes = HashMap<String, Int>()
        val openness = HashMap<String, Int>()
        for (v in aliveVotes(now)) {
            val author = visibleAuthors[v.ideaKey] ?: continue
            if (author == v.voterId) continue
            participationVotes[v.voterId] = (participationVotes[v.voterId] ?: 0) + 1
            if (v.value == 1 && st.groups.groupCount == 2) {
                val gv = st.groups.groupOf[v.voterId]
                val ga = st.groups.groupOf[author]
                if (gv != null && ga != null && gv != ga) openness[v.voterId] = (openness[v.voterId] ?: 0) + 1
            }
        }
        val ballotCount = HashMap<String, Int>()
        for (b in ballots.values) {
            val a = assemblies[b.assemblyKey] ?: continue
            if (b.choice < a.choices.size && b.at <= a.endsAt) ballotCount[b.voterId] = (ballotCount[b.voterId] ?: 0) + 1
        }
        val players = HashSet<String>()
        players.add(myId)
        players.addAll(ras.keys); players.addAll(participationVotes.keys); players.addAll(ballotCount.keys)
        players.addAll(betWins.filter { it.value.isNotEmpty() }.keys)
        players.map { p ->
            ScoreView(
                playerId = p,
                rassembleur = (ras[p] ?: 0.0).roundToInt(),
                participation = min(participationVotes[p] ?: 0, AgoraRules.PARTICIPATION_MAX_VOTES) * AgoraRules.PARTICIPATION_PER_VOTE +
                    (ballotCount[p] ?: 0) * AgoraRules.PARTICIPATION_PER_BALLOT,
                ouverture = min(openness[p] ?: 0, AgoraRules.OPENNESS_MAX_VOTES) * AgoraRules.OPENNESS_PER_VOTE,
                flair = (betWins[p]?.size ?: 0) * AgoraRules.BET_POINTS
            )
        }.sortedWith(compareByDescending<ScoreView> { it.total }.thenBy { it.playerId })
    }

    fun myScore(): ScoreView = scores().first { it.playerId == myId }

    /** Combien de joueurs AGORA ont été vus dans les 30 dernières minutes (moi compris). */
    fun agoraPlayerCount(): Int = synchronized(lock) { agoraPlayers.size + 1 }

    /** Pour le journal de fin : le Livre du cortège de la journée. */
    fun bookOfTheDay(): List<BookEntry> = synchronized(lock) { book.sortedByDescending { it.accord } }

    companion object {
        fun randomHex(n: Int): String {
            val bytes = ByteArray((n + 1) / 2)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }.take(n)
        }
    }
}
