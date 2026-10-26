package com.ecut.campus.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RulerDialMathTest {
    @Test
    public void snapRoundsAndClamps() {
        assertEquals(45, RulerDialView.snap(44.6f, 5, 180));
        assertEquals(44, RulerDialView.snap(44.4f, 5, 180));
        assertEquals(5, RulerDialView.snap(2f, 5, 180));
        assertEquals(180, RulerDialView.snap(200f, 5, 180));
    }
}
