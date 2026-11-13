package com.innovation313.roshankhata.ui.poster3d

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The live 3D hero is drawn at its view's own size. Filament's UiHelper reports 0 x 0 when it attaches to a
 * TextureView whose surface already exists (the poster screen coming back, a design swiped to); the size then
 * comes from the view, so a still taken later cannot leave the live clock drawn at the still's size (13 Nov 2026).
 */
class LiveSizeTest {
    @Test
    fun theReportedSizeWins() = assertEquals(278 to 278, LiveSize.pick(278, 278, 500, 500))

    @Test
    fun zeroFromAnExistingSurfaceTakesTheViewsOwnSize() = assertEquals(278 to 278, LiveSize.pick(0, 0, 278, 278))

    @Test
    fun aHalfReportIsNotTrusted() = assertEquals(300 to 200, LiveSize.pick(640, 0, 300, 200))

    @Test
    fun aViewNotLaidOutYetGivesNothingToDraw() = assertEquals(0 to 0, LiveSize.pick(0, 0, -1, -1))
}
