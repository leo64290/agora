package com.bitchat.android.agora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.random.Random

class AgoraEngineTest {

    // 1 800 000 000 000 ms est pile un début de manche ; on démarre une minute après.
    private var now = 1_800_000_000_000L + 60_000L
    private val zone: ZoneId = ZoneId.of("UTC")
    private var counter = 0

    private fun engine(id: String) =
        AgoraEngine(id, { now }, { "i" + (counter++).toString().padStart(5, '0') }, zone)

    private fun minutes(m: Int) { now += m * 60_000L }
    private fun seconds(s: Int) { now += s * 1000L }

    /** Diffuse un message à tous les autres moteurs. */
    private fun send(from: AgoraEngine, all: List<AgoraEngine>, result: AgoraResult): String? {
        val ok = result as? AgoraResult.Ok ?: return null
        all.filter { it !== from }.forEach { it.receive(from.myId, ok.payload) }
        return ok.key
    }

    private fun ok(r: AgoraResult): AgoraResult.Ok {
        assertTrue("refusé : $r", r is AgoraResult.Ok)
        return r as AgoraResult.Ok
    }

    // ------------------------------------------------------------ format

    @Test
    fun codecRoundTripAndCleaning() {
        val recs = listOf(
            AgoraRecord.IdeaRec("abc123", IdeaKind.SLOGAN, 42L, null, "On lâche\nrien\u001F ✊"),
            AgoraRecord.VoteRec("p1:abc", -1, 43L),
            AgoraRecord.AssemblyRec("q1", 1L, 120_001L, "Pause ?", listOf("Oui", "Non")),
            AgoraRecord.BallotRec("p1:q1", 1, 44L),
            AgoraRecord.BetRec(7L, "p1:abc", 45L),
            AgoraRecord.ClapRec("p1:abc", 12, 46L)
        )
        val back = AgoraCodec.decode(AgoraCodec.encode(recs))
        assertEquals(6, back.size)
        val idea = back[0] as AgoraRecord.IdeaRec
        assertEquals("On lâche rien ✊", idea.text)
        assertEquals(recs.drop(1), back.drop(1))
    }

    @Test
    fun codecIgnoresGarbage() {
        assertTrue(AgoraCodec.decode("pas agora".toByteArray()).isEmpty())
        val mixed = "AG1\nV\u001Fp:x\u001F9\u001F1\nV\u001Fp:x\u001F1\u001F5\nZ\u001Fbof\nI\u001F../\u001FS\u001F1\u001F\u001Fbad"
        val out = AgoraCodec.decode(mixed.toByteArray())
        assertEquals(listOf<AgoraRecord>(AgoraRecord.VoteRec("p:x", 1, 5L)), out)
    }

