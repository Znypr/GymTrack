package com.example.gymtrack.core.ui.components

import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleLevel
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The body map's shapes, layout and hit-testing (#311), checked on the JVM against the ported
 * artwork. Screenshot tooling is not in the build; the Compose previews cover the look.
 */
class BodyMapGeometryTest {
    private val front = BodyMapGeometry.front
    private val back = BodyMapGeometry.back

    // At density 1 a px is a dp, so these are the real dp rules.
    private val slop = 24f
    private val bonus = 12f

    @Test
    fun everyArtworkPathParsesIntoClosedPolygons() {
        for (fig in listOf(front, back)) {
            val art = fig.art
            (art.silhouette + art.partsUnder + art.partsOver + art.details + art.pencilOutline + art.pencilGhost + art.pencilLoops.values)
                .forEach { d -> assertTrue("empty path $d", SvgPath.flatten(SvgPath.parse(d)).isNotEmpty()) }
            fig.pieces.forEach { p ->
                assertTrue("${p.muscle} has no area", p.polygons.sumOf { polygonArea(it).toDouble() } > 4.0)
            }
        }
    }

    @Test
    fun figuresStayInTheirHalfOfTheViewBox() {
        front.pieces.forEach { assertTrue("${it.muscle} ${it.bounds}", it.bounds.left >= 0f && it.bounds.right <= 400f) }
        back.pieces.forEach { assertTrue("${it.muscle} ${it.bounds}", it.bounds.left >= 420f && it.bounds.right <= 820f) }
        (front.pieces + back.pieces).forEach { assertTrue(it.bounds.top >= 0f && it.bounds.bottom <= 460f) }
    }

    @Test
    fun all26RegionsAreDrawnAndMatchTheEnumViews() {
        assertEquals(Muscle.entries.toSet(), front.muscles + back.muscles)
        for (m in Muscle.entries) {
            assertEquals("$m front", m.front, m in front.muscles)
            assertEquals("$m back", m.back, m in back.muscles)
        }
    }

    @Test
    fun scienceShapesHaveTheRightPieceCounts() {
        fun count(fig: FigureGeometry, m: Muscle) = fig.pieces.count { it.muscle == m }
        // Three deltoid heads: front + side at the front, side + rear at the back, one per shoulder.
        assertEquals(2, count(front, Muscle.DELT_FRONT))
        assertEquals(2, count(front, Muscle.DELT_SIDE))
        assertEquals(2, count(back, Muscle.DELT_REAR))
        // Rectus abdominis in segments either side of the linea alba.
        assertEquals(8, count(front, Muscle.ABS))
        // Gastrocnemius: two heads per calf (the "heart") over the soleus.
        assertEquals(4, count(back, Muscle.GASTROCNEMIUS))
        // Traps split upper and mid/lower at the back.
        assertTrue(count(back, Muscle.TRAPS_UPPER) > 0 && count(back, Muscle.TRAPS_MID_LOWER) > 0)
    }

    @Test
    fun gastrocnemiusSitsOverTheSoleus() {
        val gastro = back.pieces.filter { it.muscle == Muscle.GASTROCNEMIUS }
        val soleus = back.pieces.filter { it.muscle == Muscle.SOLEUS }
        assertTrue(gastro.minOf { it.bounds.top } < soleus.minOf { it.bounds.top })
        assertTrue(back.pieces.indexOfFirst { it.muscle == Muscle.GASTROCNEMIUS } > back.pieces.indexOfFirst { it.muscle == Muscle.SOLEUS })
    }

    @Test
    fun tappingInsideEveryRegionSelectsIt() {
        for (view in listOf(BodyMapView.FRONT, BodyMapView.BACK)) {
            val layout = BodyMapLayout(view, 360f, 360f / BodyMapGeometry.aspectRatio(view))
            for (m in layout.figures.single().muscles) {
                val hit = interiorPoints(layout, m).any { (x, y) -> layout.hitTest(x, y, slop, bonus) == m }
                assertTrue("$m is not reachable by tapping inside it ($view)", hit)
            }
        }
    }

    @Test
    fun smallRegionsHaveAtLeast24dpTargets() {
        val layout = BodyMapLayout(BodyMapView.FRONT, 360f, 360f / BodyMapGeometry.aspectRatio(BodyMapView.FRONT))
        for (m in BODY_MAP_SMALL_REGIONS.filter { it in layout.figures.single().muscles }) {
            val piece = layout.figures.single().pieces.first { it.muscle == m }
            // From the piece centre, walk sideways until the tap is 11 dp outside the shape.
            val cx = (piece.bounds.left + piece.bounds.right) / 2f
            val cy = (piece.bounds.top + piece.bounds.bottom) / 2f
            for (dir in listOf(-1f, 1f)) {
                var mx = cx
                while (piece.distanceTo(mx, cy) * layout.scale < 11f) mx += dir * 0.25f
                val sx = mx * layout.scale + layout.dx[0]
                val sy = cy * layout.scale + layout.dy
                assertEquals("$m at 11 dp outside ($dir)", m, layout.hitTest(sx, sy, slop, bonus))
            }
        }
    }

