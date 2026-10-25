package com.ecut.campus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimetableGestureMathTest {

    @Test
    public void slowShortDragSpringsBack() {
        assertFalse(TimetableView.shouldCommitPage(-100f, 0f, 1080f));
    }

    @Test
    public void dragPastQuarterPageCommits() {
        assertTrue(TimetableView.shouldCommitPage(-280f, 0f, 1080f));
        assertTrue(TimetableView.shouldCommitPage(280f, 0f, 1080f));
    }

    @Test
    public void fastFlickCommitsEvenWhenShort() {
        assertTrue(TimetableView.shouldCommitPage(-160f, -1600f, 1080f));
        assertTrue(TimetableView.shouldCommitPage(160f, 1600f, 1080f));
    }

    @Test
    public void reverseFlickSpringsBack() {
        assertFalse(TimetableView.shouldCommitPage(-180f, 1300f, 1080f));
    }

    @Test
    public void snapAnimationStaysShortAndScalesWithDistance() {
        long small = TimetableView.pageAnimatorDuration(120f, 1080f);
        long large = TimetableView.pageAnimatorDuration(1080f, 1080f);
        assertTrue(small >= 140L);
        assertTrue(large > small);
        assertTrue(large <= 300L);
    }

    @Test
    public void zeroDistanceDoesNotAnimate() {
        assertEquals(0L, TimetableView.pageAnimatorDuration(0f, 1080f));
    }
}