    @Test
    fun longSnapshotsAreSplitIntoChunks() {
        val recs = (1..200).map { AgoraRecord.VoteRec("auteur:idee$it", 1, it.toLong()) }
        val chunks = AgoraCodec.encodeChunks(recs, 1000)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.size <= 1000 })
        assertEquals(recs, chunks.flatMap { AgoraCodec.decode(it) })
    }

    // ------------------------------------------------------- idées et votes

    @Test
    fun ideaTravelsAndVotesAreCounted() {
        val alice = engine("alice"); val bob = engine("bob"); val carol = engine("carol")
        val all = listOf(alice, bob, carol)
        val key = send(alice, all, alice.propose(IdeaKind.ACTION, "Pause eau à la prochaine place"))!!
        assertEquals(1, bob.wall().size)
        send(bob, all, bob.vote(key, 1))
        send(carol, all, carol.vote(key, -1))
        val v = alice.wall().single()
        assertEquals(1, v.pour); assertEquals(1, v.contre)
        assertTrue(v.isMine)
        assertTrue(alice.vote(key, 1) is AgoraResult.Refused)
        // Bob change d'avis : le dernier vote compte
        seconds(5)
        send(bob, all, bob.vote(key, -1))
        assertEquals(2, alice.wall().single().contre)
        // Un vieux vote qui arrive en retard ne remplace pas le nouveau
        alice.receive("bob", AgoraCodec.encode(listOf(AgoraRecord.VoteRec(key, 1, now - 3_000))))
        assertEquals(0, alice.wall().single().pour)
    }

    @Test
    fun votesExpireAndSilentIdeasDisappear() {
        val alice = engine("alice"); val bob = engine("bob")
        val all = listOf(alice, bob)
        val key = send(alice, all, alice.propose(IdeaKind.SLOGAN, "Ensemble on va plus loin"))!!
        minutes(20)
        send(bob, all, bob.vote(key, 1))
        minutes(25) // 45 min après l'idée, 25 min après le vote : toujours là
        alice.tick()
        assertEquals(1, alice.wall().single().pour)
        minutes(6)  // 31 min après le dernier vote : tout s'efface
        alice.tick()
        assertTrue(alice.wall().isEmpty())
    }

    @Test
    fun ideaLimitsAreEnforcedLocallyAndRemotely() {
        val alice = engine("alice"); val bob = engine("bob")
        ok(alice.propose(IdeaKind.SLOGAN, "Première idée"))
        assertTrue(alice.propose(IdeaKind.SLOGAN, "Trop vite") is AgoraResult.Refused)
        minutes(2); ok(alice.propose(IdeaKind.SLOGAN, "Deuxième idée"))
        minutes(2); ok(alice.propose(IdeaKind.SLOGAN, "Troisième idée"))
        minutes(2)
        assertTrue(alice.propose(IdeaKind.SLOGAN, "Quatrième idée") is AgoraResult.Refused)
        // Un tricheur qui envoie deux idées à 10 secondes d'écart : la 2e est ignorée
        bob.receive("triche", AgoraCodec.encode(listOf(
            AgoraRecord.IdeaRec("a1", IdeaKind.SLOGAN, now, null, "Spam un"),
            AgoraRecord.IdeaRec("a2", IdeaKind.SLOGAN, now - 10_000, null, "Spam deux")
        )))
        assertEquals(1, bob.wall().size)
        // Une date trop dans le futur est refusée
        bob.receive("futur", AgoraCodec.encode(listOf(AgoraRecord.IdeaRec("f", IdeaKind.SLOGAN, now + 3_600_000, null, "Du futur"))))
        assertEquals(1, bob.wall().size)
    }

    // ------------------------------------------------- camps cachés et points

    @Test
    fun bridgingIdeaBeatsDivisiveIdeaWithMoreVotes() {
        val zed = engine("zed")          // auteur neutre
        val alice = engine("a0")         // camp A, auteur d'une idée
        val obs = engine("obs")          // un observateur
        val campA = listOf(alice) + (1..5).map { engine("a$it") }
        val campB = (0..3).map { engine("b$it") }
        val all = listOf(zed, obs) + campA + campB

        val p1 = send(zed, all, zed.propose(IdeaKind.ACTION, "Bloquer le périph"))!!; minutes(2)
        val p2 = send(zed, all, zed.propose(IdeaKind.ACTION, "Marcher vers la préfecture"))!!; minutes(2)
        val d = send(zed, all, zed.propose(IdeaKind.ACTION, "Rester toute la nuit"))!!; minutes(2)
        val c = send(alice, all, alice.propose(IdeaKind.ACTION, "Minute de silence devant l'hôpital"))!!

        for (a in campA) { if (a !== alice) { send(a, all, a.vote(p1, 1)) }; send(a, all, a.vote(p2, 1)); send(a, all, a.vote(d, 1)) }
        for (b in campB) { send(b, all, b.vote(p1, -1)); send(b, all, b.vote(p2, -1)); send(b, all, b.vote(d, -1)) }
        // L'idée C : 3 Pour et 1 Contre dans chaque camp
        listOf(campA[1], campA[2], campA[3]).forEach { send(it, all, it.vote(c, 1)) }
        send(campA[4], all, campA[4].vote(c, -1))
        listOf(campB[0], campB[1], campB[2]).forEach { send(it, all, it.vote(c, 1)) }
        send(campB[3], all, campB[3].vote(c, -1))

        val wall = obs.wall()
        val vc = wall.first { it.key == c }
        val vd = wall.first { it.key == d }
        assertTrue("C doit être rassembleuse", vc.bridging)
        assertTrue("D doit diviser", vd.divisive)
        assertTrue(vd.pour >= vc.pour)
        assertTrue("C doit passer devant D", vc.rank < vd.rank)

        // Ouverture : b0 dit Pour à l'idée d'alice (camp A) => 5 points d'ouverture
        val scores = obs.scores().associateBy { it.playerId }
        assertEquals(5, scores["b0"]!!.ouverture)
        assertEquals(0, scores["a1"]!!.ouverture)
        assertTrue(scores["a0"]!!.rassembleur > 0)
    }

    @Test
    fun withFewVotersThereIsOnlyOneCampAndPlainMajority() {
        val zed = engine("zed"); val v1 = engine("v1"); val v2 = engine("v2")
        val all = listOf(zed, v1, v2)
        val k = send(zed, all, zed.propose(IdeaKind.DEMAND, "Des services publics près de chez soi"))!!
        send(v1, all, v1.vote(k, 1)); send(v2, all, v2.vote(k, 1))
        val view = zed.wall().single()
        assertEquals(75, view.accord) // (2+1)/(2+2)
        assertFalse(view.divisive)
    }

    // ------------------------------------------------------------ simulation

    @Test
    fun thirtyPhonesWithLostAndDuplicatedMessagesAgreeOnTheWall() {
        val rnd = Random(1234)
        val phones = (0 until 30).map { engine("t" + it.toString().padStart(2, '0')) }
        val inbox = ArrayList<Pair<AgoraEngine, Pair<String, ByteArray>>>()
        fun broadcast(from: AgoraEngine, payload: ByteArray) {
            for (to in phones) if (to !== from) {
                if (rnd.nextDouble() < 0.35) continue              // perdu
                inbox.add(to to (from.myId to payload))
                if (rnd.nextDouble() < 0.2) inbox.add(to to (from.myId to payload)) // en double
            }
        }
        fun deliverSome() {
            inbox.shuffle(rnd)                                       // dans le désordre
            val n = inbox.size / 2
            repeat(n) { val (to, msg) = inbox.removeAt(inbox.size - 1); to.receive(msg.first, msg.second) }
        }
        for (step in 0 until 240) {                                  // 20 minutes, un pas = 5 s
            seconds(5)
            for (p in phones) {
                val r = rnd.nextDouble()
                if (r < 0.02) {
                    val res = p.propose(IdeaKind.values()[rnd.nextInt(3)], "Idée ${p.myId} n°$step")
                    if (res is AgoraResult.Ok) broadcast(p, res.payload)
                } else if (r < 0.25) {
                    val wall = p.wall().filter { !it.isMine }
                    if (wall.isNotEmpty()) {
                        val target = wall[rnd.nextInt(wall.size)]
                        val res = p.vote(target.key, rnd.nextInt(3) - 1)
                        if (res is AgoraResult.Ok) broadcast(p, res.payload)
                    }
                }
            }
            deliverSome()
            if (step % 18 == 0) for (p in phones) p.snapshot().forEach { broadcast(p, it) }
            phones.forEach { it.tick() }
        }
        // Fin : chacun renvoie tout, sans perte
        while (inbox.isNotEmpty()) deliverSome().also { if (inbox.size == 1) { val (to, m) = inbox.removeAt(0); to.receive(m.first, m.second) } }
        for (p in phones) for (chunk in p.snapshot()) for (q in phones) if (q !== p) q.receive(p.myId, chunk)

        val reference = phones[0].wall().map { Triple(it.key, it.pour, it.contre) }
        assertTrue("il faut des idées", reference.size >= 5)
        for (p in phones) assertEquals(reference, p.wall().map { Triple(it.key, it.pour, it.contre) })
        val refScores = phones[0].scores().map { it.playerId to it.total }
        for (p in phones) assertEquals(refScores, p.scores().map { it.playerId to it.total })
    }

    // ------------------------------------------------------------ assemblées

    @Test
    fun flashAssemblyHidesResultsUntilTheEnd() {
        val alice = engine("alice"); val bob = engine("bob"); val carol = engine("carol")
        val all = listOf(alice, bob, carol)
        val events = bob.tick()
        assertTrue(events.isEmpty())
        val key = send(alice, all, alice.createAssembly("On fait une pause ?", listOf("Oui", "Non"), 5 * 60_000L))!!
        assertTrue(bob.tick().any { it is AgoraEvent.NewAssembly })
        send(bob, all, bob.ballot(key, 0))
        send(carol, all, carol.ballot(key, 1))
        seconds(10)
        send(carol, all, carol.ballot(key, 0)) // carol change d'avis
        val running = alice.assemblies().single()
        assertFalse(running.ended)
        assertNull(running.results)
        assertEquals(2, running.voters)
        assertTrue(alice.createAssembly("Une autre ?", listOf("A", "B"), 60_000L) is AgoraResult.Refused)
        minutes(5)
        assertTrue(bob.ballot(key, 1) is AgoraResult.Refused)
        // un bulletin daté d'après la fin ne compte pas
        alice.receive("dave", AgoraCodec.encode(listOf(AgoraRecord.BallotRec(key, 1, now))))
        val done = alice.assemblies().single()
        assertTrue(done.ended)
        assertEquals(listOf(100, 0), done.results)
        assertEquals(0, done.winner)
        assertTrue(alice.tick().any { it is AgoraEvent.AssemblyEnded && it.winner == "Oui" })
    }

    // ------------------------------------------------------------------ duel

    @Test
    fun betterVersionReplacesTheOldOneAndAuthorsShare() {
        val alice = engine("alice"); val bob = engine("bob")
        val voters = (1..6).map { engine("v$it") }
        val all = listOf(alice, bob) + voters
        val parent = send(alice, all, alice.propose(IdeaKind.DEMAND, "Des services publics à 20 minutes"))!!
        assertTrue(alice.propose(IdeaKind.DEMAND, "Ma propre v2", parent) is AgoraResult.Refused)
        val child = send(bob, all, bob.propose(IdeaKind.DEMAND, "Des services publics à 20 minutes, même à la campagne", parent))!!
        voters.forEachIndexed { i, v -> send(v, all, v.vote(parent, if (i < 3) 1 else -1)) }
        voters.forEach { v -> send(v, all, v.vote(child, 1)) }
        val bobEvents = bob.tick()
        alice.tick()
        assertTrue(bobEvents.any { it is AgoraEvent.DuelWon && it.iAmImprover })
        val wall = alice.wall()
        assertTrue(wall.none { it.key == parent })
        val v2 = wall.single { it.key == child }
        assertEquals(listOf("bob", "alice"), v2.authorIds)
        assertTrue(v2.isMine)
        assertTrue(v2.duelNote!!.contains("remplacé"))
        val s = alice.scores().associateBy { it.playerId }
        assertEquals(s["alice"]!!.rassembleur, s["bob"]!!.rassembleur)
    }

    @Test
    fun weakerVersionDisappearsAfterTenMinutes() {
        val alice = engine("alice"); val bob = engine("bob"); val v = engine("v1")
        val all = listOf(alice, bob, v)
        val parent = send(alice, all, alice.propose(IdeaKind.SLOGAN, "On lâche rien"))!!
        val child = send(bob, all, bob.propose(IdeaKind.SLOGAN, "On lâche rien du tout", parent))!!
        send(v, all, v.vote(parent, 1))
        minutes(11)
        alice.tick()
        assertTrue(alice.wall().none { it.key == child })
        assertTrue(alice.wall().any { it.key == parent })
    }

    // ------------------------------------------------- manches, paris, Livre

    @Test
    fun roundIsSealedInTheBookAndGoodBetsWin() {
        val alice = engine("alice"); val bob = engine("bob"); val carol = engine("carol"); val dan = engine("dan")
        val all = listOf(alice, bob, carol, dan)
        all.forEach { it.tick() }
        val k = send(alice, all, alice.propose(IdeaKind.ACTION, "Un minibus pour les personnes âgées"))!!
        listOf(bob, carol, dan).forEach { send(it, all, it.vote(k, 1)) }
        send(carol, all, carol.bet(k))
        assertTrue(carol.bet(k) is AgoraResult.Refused)
        minutes(26) // il reste 3 minutes : trop tard pour parier
        assertTrue(dan.bet(k) is AgoraResult.Refused)
        seconds(210)  // nouvelle manche
        val aliceEvents = alice.tick()
        val carolEvents = carol.tick()
        bob.tick()
        val sealed = aliceEvents.filterIsInstance<AgoraEvent.RoundSealed>().single()
        assertTrue(sealed.iAmCrowned)
        assertEquals(k, sealed.entry!!.ideaKey)
        assertTrue(carolEvents.filterIsInstance<AgoraEvent.RoundSealed>().single().myBetWon)
        assertEquals(1, bob.round().book.size)
        assertEquals(AgoraRules.BET_POINTS, bob.scores().first { it.playerId == "carol" }.flair)
    }

    @Test
    fun clapsAreGroupedBeforeSending() {
        val alice = engine("alice"); val bob = engine("bob")
        val k = send(alice, listOf(alice, bob), alice.propose(IdeaKind.SLOGAN, "Bravo les soignants"))!!
        repeat(3) { bob.clap(k) }
        val payload = bob.flushClaps()
        assertNotNull(payload)
        assertNull(bob.flushClaps())
        alice.receive("bob", payload!!)
        assertEquals(3, alice.wall().single().claps)
        alice.receive("bob", payload) // en double : pas compté deux fois
        assertEquals(3, alice.wall().single().claps)
    }

    @Test
    fun panicClearForgetsEverything() {
        val alice = engine("alice")
        ok(alice.propose(IdeaKind.SLOGAN, "Une idée"))
        alice.clear()
        assertTrue(alice.wall().isEmpty())
    }

    // ------------------------------------------------- badges, pseudos

    @Test
    fun badgesAndLevels() {
        val p = AgoraProgress(MemoryStore(), { now }, zone)
        assertFalse(p.badges().first { it.id == "voix" }.got)
        p.onIdeaProposed()
        assertTrue(p.badges().first { it.id == "voix" }.got)
        p.addRelayed(1_250)
        assertTrue(p.badges().first { it.id == "relais" }.got)
        assertEquals(12, p.relayPoints())
        assertEquals("Rassembleur·se", p.level(60).title)
        assertEquals("Pont du cortège", p.level(60).nextTitle)
        assertEquals("Agora vivante", p.level(999).title)
        p.onAssemblyEnded("Pause ?", "Oui")
        assertEquals(listOf("Pause ? : Oui"), p.dayStats().decisions)
        now += 24 * 3_600_000L // le lendemain : les compteurs du jour repartent à zéro
        assertTrue(p.dayStats().decisions.isEmpty())
        assertTrue(p.badges().first { it.id == "relais" }.got)
    }

    @Test
    fun pseudosAreStableAndAnonymous() {
        val a = AgoraNames.pseudo("0123456789abcdef")
        assertEquals(a, AgoraNames.pseudo("0123456789abcdef"))
        assertTrue(Regex("^[\\p{L}]+-\\d{2}$").matches(a))
        assertEquals("${AgoraNames.pseudo("x")} et ${AgoraNames.pseudo("y")}", AgoraNames.pseudos(listOf("x", "y")))
    }
}