    @Test
    fun tapsBesideTheBodySnapWithin24dpAndNothingFarAway() {
        val layout = BodyMapLayout(BodyMapView.BOTH, 360f, 360f / BodyMapGeometry.aspectRatio(BodyMapView.BOTH))
        assertNull(layout.hitTest(1f, 1f, slop, bonus))
        // 20 dp to the left of the leftmost front region still finds a region.
        val leftmost = front.pieces.minByOrNull { it.bounds.left }!!
        val y = (leftmost.bounds.top + leftmost.bounds.bottom) / 2f
        var mx = leftmost.bounds.left
        while (leftmost.distanceTo(mx, y) * layout.scale < 20f) mx -= 0.25f
        assertNotNull(layout.hitTest(mx * layout.scale + layout.dx[0], y * layout.scale + layout.dy, slop, bonus))
    }

    @Test
    fun hatchSegmentsStayInsideTheirRegion() {
        val quads = front.pieces.first { it.muscle == Muscle.QUADS }
        for (spec in listOf(HatchSpec(6f, 1.1f, 45f), HatchSpec(4f, 0.8f, 135f), HatchSpec(3.3f, 1.6f, 130f))) {
            val seg = Hatching.clip(quads.polygons, quads.bounds, spec)
            assertTrue(seg.size >= 8)
            var i = 0
            while (i < seg.size) {
                val mx = (seg[i] + seg[i + 2]) / 2f
                val my = (seg[i + 1] + seg[i + 3]) / 2f
                assertTrue("segment midpoint outside", quads.contains(mx, my))
                i += 4
            }
        }
    }

    @Test
    fun pencilWobbleIsSmallAndDeterministic() {
        val poly = floatArrayOf(100f, 100f, 150f, 120f, 200f, 300f)
        val a = PencilWobble.apply(poly, 0.85f)
        val b = PencilWobble.apply(poly, 0.85f)
        assertTrue(a.contentEquals(b))
        for (i in poly.indices step 2) assertTrue(hypot(a[i] - poly[i], a[i + 1] - poly[i + 1]) <= 0.85f * 1.42f)
    }

    @Test
    fun minimumSizeRuleHoldsExceptTheEnlargedSmallRegions() {
        // ANATOMY.md: the largest piece of each id must be ≥ 6 px at view = both, width 360.
        val scale = BodyMapLayout(BodyMapView.BOTH, 360f, 360f / BodyMapGeometry.aspectRatio(BodyMapView.BOTH)).scale
        val tooSmall = Muscle.entries.filter { largestPieceMinSide(it) * scale < 6f }
        assertTrue("under 6 px without an enlarged target: ${tooSmall - BODY_MAP_SMALL_REGIONS}", (tooSmall - BODY_MAP_SMALL_REGIONS).isEmpty())
    }

    @Test
    fun layoutCentresAndRoundTrips() {
        val layout = BodyMapLayout(BodyMapView.BOTH, 400f, 300f)
        val fig = layout.figures[1]
        val sx = fig.window.left * layout.scale + layout.dx[1]
        assertTrue(sx >= 0f && sx <= 400f)
        assertTrue(BodyMapGeometry.aspectRatio(BodyMapView.BOTH) in 0.7f..1.3f)
        assertTrue(BodyMapGeometry.aspectRatio(BodyMapView.FRONT) in 0.3f..0.7f)
    }

    @Test
    fun descriptionNamesLevelsInWords() {
        val levels = Muscle.entries.associateWith { MuscleLevel.NONE } +
            mapOf(Muscle.QUADS to MuscleLevel.HIGH, Muscle.BICEPS to MuscleLevel.LOW, Muscle.SOLEUS to MuscleLevel.UNMAPPED)
        assertEquals(
            "Muscle map, front and back. High: Quads. Low: Biceps. Not mapped: Soleus. All other regions none.",
            bodyMapDescription(levels, BodyMapView.BOTH),
        )
        assertEquals(
            "Muscle map, back view. No exercises are mapped to muscles yet.",
            bodyMapDescription(Muscle.entries.associateWith { MuscleLevel.UNMAPPED }, BodyMapView.BACK),
        )
        assertEquals("Muscle map, front view. No regions trained.", bodyMapDescription(emptyMap(), BodyMapView.FRONT))
    }

    @Test
    fun setsAreFormattedLikePeopleSayThem() {
        assertEquals("4", formatSets(4f))
        assertEquals("2.5", formatSets(2.5f))
        assertEquals("1 set", setsPhrase(1f))
        assertEquals("0.5 sets", setsPhrase(0.5f))
    }

    /** Grid points inside [m]'s pieces, in screen px. */
    private fun interiorPoints(layout: BodyMapLayout, m: Muscle): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        for (piece in layout.figures.single().pieces.filter { it.muscle == m }) {
            val b = piece.bounds
            for (i in 1..9) for (j in 1..9) {
                val x = b.left + b.width * i / 10f
                val y = b.top + b.height * j / 10f
                if (piece.contains(x, y)) out += (x * layout.scale + layout.dx[0]) to (y * layout.scale + layout.dy)
            }
        }
        return out
    }
}
